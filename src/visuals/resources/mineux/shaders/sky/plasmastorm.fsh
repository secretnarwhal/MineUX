
// Plasma Storm: a dome of domain-warped plasma with white-hot filaments crawling across it.
vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime * 0.35;
    vec3 p = cdir * (2.2 / uScale);
    vec3 q = vec3(fbm3_3(p + vec3(0.0, t, 0.0)), fbm3_3(p + vec3(5.2, 1.3 - t * 0.7, 2.8)), fbm3_3(p + vec3(-3.1, 2.2, t * 0.6)));
    vec3 r = vec3(fbm3_3(p + q * 2.4 + vec3(1.7, 9.2, t)), fbm3_3(p + q * 2.4 + vec3(8.3, 2.8, -t)), 0.0);
    float f = fbm3_5(p + vec3(r.xy, 0.0) * 2.1);
    float fil = exp(-abs(f - 0.5) * 22.0);
    float fil2 = exp(-abs(fbm3_3(p * 2.7 + q * 3.0 - t) - 0.5) * 30.0);
    vec3 base = mix(uColor2 * 0.18, uColor1 * 0.55, smoothstep(0.25, 0.85, f));
    vec3 hot = mix(uColor1, vec3(1.0), 0.45);
    vec3 col = base * (0.35 + 0.8 * length(q)) + hot * (fil * 1.6 + fil2 * 0.9);
    float flash = pow(max(0.0, sin(uTime * 0.9 + f * 6.0)), 18.0) * 0.8;
    col += hot * flash * fil;
    float dome = smoothstep(-0.12, 0.25, dir.y);
    vec3 night = vec3(0.002, 0.002, 0.005) + starField(cdir, uStars * 0.6, 0.5) * 0.5;
    return mix(night, night + col * 0.5 * uIntensity, dome);
}
