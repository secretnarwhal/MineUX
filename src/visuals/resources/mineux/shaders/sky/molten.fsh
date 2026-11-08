
vec2 crust(vec2 x, out vec2 id) {
    vec2 n = floor(x), f = fract(x);
    float f1 = 8.0, f2 = 8.0;
    id = n;
    for (int j = -1; j <= 1; j++)
    for (int i = -1; i <= 1; i++) {
        vec2 g = vec2(float(i), float(j));
        vec2 o = hash22(n + g) * 0.8 + 0.1;
        float d = length(g + o - f);
        if (d < f1) { f2 = f1; f1 = d; id = n + g; }
        else if (d < f2) f2 = d;
    }
    return vec2(f1, f2 - f1);
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 magma = uColor1;
    vec3 smoke = uColor2;
    vec3 hot = mix(magma, vec3(1.0, 0.95, 0.7), 0.5);

    vec3 col = mix(smoke * 0.12, smoke * 0.6, smoothstep(-0.6, 0.8, y));

    if (y > 0.0) {
        vec2 uv = dir.xz / max(y, 0.07) * (0.85 / uScale) + vec2(t * 0.006, t * 0.003);
        vec2 w = vec2(vnoise2(uv * 0.8 + t * 0.01), vnoise2(uv * 0.8 + 23.0)) * 0.6;
        vec2 id;
        vec2 c = crust(uv + w, id);
        float px = fwidth(uv.x) * 1.5 + 1e-4;
        float breathe = 0.7 + 0.3 * sin(t * 0.8 + hash12(id) * 6.0);
        float crack = 1.0 - smoothstep(0.0, 0.05 + px, c.y);
        float bleed = exp(-c.y * 7.0);
        float r1 = vnoise2hq(uv * 2.0 + vec2(t * 0.05, t * 0.02));
        float r2 = vnoise2hq(uv * 3.0 - vec2(t * 0.03, t * 0.05) + r1);
        float veins = pow(1.0 - abs(r1 - r2), 12.0);
        vec3 plate = smoke * (0.25 + 0.3 * hash12(id)) + magma * veins * 0.35 + magma * bleed * 0.35 * breathe;
        vec3 surf = plate + mix(magma, hot, crack) * crack * 1.8 * breathe;
        float view = smoothstep(0.0, 0.45, y);
        col = mix(col, surf * mix(0.4, 1.0, view), smoothstep(0.0, 0.18, y));
    }

    if (y < 0.921) {
        float az = atan(dir.z, dir.x) * (256.0 / TAU);
        float s1 = vnoise2hq(vec2(az * 0.7 + sin(t * 0.3) * 2.0, t * 0.12));
        float s2 = vnoise2hq(vec2(az * 1.9 + 70.0, t * 0.2));
        float shafts = pow(s1, 3.0) + pow(s2, 5.0) * 0.8;
        float a = acos(clamp(y, -1.0, 1.0));
        float fall = smoothstep(0.4, 0.95, a) * exp(-max(a - 0.95, 0.0) * 1.7);
        col += magma * shafts * fall * 0.4 * uIntensity;
    }

    vec3 ed = normalize(dir + vec3(sin(t * 0.05) * 0.004, -t * 0.006, 0.0));
    vec3 em = starLayer(ed, 70.0, 0.18, 0.0012, 5.0, 0.7) + starLayer(ed, 150.0, 0.18, 0.0008, 11.0, 0.7) * 0.6;
    col += hot * luma(em) * 0.6 * uStars;

    col *= mix(0.3, 1.0, smoothstep(-0.7, 0.1, y));
    return col;
}
