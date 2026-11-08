// World Shaders, low-resolution pass (1/2 to 1/4 of the screen): everything that marches.
//   out0: r = contact shadow, g = god rays, b = fog transmittance, a = distance / 256 (for the depth-aware upsample)
//   out1: bloom (linear rgb)
in vec2 uv;
layout(location = 0) out vec4 outA;
layout(location = 1) out vec4 outB;

uniform vec4  uSunScreen;     // xy = sun in uv, z = on-screen weight, w = visible weight
uniform float uShadowK, uRayK, uFogK, uFogH, uFogStart, uDrift, uBloomK;
uniform int   uFogSteps, uShadowSteps, uRaySteps;
uniform vec2  uLoTexel;       // 1 / low resolution
uniform sampler2D uNoise3;    // 32^3 random lattice as 32 slices of 34 x 34 side by side, each with a one-texel wrapped border

// One octave of value noise: the lattice read linear and repeating, as one trilinear fetch of a 3D texture would.
// Inside a slice the border makes the bilinear read wrap exactly; the two slices either side of p.z are mixed.
float noise3(vec3 p) {
    float z = p.z * 32.0 - 0.5;
    float zf = floor(z);
    vec2 s = fract(p.xy) * 32.0 + 1.0;
    vec2 scale = vec2(1.0 / 1088.0, 1.0 / 34.0);
    float a = textureLod(uNoise3, vec2(mod(zf, 32.0) * 34.0 + s.x, s.y) * scale, 0.0).r;
    float b = textureLod(uNoise3, vec2(mod(zf + 1.0, 32.0) * 34.0 + s.x, s.y) * scale, 0.0).r;
    return mix(a, b, z - zf);
}

void main() {
    float d = texture(uDepth, uv).r;
    bool sky = d <= SKY_DEPTH;
    vec3 P = sky ? posAt(uv, FAR_DEPTH) : posAt(uv, d);
    float dist = sky ? 220.0 : length(P);
    vec3 rd = P / max(length(P), 1e-4);
    vec3 L = normalize(uLightDir);
    float jit = hash12(gl_FragCoord.xy + fract(uTime * 7.3) * 91.0);

    // ── screen-space sun shadows ──
    // March from the surface toward the sun through the depth buffer. A step is in shadow when the scene there is in
    // front of the ray by more than a small bias but less than an object's thickness (so thin edges and the far
    // background don't cast). No per-pixel randomness: a stable result instead of grain. The softness grows with the
    // distance to the occluder, like a real penumbra.
    float shadow = 0.0;
    if (!sky && uShadowK > 0.001 && dist < 64.0 && uShadowSteps > 0) {
        vec3 n = normalAt(uv, P, rd, uTexel);
        float ndl = dot(n, L);
        if (ndl > 0.03) {
            vec3 rp = P + n * (0.05 + dist * 0.004);
            float maxLen = 3.5 + dist * 0.06;
            float travelled = 0.0;
            float bias = 0.025 + dist * 0.004;
            float thick = 0.9 + dist * 0.03;
            for (int i = 1; i <= 24; i++) {
                if (i > uShadowSteps) break;
                float t = float(i) / float(uShadowSteps);
                float len = maxLen * t * t;                 // short steps near the surface, longer further out
                vec3 sp = rp + L * len;
                vec4 c = uViewProj * vec4(sp, 1.0);
                if (c.w <= 0.05) break;
                vec2 suv = c.xy / c.w * 0.5 + 0.5;
                if (suv.x < 0.0 || suv.y < 0.0 || suv.x > 1.0 || suv.y > 1.0) break;
                float sd = texture(uDepth, suv).r;
                if (sd <= SKY_DEPTH) continue;
                float gap = length(sp) - length(posAt(suv, sd));
                if (gap > bias && gap < thick) { shadow = 1.0 - smoothstep(0.0, 1.0, len / maxLen) * 0.6; travelled = len; break; }
            }
            // fade out with distance and toward the screen edges, where the depth buffer runs out
            vec2 e = smoothstep(vec2(0.0), vec2(0.06), uv) * smoothstep(vec2(1.0), vec2(0.94), uv);
            shadow *= (1.0 - smoothstep(40.0, 64.0, dist)) * e.x * e.y * smoothstep(0.03, 0.2, ndl);
        }
    }

    // ── god rays ──
    float rays = 0.0;
    if (uRayK > 0.001 && uSunScreen.z > 0.001 && uSunScreen.w > 0.001) {
        vec2 sun = uSunScreen.xy, dv = sun - uv;
        float acc = 0.0, wsum = 0.0, w = 1.0;
        for (int i = 0; i < 24; i++) {
            if (i >= uRaySteps) break;
            vec2 s = uv + dv * ((float(i) + jit) / float(uRaySteps)) * 0.92;
            float open = step(texture(uDepth, s).r, SKY_DEPTH);
            acc += open * w * exp(-length((s - sun) * vec2(uTexel.y / uTexel.x, 1.0)) * 5.5);
            wsum += w;
            w *= 0.94;
        }
        rays = acc / max(wsum, 1e-3) * exp(-length(dv * vec2(uTexel.y / uTexel.x, 1.0)) * 1.6);
    }

    // ── volumetric height fog: only the transmittance is needed (in-scatter = (1 - T) * light) ──
    float T = 1.0;
    if (uFogK > 0.001 && uFogSteps > 0) {
        float start = uFogStart, end = min(dist, 220.0);
        if (end > start) {
            float ds = (end - start) / float(uFogSteps);
            for (int i = 0; i < 24; i++) {
                if (i >= uFogSteps) break;
                float t = start + (float(i) + jit) * ds;
                vec3 wp = rd * t;
                float dens = 0.028 * uFogK * exp(-max(wp.y + 1.6, -8.0) / max(uFogH, 0.5));
                vec3 np = (wp + uCamPos) * 0.07 + vec3(uTime * 0.35, uTime * 0.05, uTime * 0.22) * uDrift;
                dens *= 0.35 + 1.3 * (noise3(np / 32.0) * 0.7 + noise3((np * 2.3 + 9.1) / 32.0) * 0.3);
                dens *= smoothstep(start, start + 6.0, t);
                T *= exp(-min(dens, 0.35) * ds);
                if (T < 0.02) break;
            }
            if (sky) T = mix(1.0, T, smoothstep(0.35, 0.0, abs(rd.y)) * 0.8);
        }
    }

    // ── bloom: bright parts of the scene, gathered over a wide footprint ──
    vec3 bloom = vec3(0.0);
    if (uBloomK > 0.001) {
        for (int r = 0; r < 2; r++) {
            float rad = r == 0 ? 1.6 : 4.0;
            for (int i = 0; i < 6; i++) {
                float an = float(i) * 1.0472 + float(r) * 0.52 + jit;
                vec3 s = pow(max(texture(uScene, uv + vec2(cos(an), sin(an)) * rad * uLoTexel).rgb, 0.0), vec3(2.2));
                bloom += max(s - 0.55, 0.0) * (r == 0 ? 0.6 : 0.4);
            }
        }
        bloom /= 6.0;
    }

    outA = vec4(shadow, rays, T, clamp(dist / 256.0, 0.0, 1.0));
    outB = vec4(bloom, 1.0);
}
