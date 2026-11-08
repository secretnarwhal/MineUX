
// Mandala: a slowly turning kaleidoscope of glowing petals overhead.
vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime * 0.12;
    vec3 col = vec3(0.003, 0.002, 0.008) + starField(cdir, uStars, 0.6) * 0.7;
    float r = acos(clamp(dir.y, -1.0, 1.0)) / 1.5708 * (1.0 / uScale);
    float a = atan(dir.z, dir.x) + t;
    float seg = 12.0;
    float sa = abs(mod(a, 6.2832 / seg) - 3.1416 / seg);
    vec2 q = vec2(cos(sa), sin(sa)) * r;
    float petals = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float rr = 0.18 + fi * 0.2 + 0.03 * sin(uTime * 0.4 + fi);
        vec2 c = vec2(rr, 0.0);
        float d = length((q - c) * vec2(1.0, 2.2 - fi * 0.3)) - (0.07 + fi * 0.03);
        petals += exp(-abs(d) * 70.0) * (1.0 - fi * 0.15) + exp(-max(d, 0.0) * 18.0) * 0.12;
    }
    float rings = 0.0;
    for (int i = 0; i < 5; i++) {
        float rr = 0.1 + float(i) * 0.17;
        rings += exp(-pow((r - rr) * 90.0, 2.0)) * (0.5 + 0.5 * sin(a * seg * 2.0 + uTime * 0.6 + float(i)));
    }
    float pulse = 0.75 + 0.25 * sin(uTime * 0.8 - r * 9.0);
    vec3 c1 = mix(uColor1, uColor2, smoothstep(0.0, 0.9, r));
    vec3 m = c1 * petals * 1.2 * pulse + mix(uColor2, vec3(1.0), 0.3) * rings * 0.7 + mix(uColor1, vec3(1.0), 0.7) * exp(-r * 18.0) * 1.5;
    float fade = smoothstep(-0.05, 0.3, dir.y);
    col += m * 0.45 * uIntensity * fade;
    return col;
}
