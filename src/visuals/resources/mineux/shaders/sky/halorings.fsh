// Halo Rings: a colour wash with glowing rings circling the whole sky.

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float g = fbm3_3(cdir * 1.2 + vec3(t * 0.01));
    vec3 col = mix(uColor2, uColor1, smoothstep(0.3, 0.7, g)) * (0.018 + 0.03 * g);
    col += mix(uColor1, uColor2, 0.5 + 0.5 * cdir.y) * 0.012;

    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        vec3 h = hash33(vec3(fi * 13.1, fi * 7.7, 3.3));
        float a1 = h.x * TAU + t * (0.01 + 0.015 * h.z), a2 = (h.y - 0.5) * 2.4;
        vec3 axis = normalize(vec3(cos(a1) * cos(a2), sin(a2), sin(a1) * cos(a2)));
        float off = (h.z - 0.5) * 0.9;
        float d = dot(cdir, axis) - off;
        float fw = max(fwidth(d), 1e-5);
        float w = max(0.0025 * uScale, fw * 0.9);
        float core = exp(-(d * d) / (w * w));
        float glow = exp(-abs(d) / (w * 9.0)) * 0.28;

        vec3 u = normalize(cross(axis, abs(axis.y) < 0.9 ? vec3(0, 1, 0) : vec3(1, 0, 0)));
        vec3 v = cross(axis, u);
        float ang = atan(dot(cdir, v), dot(cdir, u));
        float s = fract(ang / TAU - t * (0.015 + 0.02 * h.y) * (h.x > 0.5 ? 1.0 : -1.0));
        float spark = exp(-s * 14.0) + exp(-(1.0 - s) * 60.0);
        vec3 rc = mix(uColor1, uColor2, h.y);
        col += rc * (core * 0.9 + glow) * (0.18 + 1.3 * spark) * uIntensity;
    }
    col += starField(cdir, uStars * 0.5, 1.0);
    return col;
}
