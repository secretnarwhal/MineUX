
// Ether Waves: glowing sheets of light rolling across the sky like slow ocean swells.
vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    vec3 col = mix(vec3(0.004, 0.003, 0.011), vec3(0.001, 0.001, 0.003), smoothstep(0.0, 0.8, dir.y));
    col += starField(cdir, uStars, 0.6) * 0.7 * smoothstep(0.05, 0.4, dir.y);
    float az = atan(cdir.z, cdir.x);
    float el = dir.y;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float h = hash11(fi * 7.31 + 2.0);
        float freq = (1.0 + h * 2.0) / uScale;
        float y = 0.08 + fi * 0.07 + 0.06 * sin(az * freq + t * (0.15 + 0.1 * h) + h * 6.2832)
                + 0.03 * sin(az * freq * 2.7 - t * 0.21 + fi);
        float d = el - y;
        float sheet = exp(-max(d, 0.0) * (14.0 + fi * 3.0)) * smoothstep(-0.004, 0.0, d);
        float edge = exp(-abs(d) * 160.0);
        vec3 c = mix(uColor1, uColor2, fract(h + fi * 0.17));
        float shimmer = 0.6 + 0.4 * vnoise2(vec2(az * 20.0 - t * (0.6 + h), fi * 9.0));
        sum += c * (sheet * 0.35 + edge * 1.6) * shimmer * (1.0 - fi * 0.1);
    }
    col += sum * 0.4 * uIntensity * smoothstep(-0.05, 0.08, el);
    return col;
}
