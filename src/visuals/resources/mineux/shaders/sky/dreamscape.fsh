
const vec3 DS_SUN = vec3(0.6255, 0.3363, -0.7040);

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 col = mix(uColor2, uColor1, smoothstep(-0.02, 0.55, y));
    col = mix(col, mix(uColor2, vec3(1.0, 0.88, 0.75), 0.5), exp(-max(y, 0.0) * 10.0) * 0.5);

    float sd = max(dot(dir, DS_SUN), 0.0);
    col += vec3(1.0, 0.92, 0.80) * (pow(sd, 400.0) * 3.0 + pow(sd, 18.0) * 0.25 + pow(sd, 3.0) * 0.06);

    if (y > 0.0) {
        float denom = y + 0.06;
        vec2 p  = dir.xz / denom * (1.3 / uScale) + vec2(t * 0.012, t * 0.004);
        float cov = mix(0.58, 0.34, clamp(uIntensity * 0.5, 0.0, 1.0));
        float n  = fbm2_5(p);
        float dens = smoothstep(cov, cov + 0.18, n);
        if (dens > 0.001) {
            vec2 ld = normalize(DS_SUN.xz) * 0.06;
            float n2 = fbm2_4(p + ld);
            float lit = clamp(0.6 + (n - n2) * 6.0, 0.0, 1.0);
            vec3 shadow = mix(uColor2, uColor1, 0.35) * 0.75;
            vec3 cc = mix(shadow, vec3(1.0, 0.97, 0.93), lit);
            cc += vec3(1.0, 0.9, 0.75) * pow(sd, 6.0) * (1.0 - dens) * 0.8;
            float fade = smoothstep(0.0, 0.12, y);
            col = mix(col, cc, dens * fade * 0.95);
        }
    }
    col = mix(col, uColor2 * 0.8, smoothstep(0.0, -0.2, y));
    return col;
}
