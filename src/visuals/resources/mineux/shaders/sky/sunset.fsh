
vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 sun = normalize(vec3(0.0, 0.055, 1.0));
    float cosS = dot(dir, sun);
    float hl = length(dir.xz);
    float towardRaw = 0.5 + 0.5 * (dir.z / max(hl, 1e-4));
    float toward = mix(0.42, towardRaw, smoothstep(0.0, 0.35, hl));

    vec3 warm   = uColor1;
    vec3 zenith = uColor2 * 0.55;
    vec3 rose   = mix(warm, vec3(1.0, 0.45, 0.62), 0.55);

    float yy = max(y, 0.0);
    float band = exp(-yy * mix(7.0, 2.6, toward * toward));
    vec3 horizonCol = mix(rose * 0.55, warm * 1.15, pow(toward, 1.5));
    vec3 col = mix(zenith, horizonCol, band);
    col = mix(col, mix(zenith, warm, 0.5) * 0.8, exp(-yy * 22.0) * pow(toward, 3.0) * 0.6);

    float away = 1.0 - toward;
    float shadow = (1.0 - smoothstep(0.0, 0.07, y)) * away * away;
    col = mix(col, uColor2 * 0.18 + vec3(0.02, 0.02, 0.035), shadow * 0.75);
    col += vec3(0.55, 0.22, 0.34) * exp(-pow((y - 0.10) / 0.06, 2.0)) * away * 0.10;

    col += warm * (smoothstep(0.99955, 0.9998, cosS) * 30.0
                 + exp((cosS - 1.0) * 220.0) * 1.6
                 + exp((cosS - 1.0) * 14.0) * 0.30);

    if (y > 0.0) {
        vec2 uv = dir.xz / (y + 0.09) * (0.55 / uScale);
        vec2 wind = vec2(t * 0.012, t * 0.004);
        float n = fbm2_5(uv * 1.1 + wind);
        float d = fbm2_4(uv * 3.3 - wind * 1.7 + n);
        float cover = 0.62 - 0.18 * uIntensity;
        float c = smoothstep(cover, cover + 0.22, n * 0.75 + d * 0.35);
        float thick = smoothstep(cover + 0.05, cover + 0.45, n * 0.75 + d * 0.35);

        vec3 lit = mix(mix(vec3(0.35, 0.28, 0.42), rose, smoothstep(0.0, 0.6, toward)), warm * 1.4, pow(toward, 4.0));
        vec3 dark = mix(uColor2 * 0.25, vec3(0.20, 0.12, 0.16), toward);
        vec3 cc = mix(lit, dark, thick * 0.65);
        float rim = c * (1.0 - thick) * exp((cosS - 1.0) * 6.0) * 1.6;
        cc += warm * rim;
        float fade = smoothstep(0.0, 0.12, y);
        col = mix(col, cc, c * fade * 0.92);
    }

    if (y < 0.0) col = mix(horizonCol * 0.6, mix(horizonCol, zenith, 0.6) * 0.35, smoothstep(0.0, -0.9, y));
    return col;
}
