
vec3 skyColor(vec3 dir, vec3 cdir) {
    const vec3 N  = vec3(0.2425, 0.5336, 0.8104);
    const vec3 TA = vec3(0.9579, 0.0000, -0.2867);
    const vec3 TB = vec3(0.1530, -0.8457, 0.5112);

    float b = dot(cdir, N);
    float l = atan(dot(cdir, TB), dot(cdir, TA));
    vec3  p = cdir * (2.6 / uScale);

    float wob   = vnoise3(p * 1.2 + 3.0) - 0.5;
    float bb    = b + wob * 0.07;
    float width = 0.16 + 0.05 * vnoise3(p * 0.8 + 9.0);
    float band  = exp(-bb * bb / (width * width));
    float halo  = exp(-bb * bb / (width * width * 6.0));
    float core  = exp(-l * l * 1.8 - bb * bb * 30.0);

    float clump = fbm3_5(p * 2.2 + vec3(0.0, 0.0, uTime * 0.004));
    float stars = clump * clump * 1.8;

    float lane  = fbm3_5(p * 3.4 + 11.0);
    float dust  = smoothstep(0.42, 0.68, lane) * exp(-bb * bb / 0.0045);
    dust = max(dust, smoothstep(0.55, 0.8, lane) * band * 0.6);

    vec3 armCol  = uColor2;
    vec3 coreCol = uColor1;
    vec3 glow = armCol  * (band * (0.10 + 0.55 * stars) + halo * 0.035)
              + coreCol * (core * (0.9 + 1.4 * clump) + band * clump * 0.08);
    glow *= (1.0 - 0.88 * dust);

    float knot = smoothstep(0.70, 0.92, vnoise3(p * 11.0 + 21.0)) * band * clump * (1.0 - dust);
    glow += mix(coreCol, vec3(1.0, 0.25, 0.55), 0.6) * knot * 0.10;

    vec3 col = vec3(0.0016, 0.0022, 0.0060) + glow * uIntensity;
    col += starField(cdir, 0.8 + band * 2.0 + core * 1.5, 0.3) * uStars * 0.65;

    col *= smoothstep(-0.12, 0.22, dir.y) * 0.85 + 0.15;
    col += uColor2 * 0.006 * exp(-abs(dir.y) * 9.0);
    return col;
}
