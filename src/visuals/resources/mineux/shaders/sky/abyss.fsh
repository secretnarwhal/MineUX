
vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 light = uColor1;
    vec3 deep  = uColor2;

    vec3 col = mix(deep * 0.06, deep * 0.55, smoothstep(-0.6, 0.9, y));

    float a = acos(clamp(y, -1.0, 1.0));
    const float WINDOW = 0.85;
    if (y > 0.0) {
        vec2 uv = dir.xz / max(y, 0.08) * (1.6 / uScale);
        vec2 w1 = vec2(t * 0.11, t * 0.05), w2 = vec2(-t * 0.07, t * 0.09);
        float r1 = vnoise2hq(uv * 1.3 + w1 + vnoise2(uv * 0.6 - w2) * 1.5);
        float r2 = vnoise2hq(uv * 2.1 + w2 + r1);
        float caustic = pow(1.0 - abs(r1 - r2), 9.0);
        float rip = r1 * 0.5 + r2 * 0.5;

        float inWindow = 1.0 - smoothstep(WINDOW - 0.05, WINDOW + 0.02, a);
        vec3 window = light * 0.32 * (0.45 + 1.1 * rip) + mix(light, vec3(1.0), 0.4) * caustic * 0.75;
        float rim = exp(-pow((a - WINDOW) / 0.03, 2.0)) * (0.6 + 0.6 * rip);
        vec3 mirror = deep * (0.35 + 0.35 * rip) + light * caustic * 0.12;
        vec3 surf = mix(mirror, window, inWindow) + light * rim * 0.22;
        col = mix(col, surf, smoothstep(0.0, 0.25, y));
    }

    if (a > WINDOW * 0.9) {
        float az = atan(dir.z, dir.x) * (256.0 / TAU);
        float sway = sin(t * 0.4) * 3.0;
        float s1 = vnoise2hq(vec2(az * 0.55 + sway, t * 0.18));
        float s2 = vnoise2hq(vec2(az * 1.3 - sway * 0.6 + 50.0, t * 0.27));
        float shafts = pow(s1, 3.0) * 1.2 + pow(s2, 4.0) * 0.8;
        float fall = smoothstep(WINDOW * 0.9, WINDOW + 0.25, a) * exp(-(a - WINDOW) * 1.6);
        col += light * shafts * fall * 0.55 * uIntensity * step(WINDOW * 0.9, a);
    }

    vec3 sd = normalize(dir + vec3(0.0, -t * 0.004, t * 0.002));
    col += starField(normalize(sd + vec3(sin(t * 0.03), 0.0, cos(t * 0.02)) * 0.002), 0.9, 0.0) * 0.18 * uStars
         * vec3(0.8, 0.95, 1.0);

    col *= mix(0.35, 1.0, smoothstep(-0.7, 0.1, y));
    return col;
}
