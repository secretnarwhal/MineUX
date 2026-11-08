
// Liquid Light: an iridescent film of light flowing across the sky, like oil on water lit from behind.
vec3 filmColor(float x) {
    return 0.5 + 0.5 * cos(6.2832 * (x + vec3(0.0, 0.33, 0.67)));
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime * 0.25;
    vec2 p = cdir.xz / (abs(cdir.y) + 0.4) * (1.6 / uScale);
    vec2 w = vec2(fbm2_4(p + vec2(t, -t * 0.6)), fbm2_4(p * 1.3 + vec2(-t * 0.8, t * 0.4) + 7.0));
    float h = fbm2_5(p + w * 2.6 + vec2(t * 0.5, 0.0));
    float thick = h * 3.2 + w.x * 1.4;
    vec3 iri = filmColor(thick);
    vec3 tint = mix(uColor1, uColor2, smoothstep(0.3, 0.7, h));
    vec3 c = mix(iri, tint, 0.45);
    float ridge = exp(-abs(fract(thick * 1.5) - 0.5) * 9.0);
    float glow = pow(h, 2.2) * 1.4 + ridge * 0.6;
    float fade = smoothstep(-0.1, 0.35, dir.y);
    vec3 col = vec3(0.004, 0.003, 0.01) + starField(cdir, uStars * 0.5, 0.4) * 0.4;
    col += c * glow * 0.45 * uIntensity * fade;
    col += tint * 0.05 * exp(-abs(dir.y) * 9.0) * uIntensity;
    return col;
}
