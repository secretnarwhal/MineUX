// World Shaders, full-resolution composite. Light, shadows, fog, god rays and bloom come from the low-resolution pass
// (depth-aware upsample); rain wetness, puddles, ripples and reflections are done here; then the grade.
// Compiled twice: with CL_RAIN (rain wetness, puddles, reflections) and without, so rain costs nothing when off.
in vec2 uv;
out vec4 fragColor;

uniform sampler2D uLoA;       // shadow, rays, fog T, distance/256
uniform sampler2D uLoB;       // bloom (sampled bilinear)
uniform sampler2D uHeight;    // rain: heightmap around the camera (blocks above uHmCamY)
uniform vec2  uLoSize;        // low-res size in pixels
uniform int   uWorld;         // 1 = World Shaders is on
uniform vec3  uShadeCol, uFogCol;
uniform vec4  uSunScreen;
uniform float uSunK, uShadowK, uRayK, uFogK, uBloomK, uFogSun;
uniform float uExposure, uContrast, uSat, uWarmth, uVignette;
// rain
uniform float uWet;           // 0..1 how wet the ground is
uniform float uPuddles;       // 0..1 how much of the ground is under water
uniform float uRipples;       // 0..1 rain hitting the puddles
uniform float uGloss;         // 0..1 reflection strength
uniform int   uSsrSteps;
uniform vec3  uSkyZenith, uSkyHorizon;
uniform vec3  uWaterTint;
uniform vec2  uHmOff;         // camera position inside the heightmap (x, z)
uniform float uHmCamY;        // camera height above the heightmap's base
uniform float uHmSize;
uniform int   uUseHeight;

vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }

/** Low-res sample at uv, weighted toward texels at the same distance (keeps edges sharp). */
vec4 upsample(sampler2D t, float dist) {
    vec2 p = uv * uLoSize - 0.5;
    vec2 f = fract(p);
    ivec2 i0 = ivec2(floor(p));
    ivec2 mx = ivec2(uLoSize) - 1;
    vec4 s00 = texelFetch(t, clamp(i0, ivec2(0), mx), 0), s10 = texelFetch(t, clamp(i0 + ivec2(1, 0), ivec2(0), mx), 0);
    vec4 s01 = texelFetch(t, clamp(i0 + ivec2(0, 1), ivec2(0), mx), 0), s11 = texelFetch(t, clamp(i0 + ivec2(1, 1), ivec2(0), mx), 0);
    float k = 1.0 / max(0.15, dist * 0.06);
    vec4 w = vec4((1.0 - f.x) * (1.0 - f.y), f.x * (1.0 - f.y), (1.0 - f.x) * f.y, f.x * f.y)
           / (1.0 + k * abs(vec4(s00.a, s10.a, s01.a, s11.a) * 256.0 - dist)) + 1e-4;
    return (s00 * w.x + s10 * w.y + s01 * w.z + s11 * w.w) / (w.x + w.y + w.z + w.w);
}

/** The shadow, filtered over the 3x3 low-res texels around uv (only texels at about the same distance count). */
float shadowAt(float dist) {
    vec2 p = uv * uLoSize - 0.5;
    ivec2 c = ivec2(floor(p + 0.5));
    ivec2 mx = ivec2(uLoSize) - 1;
    float k = 1.0 / max(0.12, dist * 0.04);
    float acc = 0.0, wsum = 0.0;
    for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
        vec4 s = texelFetch(uLoA, clamp(c + ivec2(i, j), ivec2(0), mx), 0);
        vec2 d = vec2(c + ivec2(i, j)) - p;
        float w = exp(-dot(d, d) * 0.9) / (1.0 + k * abs(s.a * 256.0 - dist));
        acc += s.r * w; wsum += w;
    }
    return acc / max(wsum, 1e-4);
}

#ifdef CL_RAIN
/** Rain ripples on a puddle at world xz: a few expanding rings per cell. Returns a normal tilt (x, z). */
vec2 ripples(vec2 xz, float dist) {
    vec2 tilt = vec2(0.0);
    int layers = dist < 10.0 ? 2 : 1;           // the fine layer only close up
    for (int layer = 0; layer < 2; layer++) {
        if (layer >= layers) break;
        vec2 p = xz * (layer == 0 ? 1.6 : 2.7) + float(layer) * 17.3;
        vec2 cell = floor(p);
        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            vec2 c = cell + vec2(i, j);
            float h = hash12(c);
            float period = 0.9 + h * 0.7;
            float t = fract(uTime / period + h * 7.0);
            vec2 centre = c + vec2(hash12(c + 3.1), hash12(c + 7.7));
            vec2 dv = p - centre;
            float r = length(dv);
            float R = t * 0.75;
            float wave = sin((r - R) * 28.0) * exp(-pow((r - R) * 9.0, 2.0)) * (1.0 - t) * (1.0 - t);
            tilt += (dv / max(r, 1e-3)) * wave;
        }
    }
    return tilt * 0.22 * uRipples * (1.0 - smoothstep(12.0, 32.0, dist));
}

/** How exposed to the sky a surface is (rain only lands where nothing is above). */
float exposed(vec3 P) {
    if (uUseHeight == 0) return 1.0;
    vec2 tc = (P.xz + uHmOff) / uHmSize;
    if (tc.x < 0.0 || tc.y < 0.0 || tc.x > 1.0 || tc.y > 1.0) return 1.0;
    float top = texture(uHeight, tc).r;
    return smoothstep(-0.9, -0.4, P.y + uHmCamY - top);
}

/** Screen-space reflection of the ray from P along R, marched in clip space (no matrix work per step).
 *  a = how much of it was found on screen. Misses that end up in the sky sample the real sky on screen. */
vec4 reflectRay(vec3 P, vec3 R, float dist) {
    float maxD = 28.0;
    vec4 c0 = uViewProj * vec4(P, 1.0), c1 = uViewProj * vec4(P + R * maxD, 1.0);
    if (c1.w < 0.05) {                      // the far end is behind the camera: shorten the ray
        float t = (c0.w - 0.05) / max(c0.w - c1.w, 1e-4);
        c1 = mix(c0, c1, t * 0.98);
    }
    float prevT = 0.0;
    for (int i = 1; i <= 40; i++) {
        if (i > uSsrSteps) break;
        float t = float(i) / float(uSsrSteps);
        t *= t;                             // denser near the start, where detail matters
        vec4 c = mix(c0, c1, t);
        vec3 ndc = c.xyz / c.w;
        vec2 suv = ndc.xy * 0.5 + 0.5;
        if (suv.x < 0.0 || suv.y < 0.0 || suv.x > 1.0 || suv.y > 1.0) break;
        float sd = texture(uDepth, suv).r;
        if (sd > ndc.z && sd > SKY_DEPTH) {
            float a = prevT, b = t;
            for (int k = 0; k < 4; k++) {
                float m = (a + b) * 0.5;
                vec4 cm = mix(c0, c1, m);
                vec3 nm = cm.xyz / cm.w;
                if (texture(uDepth, nm.xy * 0.5 + 0.5).r > nm.z) b = m; else a = m;
            }
            vec4 cb = mix(c0, c1, b);
            vec3 nb = cb.xyz / cb.w;
            vec2 huv = nb.xy * 0.5 + 0.5;
            // reject hits far behind a surface (the ray passed behind something)
            float hitLin = length(posAt(huv, texture(uDepth, huv).r)), rayLin = length(P + R * maxD * b);
            if (rayLin - hitLin > 1.0 + maxD * b * 0.08) break;
            vec2 e = smoothstep(vec2(0.0), vec2(0.07), huv) * smoothstep(vec2(1.0), vec2(0.93), huv);
            return vec4(pow(texture(uScene, huv).rgb, vec3(2.2)), e.x * e.y);
        }
        prevT = t;
    }
    // missed: if the reflected direction points at open sky that is on screen, use the real sky colour there
    vec4 cs = uViewProj * vec4(R * 200.0, 1.0);
    if (cs.w > 0.05) {
        vec2 suv = cs.xy / cs.w * 0.5 + 0.5;
        if (suv.x > 0.0 && suv.y > 0.0 && suv.x < 1.0 && suv.y < 1.0 && texture(uDepth, suv).r <= SKY_DEPTH) {
            vec2 e = smoothstep(vec2(0.0), vec2(0.1), suv) * smoothstep(vec2(1.0), vec2(0.9), suv);
            return vec4(pow(texture(uScene, suv).rgb, vec3(2.2)), e.x * e.y * 0.85);
        }
    }
    return vec4(0.0);
}

#endif

void main() {
    vec3 src = texture(uScene, uv).rgb;
    vec3 col = pow(max(src, 0.0), vec3(2.2));
    float d = texture(uDepth, uv).r;
    bool sky = d <= SKY_DEPTH;
    vec3 P = sky ? posAt(uv, FAR_DEPTH) : posAt(uv, d);
    float dist = sky ? 220.0 : length(P);
    vec3 rd = P / max(length(P), 1e-4);
    vec3 L = normalize(uLightDir);
    float jit = hash12(gl_FragCoord.xy + fract(uTime * 7.3) * 91.0);

    vec4 lo = uWorld == 1 ? upsample(uLoA, dist) : vec4(0.0, 0.0, 1.0, 0.0);
    vec3 B = uWorld == 1 ? texture(uLoB, uv).rgb : vec3(0.0);

    // normal from screen-space derivatives of the position (free; only the 1-pixel silhouette is approximate)
    vec3 n = normalize(cross(dFdx(P), dFdy(P)));
    if (dot(n, -rd) < 0.0) n = -n;

    // ── World Shaders: relight with the sun, contact shadows ──
    if (uWorld == 1 && !sky) {
        float lit = smoothstep(-0.08, 0.55, dot(n, L));
        float shadow = uShadowK > 0.001 ? shadowAt(dist) : 0.0;
        float k = uSunK * (0.35 + 0.65 * uDay);
        float sh = shadow * clamp(uShadowK, 0.0, 1.5);
        float direct = lit * (1.0 - sh * 0.85);
        vec3 light = mix(uShadeCol * 0.78, uLightCol * 1.18, direct);
        col *= mix(vec3(1.0), light, clamp(k * 0.6, 0.0, 1.0));
        // shadows: darker and tinted by the sky light, stronger in daylight
        col *= mix(vec3(1.0), uShadeCol * 0.42, clamp(sh * lit * (0.35 + 0.65 * uDay), 0.0, 0.85));
        float rim = pow(clamp(1.0 - max(dot(n, -rd), 0.0), 0.0, 1.0), 4.0) * lit * (1.0 - shadow);
        col += uLightCol * rim * 0.06 * k * uDay;
    }

#ifdef CL_RAIN
    // ── Rain: wet, glossy ground and puddles ──
    if (uWet > 0.001 && !sky && dist < 96.0) {
        vec3 W = P + uCamPos;
        float up = smoothstep(0.55, 0.9, n.y);
        float exp0 = exposed(P);
        float wet = uWet * exp0 * mix(0.35, 1.0, up) * (1.0 - smoothstep(64.0, 96.0, dist));
        if (wet > 0.001) {
            // puddles: low spots in a world-space noise field, only on flat ground
            float pn = vnoise2(W.xz * 0.16) * 0.65 + vnoise2(W.xz * 0.53 + 4.2) * 0.35;
            float level = 1.0 - uPuddles * 0.62;
            float puddle = smoothstep(level, level + 0.06, pn) * up * exp0 * smoothstep(0.0, 0.25, uWet);
            float edge = smoothstep(level - 0.12, level, pn) * up * (1.0 - puddle);
            // wet surfaces get darker and richer; a soaked rim around each puddle
            float soak = clamp(wet * 0.85 + edge * 0.35, 0.0, 1.0);
            col *= mix(1.0, 0.58, soak);
            col = mix(vec3(luma(col)), col, 1.0 + 0.25 * soak);
            col = mix(col, col * uWaterTint * 0.85, puddle * 0.55);
            // normal: flat water in puddles (with ripples), the block's normal plus a little grain elsewhere
            vec3 nn = n;
            if (puddle > 0.01) {
                vec2 tl = ripples(W.xz, dist);
                nn = normalize(mix(n, vec3(0.0, 1.0, 0.0), puddle) + vec3(tl.x, 0.0, tl.y) * puddle);
            } else {
                float g1 = vnoise2(W.xz * 9.0) - 0.5, g2 = vnoise2(W.xz * 9.0 + 5.0) - 0.5;
                nn = normalize(n + vec3(g1, 0.0, g2) * 0.12);
            }
            vec3 V = -rd;
            vec3 R = reflect(rd, nn);
            float F = 0.02 + 0.98 * pow(1.0 - max(dot(nn, V), 0.0), 5.0);
            float gloss = mix(0.45, 1.0, puddle) * uGloss;
            vec3 skyR = mix(uSkyHorizon, uSkyZenith, smoothstep(0.0, 0.6, R.y));
            float k = clamp(F * gloss * (wet * 0.55 + puddle * 0.95), 0.0, 1.0);
            vec4 refl = k > 0.03 ? reflectRay(P + nn * 0.02, R, dist) : vec4(0.0);
            vec3 env = mix(skyR, refl.rgb, refl.a);
            // the sun's glint: a tight highlight on puddles, a soft sheen on wet ground
            float spec = pow(max(dot(R, L), 0.0), mix(120.0, 900.0, puddle)) * (0.3 + 0.7 * uDay) * mix(0.2, 0.9, puddle);
            col = mix(col, env, k) + uLightCol * spec * gloss * wet;
        }
    }
#endif

    if (uWorld == 1) {
        // ── god rays ──
        col += uLightCol * lo.g * uRayK * 0.9 * uSunScreen.z * uSunScreen.w * (sky ? 0.5 : 1.0);
        // ── fog: T from the low-res march, in-scatter lit toward the light ──
        if (uFogK > 0.001) {
            float g = 0.58, cosT = dot(rd, L);
            float phase = (1.0 - g * g) / pow(1.0 + g * g - 2.0 * g * cosT, 1.5) * 0.25;
            vec3 scatter = uFogCol * (0.25 + 0.75 * uDay) + uLightCol * phase * uFogSun * (0.25 + 0.75 * uDay) * 1.6;
            float T = lo.b;
            col = col * T + (1.0 - T) * scatter * (sky ? 0.6 : 1.0);
        }
        col += B * uBloomK * 0.9;
        // ── grade ──
        col *= uExposure;
        col *= vec3(1.0 + 0.10 * uWarmth, 1.0 + 0.015 * uWarmth, 1.0 - 0.11 * uWarmth);
        col = mix(col, aces(col * 1.12), 0.55);
        col = pow(max(col, 0.0), vec3(1.0 / 2.2));
        col = (col - 0.5) * uContrast + 0.5;
        col = mix(vec3(luma(col)), col, uSat);
        vec2 q = (uv - 0.5) * vec2(uTexel.y / uTexel.x, 1.0);
        col *= mix(1.0, smoothstep(1.05, 0.25, length(q) * 1.15), 0.42 * uVignette);
        col += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    } else {
        col = pow(max(col, 0.0), vec3(1.0 / 2.2));
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
