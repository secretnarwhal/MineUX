
vec3 jelly(vec3 dir, float i, float t) {
    float h = hash11(i * 7.13 + 1.0);
    float azj = i * 1.9 + t * (0.010 + 0.012 * h) * (h > 0.5 ? 1.0 : -1.0);
    float elj = 0.15 + 0.45 * hash11(i * 3.7 + 2.0) + 0.05 * sin(t * 0.21 + i);
    vec3 c = normalize(vec3(cos(azj) * cos(elj), sin(elj), sin(azj) * cos(elj)));
    float size = (0.09 + 0.06 * hash11(i * 11.1)) * uScale;
    if (dot(dir, c) < cos(min(7.8 * size, 3.0))) return vec3(0.0);
    vec3 right = normalize(cross(c, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, c);
    vec3 rel = dir - c * dot(dir, c);
    vec2 p = vec2(dot(rel, right), dot(rel, up)) / size;
    if (dot(dir, c) < 0.0 || dot(p, p) > 60.0) return vec3(0.0);

    float pulse = 0.5 + 0.5 * sin(t * (1.4 + h) + i * 3.0);
    vec2 q = p * vec2(1.0 + 0.18 * pulse, 1.0 - 0.12 * pulse);
    float r = length(q * vec2(1.0, 1.25));
    float bell = (1.0 - smoothstep(0.85, 1.0, r)) * smoothstep(-0.25, 0.05, q.y);
    float rim  = exp(-pow((r - 0.9) / 0.09, 2.0)) * smoothstep(-0.3, 0.0, q.y);
    float inner = exp(-dot(q - vec2(0.0, 0.25), q - vec2(0.0, 0.25)) * 3.0);
    float g = bell * 0.25 + rim * 0.9 + inner * 0.55;
    for (int k = 0; k < 5; k++) {
        float fk = float(k);
        float x0 = -0.6 + fk * 0.3;
        float yy = -q.y;
        if (yy > 0.0 && yy < 3.6) {
            float wv = x0 + sin(yy * 2.4 - t * 1.6 + fk * 1.3 + i) * 0.12 * yy;
            float d = abs(q.x - wv);
            g += exp(-d * d / 0.0025) * exp(-yy * 0.7) * 0.5;
        }
    }
    vec3 col = mix(uColor1, mix(uColor1, vec3(0.75, 0.4, 1.0), 0.6), hash11(i * 5.3));
    return col * g * (0.55 + 0.45 * pulse);
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 glow = uColor1;
    vec3 deep = uColor2;

    vec3 col = mix(deep * 0.25, deep * 1.0, smoothstep(-0.5, 1.0, y));

    if (y > 0.3) {
        vec2 uv = dir.xz / y * (1.2 / uScale);
        float r1 = vnoise2hq(uv * 1.3 + vec2(t * 0.05, t * 0.03));
        float r2 = vnoise2hq(uv * 2.1 - vec2(t * 0.04, -t * 0.05) + r1);
        float veins = pow(1.0 - abs(r1 - r2), 12.0);
        float k = smoothstep(0.3, 0.95, y);
        col += mix(deep * 4.0, glow, 0.25) * (0.05 + veins * 0.35) * k * k;
    }

    vec3 j = vec3(0.0);
    for (int i = 0; i < 6; i++) j += jelly(dir, float(i), t);
    col += j * 0.6 * uIntensity;

    vec3 pd = normalize(dir + vec3(sin(t * 0.02) * 0.01, t * 0.0015, 0.0));
    vec3 plankton = starLayer(pd, 90.0, 0.22, 0.0011, 3.0, 0.95) + starLayer(pd, 200.0, 0.2, 0.0007, 9.0, 0.95) * 0.6;
    col += glow * luma(plankton) * 0.55 * uStars;

    col *= mix(0.35, 1.0, smoothstep(-0.7, 0.2, y));
    return col;
}
