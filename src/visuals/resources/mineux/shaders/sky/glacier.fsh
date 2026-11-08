
vec2 cells(vec2 x, out vec2 id) {
    vec2 n = floor(x), f = fract(x);
    float f1 = 8.0, f2 = 8.0;
    id = n;
    for (int j = -1; j <= 1; j++)
    for (int i = -1; i <= 1; i++) {
        vec2 g = vec2(float(i), float(j));
        vec2 o = hash22(n + g) * 0.85 + 0.075;
        float d = length(g + o - f);
        if (d < f1) { f2 = f1; f1 = d; id = n + g; }
        else if (d < f2) f2 = d;
    }
    return vec2(f1, f2 - f1);
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 light = uColor1;
    vec3 deep  = uColor2;
    vec3 col = mix(deep * 0.05, deep * 0.5, smoothstep(-0.6, 0.9, y));

    if (y > 0.0) {
        vec2 uv = dir.xz / max(y, 0.06) * (0.9 / uScale);
        vec2 w = vec2(vnoise2(uv * 0.7), vnoise2(uv * 0.7 + 19.0)) * 0.55;
        vec2 id;
        vec2 c = cells(uv + w, id);
        float px = fwidth(uv.x) * 1.5 + 1e-4;

        float shimmer = 0.75 + 0.25 * sin(t * 1.3 + vnoise2hq(uv * 3.0 + t * 0.15) * 6.0);
        float crack = 1.0 - smoothstep(0.0, 0.035 + px, c.y);
        float glowC = exp(-c.y * 9.0);
        float plateR = hash12(id);
        vec3 plate = deep * (0.5 + 0.4 * plateR) + light * (0.10 + 0.12 * plateR) * (1.0 - c.x * 0.6);
        float r1 = vnoise2hq(uv * 2.2 + vec2(t * 0.06, -t * 0.04));
        float r2 = vnoise2hq(uv * 3.1 - vec2(t * 0.05, t * 0.07) + r1);
        plate += light * pow(1.0 - abs(r1 - r2), 10.0) * 0.18;
        vec2 bg = uv * 9.0; vec2 bc = floor(bg); vec2 bo = hash22(bc + id);
        float bub = (1.0 - smoothstep(0.03, 0.06 + px * 9.0, length(fract(bg) - 0.2 - 0.6 * bo))) * step(0.82, hash12(bc * 1.3));
        plate += light * bub * 0.35;

        vec3 ice = plate + light * (crack * 1.6 + glowC * 0.45) * shimmer;
        float view = smoothstep(0.0, 0.5, y);
        col = mix(col, ice * mix(0.35, 1.0, view), smoothstep(0.0, 0.2, y));
    }

    if (y < 0.94) {
        float az = atan(dir.z, dir.x) * (256.0 / TAU);
        float s1 = vnoise2hq(vec2(az * 0.8 + sin(t * 0.2) * 2.0, t * 0.10));
        float s2 = vnoise2hq(vec2(az * 2.1 + 40.0, t * 0.16));
        float shafts = pow(s1, 4.0) * 1.3 + pow(s2, 6.0) * 1.1;
        float a = acos(clamp(y, -1.0, 1.0));
        float fall = smoothstep(0.35, 0.9, a) * exp(-max(a - 0.9, 0.0) * 1.8);
        col += light * shafts * fall * 0.35 * uIntensity;
    }

    vec3 sd = normalize(dir + vec3(t * 0.0015, t * 0.003, 0.0));
    col += starField(sd, 1.1, 0.6) * 0.14 * uStars * vec3(0.85, 0.97, 1.0);

    col *= mix(0.3, 1.0, smoothstep(-0.7, 0.1, y));
    return col;
}
