// Cells: the sky as soft coloured cells divided by glowing seams that pulse.

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    vec3 p = cdir * (3.0 / uScale) + vec3(0.0, t * 0.01, 0.0);
    vec3 ip = floor(p), fp = fract(p);
    float f1 = 9.0, f2 = 9.0;
    vec3 id1 = vec3(0.0);
    for (int z = -1; z <= 1; z++)
    for (int y = -1; y <= 1; y++)
    for (int x = -1; x <= 1; x++) {
        vec3 o = vec3(float(x), float(y), float(z));
        vec3 h = hash33(ip + o);
        vec3 c = o + 0.5 + 0.4 * sin(t * 0.08 + h * TAU) - fp;
        float d = dot(c, c);
        if (d < f1) { f2 = f1; f1 = d; id1 = ip + o; }
        else if (d < f2) f2 = d;
    }
    float edge = sqrt(f2) - sqrt(f1);
    float h = hash12(id1.xy + id1.z * 7.7);
    vec3 cell = mix(uColor1, uColor2, h);
    float pulse = 0.5 + 0.5 * sin(t * (0.25 + 0.35 * h) + h * 40.0);
    vec3 col = cell * (0.006 + 0.016 * pulse);

    float fw = max(fwidth(edge), 1e-4);
    float core = 1.0 - smoothstep(fw * 0.6, fw * 2.2, edge);
    float glow = exp(-edge * 14.0) * 0.22;
    float travel = 0.5 + 0.5 * sin(dot(cdir, vec3(3.0, 5.0, 4.0)) * 2.0 - t * 0.5);
    vec3 seam = mix(uColor1, uColor2, 0.5 + 0.5 * sin(dot(cdir, vec3(2.0, 1.0, 3.0)) * 2.5 + t * 0.07));
    col += seam * (core * 0.8 + glow) * (0.2 + 0.8 * travel * travel) * uIntensity;
    col += starField(cdir, uStars * 0.3, 1.0);
    return col;
}
