// Prism Grid: a slow colour field wrapped around the whole sky, with a glowing
// latitude/longitude lattice and soft light pulses running along it.

vec3 colourField(vec3 d, float t) {
    float f = fbm3_3(d * 1.4 + vec3(0.0, t * 0.02, t * 0.015));
    float g = fbm3_3(d * 0.8 - vec3(t * 0.01, 0.0, 3.1));
    vec3 col = mix(uColor1, uColor2, smoothstep(0.30, 0.72, f));
    return col * (0.020 + 0.050 * g * g);
}

float gridCore(float v, float cells) {
    float k = v * cells;
    float dist = 0.5 - abs(fract(k) - 0.5);
    float fw = max(fwidth(k), 1e-4);
    return 1.0 - smoothstep(0.0, fw * 1.4, dist);
}

float gridLine(float v, float cells, float scale) {
    float k = v * cells;
    float dist = abs(fract(k) - 0.5);
    dist = 0.5 - dist;
    float fw = max(fwidth(k), 1e-4);
    float core = 1.0 - smoothstep(0.0, fw * 1.4, dist);
    float glow = exp(-dist / (fw * 8.0)) * 0.30;
    return (core + glow) * scale;
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    vec3 col = colourField(cdir, t);

    float lon = atan(cdir.z, cdir.x) / TAU + 0.5;
    float lat = asin(clamp(cdir.y, -1.0, 1.0)) / PI + 0.5;
    float merid = 24.0 / uScale, paral = 12.0 / uScale;
    float pole = clamp(sqrt(max(1.0 - cdir.y * cdir.y, 0.0)) * 1.6, 0.0, 1.0);

    float lm = gridLine(lon, merid, pole);
    float lp = gridLine(lat, paral, 1.0);

    // pulses running along the meridians (up) and parallels (around)
    float mi = floor(lon * merid), pi_ = floor(lat * paral);
    float hm = hash11(mi * 3.71 + 1.0), hp = hash11(pi_ * 5.13 + 7.0);
    float sm = fract(lat * 1.0 - t * (0.03 + 0.05 * hm) + hm);
    float sp = fract(lon * 2.0 + t * (0.02 + 0.04 * hp) * (hp > 0.5 ? 1.0 : -1.0) + hp * 3.0);
    float pm = exp(-pow((sm - 0.5) * 9.0, 2.0)) * step(0.45, hm);
    float pp = exp(-pow((sp - 0.5) * 12.0, 2.0)) * step(0.5, hp);

    vec3 lineCol = mix(uColor1, uColor2, 0.5 + 0.5 * sin(lon * TAU * 2.0 + t * 0.1));
    float breathe = 0.75 + 0.25 * sin(t * 0.4 + lat * 9.0);
    float cm = gridCore(lon, merid) * pole, cp = gridCore(lat, paral);
    col += lineCol * (lm * 0.07 + lp * 0.07 + (cm * pm + cp * pp) * 1.6 + (lm * pm + lp * pp) * 0.25) * breathe * uIntensity;

    // brighter nodes where the lines cross
    col += mix(lineCol, vec3(1.0), 0.4) * lm * lp * 0.35 * uIntensity;
    col += starField(cdir, uStars * 0.4, 1.0);
    return col;
}
