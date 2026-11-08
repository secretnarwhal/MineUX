// Flow Lines: drifting colour with glowing contour lines that wrap the full sky.

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    vec3 p = cdir * (1.25 / uScale);
    vec3 w = vec3(fbm3_3(p * 0.7 + vec3(t * 0.015, 0.0, 0.0)),
                  fbm3_3(p * 0.7 + vec3(5.2, t * 0.012, 1.3)),
                  fbm3_3(p * 0.7 + vec3(1.7, 9.2, -t * 0.01)));
    float n = fbm3_3(p + w * 1.1);

    vec3 field = mix(uColor2, uColor1, smoothstep(0.25, 0.75, n));
    vec3 col = field * (0.010 + 0.020 * n * n);

    float k = n * 11.0;
    float dist = 0.5 - abs(fract(k) - 0.5);
    float fw = max(fwidth(k), 1e-4);
    float core = 1.0 - smoothstep(0.0, fw * 1.3, dist);
    float glow = exp(-dist / (fw * 9.0));

    // light travelling across the levels like a slow wave
    float lvl = floor(k);
    float wave = 0.5 + 0.5 * sin(lvl * 0.9 - t * 0.6 + w.x * 6.0);
    float lit = 0.12 + 0.88 * pow(wave, 3.0);
    vec3 lineCol = mix(uColor2, uColor1, fract(lvl * 0.137 + 0.3));
    lineCol = mix(lineCol, vec3(1.0), 0.25 * pow(wave, 6.0));
    col += lineCol * (core * 0.9 + glow * 0.10) * lit * uIntensity;
    col += starField(cdir, uStars * 0.3, 1.0);
    return col;
}
