
vec3 lineLayer(vec2 uv, float layer, float t) {
    float ang  = 0.35 + layer * 1.9;
    mat2  R    = mat2(cos(ang), sin(ang), -sin(ang), cos(ang));
    vec2  q    = R * uv;

    float gap  = 0.55 * uScale * (1.0 + layer * 0.35);
    float px    = max(fwidth(q.y), 1e-5);
    float width = max(0.006 * (1.0 - layer * 0.25), px * 0.9);
    float layerDim = 1.0 - layer * 0.32;
    vec3 sum = vec3(0.0);

    float row0 = floor(q.y / gap);
    for (int k = -1; k <= 1; k++) {
        float row  = row0 + float(k);
        float seed = hash11(row * 7.31 + layer * 91.7);
        float seed2 = hash11(row * 3.17 + layer * 13.1 + 5.0);

        float wave = 0.16 * gap * sin(q.x * (0.9 + seed) + t * (0.12 + 0.1 * seed) + seed * TAU)
                   + 0.07 * gap * sin(q.x * 2.3 + t * 0.21 + seed2 * TAU);
        float cy   = (row + 0.5 + (seed - 0.5) * 0.5) * gap + wave;
        float d    = abs(q.y - cy);
        float core = exp(-(d * d) / (width * width));
        float glow = exp(-d / (width * 7.0)) * 0.3;

        float speed = (0.25 + 0.55 * seed2) * (1.0 - layer * 0.25) * (seed > 0.5 ? 1.0 : -1.0);
        float span  = 6.0 * uScale;
        float s     = q.x * sign(speed) - t * abs(speed) * 2.2 + seed * span;
        float ph    = fract(s / span) * span;
        float ahead = span - ph;
        float trail = exp(-ph / (1.6 * uScale));
        float head  = exp(-ph * ph / 0.0016) + exp(-ahead * ahead / 0.0004);
        float lit   = 0.07 + 1.9 * trail + 2.2 * head;

        float awake = smoothstep(0.25, 0.6, 0.5 + 0.5 * sin(t * (0.07 + 0.05 * seed) + seed2 * 40.0));
        lit *= mix(0.2, 1.0, awake);

        vec3 col = mix(uColor1, uColor2, smoothstep(0.2, 0.8, seed2));
        col = mix(col, vec3(1.0), 0.55 * clamp(head, 0.0, 1.0));
        sum += col * (core * 1.4 + glow * (0.35 + 0.65 * trail)) * lit;
    }
    return sum * layerDim;
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 tint = mix(uColor1, uColor2, 0.5);
    vec3 col = mix(tint * 0.012 + vec3(0.002, 0.002, 0.004), vec3(0.0012, 0.0012, 0.0022), smoothstep(-0.05, 0.75, y));

    if (y > -0.08) {
        float denom = max(y + 0.22, 0.05);
        vec2 base = dir.xz / denom;
        vec3 light = vec3(0.0);
        for (int L = 0; L < 3; L++) {
            float fl = float(L);
            vec2 uv = base * (1.0 + fl * 0.6) + vec2(fl * 17.3, fl * -9.1);
            light += lineLayer(uv, fl, t + fl * 13.0);
        }
        float fade = smoothstep(-0.06, 0.22, y);
        col += light * 0.32 * uIntensity * fade;
    }

    col += tint * 0.018 * exp(-abs(y - 0.04) * 10.0) * (0.75 + 0.25 * sin(t * 0.5)) * uIntensity;
    return col;
}
