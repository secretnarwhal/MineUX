
// Crystal Lattice: a glowing geodesic lattice over the sky, with pulses of light running along its edges.
vec3 latticeLayer(vec2 uv, float t, float layer) {
    // triangular lattice: three families of parallel lines
    vec3 sum = vec3(0.0);
    float w = 0.012 + layer * 0.006;
    for (int k = 0; k < 3; k++) {
        float a = float(k) * 2.0944 + layer * 0.4;
        vec2 dirv = vec2(cos(a), sin(a));
        float s = dot(uv, dirv);
        float id = floor(s);
        float d = abs(fract(s) - 0.5);
        float line = exp(-pow((0.5 - d) / w, 2.0)) + exp(-(0.5 - d) / (w * 6.0)) * 0.25;
        vec2 tang = vec2(-dirv.y, dirv.x);
        float along = dot(uv, tang);
        float h = hash11(id * 3.7 + float(k) * 11.0 + layer * 5.0);
        float pulse = exp(-pow(fract(along * 0.25 - t * (0.18 + 0.2 * h) + h) - 0.5, 2.0) * 160.0);
        vec3 c = mix(uColor1, uColor2, h);
        sum += c * line * (0.25 + 2.4 * pulse * step(0.35, h));
    }
    // glowing nodes
    vec2 g = uv * mat2(1.0, 0.0, 0.57735, 1.1547);
    vec2 cell = fract(g) - 0.5;
    float node = exp(-dot(cell, cell) * 90.0) * (0.5 + 0.5 * sin(t * 2.0 + hash12(floor(g)) * 30.0));
    sum += mix(uColor1, vec3(1.0), 0.5) * node * 0.6;
    return sum;
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    vec3 col = vec3(0.002, 0.003, 0.008) + starField(cdir, uStars, 0.5) * 0.6;
    if (dir.y > -0.1) {
        vec3 d = cdir;
        vec2 uv = d.xz / (abs(d.y) + 0.35) * (3.0 / uScale);
        vec3 l = latticeLayer(uv, t, 0.0) + latticeLayer(uv * 0.55 + 7.3, t * 0.7, 1.0) * 0.45;
        float fade = smoothstep(-0.08, 0.3, dir.y);
        col += l * 0.35 * uIntensity * fade;
        col += mix(uColor1, uColor2, 0.5) * 0.06 * exp(-dir.y * 6.0) * fade * uIntensity;
    }
    return col;
}
