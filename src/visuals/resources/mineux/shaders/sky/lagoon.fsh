
float causticNet(vec2 uv, float t) {
    float r1 = vnoise2hq(uv * 1.4 + vec2(t * 0.16, t * 0.07) + vnoise2(uv * 0.5 - t * 0.05) * 1.8);
    float r2 = vnoise2hq(uv * 2.3 + vec2(-t * 0.11, t * 0.13) + r1 * 1.2);
    return pow(1.0 - abs(r1 - r2), 7.0);
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 sun = uColor1;
    vec3 sea = uColor2;
    vec3 sand = mix(vec3(0.95, 0.85, 0.62), sun, 0.25);

    vec3 col = mix(sea * 0.18, sea * 0.75, smoothstep(-0.5, 0.6, y));

    float a = acos(clamp(y, -1.0, 1.0));
    const float WINDOW = 0.85;
    if (y > 0.0) {
        vec2 uv = dir.xz / max(y, 0.08) * (1.4 / uScale);
        float cn = causticNet(uv, t);
        float rip = vnoise2hq(uv * 1.7 + vec2(t * 0.2, -t * 0.1));
        float inWindow = 1.0 - smoothstep(WINDOW - 0.06, WINDOW + 0.02, a);
        vec3 sky = mix(sea * 0.9, vec3(0.55, 0.85, 1.0), 0.35) * (0.45 + 0.55 * rip);
        vec3 sunDir = normalize(vec3(0.25, 1.0, 0.15));
        float cs = dot(dir, sunDir);
        sky += sun * (exp((cs - 1.0) * 160.0) * 3.0 + exp((cs - 1.0) * 14.0) * 0.6) * (0.8 + 0.4 * rip);
        sky += mix(sun, vec3(1.0), 0.4) * cn * 0.7 * uIntensity;
        vec3 floorC = sand * 0.35 * (0.6 + 0.6 * vnoise2hq(uv * 0.6)) + sun * causticNet(uv * 0.8 + 9.0, t * 0.9) * 0.35;
        floorC = mix(floorC, sea * 0.55, 0.6);
        float rim = exp(-pow((a - WINDOW) / 0.025, 2.0));
        vec3 surf = mix(floorC, sky, inWindow) + sun * rim * 0.3;
        col = mix(col, surf, smoothstep(0.0, 0.2, y));
    }

    if (a > WINDOW * 0.85) {
        float az = atan(dir.z, dir.x) * (256.0 / TAU);
        float sway = sin(t * 0.5) * 2.5;
        float s1 = vnoise2hq(vec2(az * 0.6 + sway, t * 0.22));
        float s2 = vnoise2hq(vec2(az * 1.6 - sway * 0.7 + 30.0, t * 0.33));
        float shafts = pow(s1, 2.5) + pow(s2, 3.5) * 0.7;
        float fall = smoothstep(WINDOW * 0.85, WINDOW + 0.2, a) * exp(-(a - WINDOW) * 1.2);
        col += mix(sun, sea, 0.45) * shafts * fall * 0.32 * uIntensity * step(WINDOW * 0.85, a);
    }

    col += starField(normalize(dir + vec3(0.0, t * 0.002, t * 0.003)), 0.7, 0.8) * 0.12 * uStars * sun;
    return col;
}
