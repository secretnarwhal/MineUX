
vec3 skyColor(vec3 dir, vec3 cdir) {
    vec3 p = cdir * (2.0 / uScale);
    float t = uTime * 0.006;
    p += (vec3(fbm3_3(p * 1.4 + 2.0), fbm3_3(p * 1.4 + 9.0), 0.5) - 0.5) * 1.4;

    float n1 = fbm3_5(p * 1.3 + vec3(t, 0.0, 0.0));
    float n2 = fbm3_5(p * 2.1 + vec3(7.3, -t, 3.0));
    float g1 = smoothstep(0.38, 0.86, n1); g1 *= g1;
    float g2 = smoothstep(0.42, 0.88, n2); g2 *= g2;
    float dust = smoothstep(0.50, 0.72, vnoise3(p * 4.5 + 31.0) * 0.6 + n2 * 0.4);

    vec3 gas = uColor1 * g1 * 1.1 + uColor2 * g2 * 0.95;
    gas += vec3(1.0, 0.9, 0.95) * pow(g1 * g2, 1.5) * 1.6;
    gas *= 1.0 - 0.8 * dust;
    gas += mix(uColor1, uColor2, 0.5) * 0.012 * (n1 + n2);

    vec3 col = vec3(0.0015, 0.0012, 0.0035) + gas * uIntensity * 0.75;
    col += starField(cdir, 1.4 + (g1 + g2) * 1.5, 0.3) * uStars * 0.75;

    col += starLayerBloom(cdir, 16.0, 0.35, 0.0022, 0.012, 0.025 / 0.9, 41.0, 0.15) * uStars * 0.9;

    col *= smoothstep(-0.12, 0.22, dir.y) * 0.85 + 0.15;
    return col;
}
