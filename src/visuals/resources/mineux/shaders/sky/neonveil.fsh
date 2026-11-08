
// Neon Veil: tall curtains of light that ripple and breathe, with a glowing seam where they fold.
vec3 veilLayer(vec3 d, float k, float t) {
    float az = atan(d.z, d.x);
    float el = clamp(d.y, -0.2, 1.0);
    float x = az * (3.0 + k * 1.7) / uScale + k * 13.1;
    float fold = sin(x + t * (0.23 + 0.07 * k) + sin(x * 0.37 - t * 0.17) * 2.2);
    float seam = exp(-abs(fold) * 9.0);
    float sheet = 0.5 + 0.5 * fold;
    float n = fbm2_4(vec2(x * 1.3, el * 3.0 - t * 0.15 + k * 7.0));
    float rays = pow(vnoise2(vec2(x * 9.0 + t * 0.4, k * 19.0)), 3.0);
    float top = 0.25 + 0.18 * sin(x * 0.7 + t * 0.2 + k);
    float band = smoothstep(-0.05, 0.12, el) * smoothstep(top + 0.55, top, el);
    vec3 c = mix(uColor1, uColor2, clamp(sheet + (n - 0.5) * 0.8, 0.0, 1.0));
    float hue = sin(k * 2.0 + x * 0.25 + t * 0.1) * 0.5 + 0.5;
    c = mix(c, mix(uColor2, vec3(1.0), 0.25), hue * 0.3);
    return c * band * (seam * 2.2 + sheet * 0.35 + rays * 1.2) * (0.55 + 0.6 * n);
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    vec3 col = mix(vec3(0.004, 0.003, 0.012), vec3(0.0005, 0.0005, 0.002), smoothstep(0.0, 0.9, dir.y));
    col += starField(cdir, uStars, 0.6) * 0.8 * smoothstep(-0.05, 0.3, dir.y);
    vec3 v = vec3(0.0);
    for (int i = 0; i < 3; i++) v += veilLayer(dir, float(i), t + float(i) * 9.0) * (1.0 - float(i) * 0.25);
    col += v * 0.42 * uIntensity;
    col += mix(uColor1, uColor2, 0.5) * 0.05 * exp(-abs(dir.y - 0.02) * 12.0) * uIntensity;
    return col;
}
