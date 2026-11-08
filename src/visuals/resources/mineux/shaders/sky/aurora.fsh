
const int AURORA_STEPS = 22;

float auroraField(vec2 p, vec2 warp, float t) {
    vec2 q = p * vec2(0.45, 1.5) + warp;
    float n = vnoise2hq(q + vec2(t * 0.9, t * 0.15));
    float r = clamp(1.0 - abs(n - 0.5) * 9.0, 0.0, 1.0);
    return r * r * r;
}

float auroraRays(vec3 dir, float t) {
    float az = atan(dir.z, dir.x) * (256.0 / TAU);
    float a = vnoise2hq(vec2(az, t * 0.6));
    float b = vnoise2hq(vec2(az * 0.5 + 91.0, t * 0.35));
    float r = 0.25 + 1.3 * a * a + 0.55 * b;
    return mix(r, 0.85, smoothstep(0.45, 0.92, dir.y));
}

vec4 aurora(vec3 dir) {
    float t = uTime * 0.05;
    float sc = 1.0 / uScale;
    float denom = dir.y + 0.12;
    vec2 pm = dir.xz * (1.6 / denom) * sc;
    vec2 warp = vec2(vnoise2hq(pm * 0.11 + vec2(t * 0.35, 0.0)) * 5.0,
                     vnoise2hq(pm * 0.09 + vec2(4.0, -t * 0.25)) * 2.5);

    float rays = auroraRays(dir, t);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec3 acc = vec3(0.0);
    vec3 avg = vec3(0.0);
    for (int i = 0; i < AURORA_STEPS; i++) {
        float fi = float(i) + jitter;
        float h  = 1.0 + fi * 0.055 + fi * fi * 0.0012;
        vec2  p  = dir.xz * (h / denom) * sc;
        float f  = auroraField(p, warp, t) * rays;
        float k  = fi / float(AURORA_STEPS);
        vec3  c  = mix(uColor1, uColor2, smoothstep(0.15, 0.85, k));
        c *= mix(1.0, 1.6, smoothstep(0.75, 1.0, k));
        avg = mix(avg, c * f, 0.5);
        acc += avg * exp2(-fi * 0.11) * smoothstep(0.0, 2.5, fi);
    }
    acc *= 0.32 * uIntensity;
    float fade = smoothstep(0.0, 0.18, dir.y);
    acc *= fade;
    return vec4(acc, clamp(luma(acc) * 3.0, 0.0, 1.0));
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float y = dir.y;
    vec3 zenith  = vec3(0.0025, 0.0045, 0.016);
    vec3 horizon = vec3(0.010, 0.022, 0.040);
    vec3 col = mix(horizon, zenith, smoothstep(-0.02, 0.55, y));

    col += uColor1 * 0.018 * exp(-abs(y - 0.06) * 14.0) * uIntensity;

    if (y > -0.05) {
        float ext = smoothstep(-0.05, 0.25, y);
        col += starField(cdir, 1.0, 0.35) * uStars * 0.55 * ext;
    }

    if (y > 0.0) {
        vec4 a = aurora(dir);
        col = col * (1.0 - a.a * 0.6) + a.rgb;
        col += uColor1 * a.a * 0.04;
    }
    return col;
}
