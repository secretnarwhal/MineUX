
const int   BH_STEPS = 80;
const float R_IN  = 2.6;
const float R_OUT = 13.0;
const vec3  BH_DIR = vec3(0.0, 0.4472, -0.8944);

vec3 bhBackground(vec3 d) {
    vec3 c = uCelestial * d;
    vec3 col = vec3(0.0012, 0.0014, 0.0030);
    float n = fbm3_3(c * 2.2 + 5.0);
    col += uColor2 * 0.018 * smoothstep(0.45, 0.8, n);
    col += starField(c, 1.3, 0.25) * uStars * 0.8;
    return col;
}

vec3 diskShade(vec3 hit, vec3 n, vec3 e1, vec3 e2, vec3 rayDir, float t) {
    float r   = length(hit);
    float phi = atan(dot(hit, e2), dot(hit, e1));
    float omega = 2.2 * pow(r, -1.5);
    float pr  = phi - t * omega * 6.0;
    vec3  q   = vec3(r * 3.2, cos(pr) * 2.4, sin(pr) * 2.4);
    float tur = vnoise3(q) * 0.65 + vnoise3(q * 2.3 + 7.0) * 0.35;
    float rings = 0.55 + 0.45 * vnoise2hq(vec2(r * 7.0, 13.0));

    float x = R_IN / r;
    float I = pow(x, 1.7) * smoothstep(R_IN, R_IN + 0.7, r) * smoothstep(R_OUT, R_OUT - 4.0, r);
    I *= (0.35 + 1.1 * tur * tur) * rings;

    vec3  v = normalize(cross(n, hit)) * sqrt(0.5 / r) * 1.3;
    float D = 1.0 + dot(v, -rayDir);
    float beam = D * D * D;
    vec3 c = mix(uColor2, uColor1, smoothstep(0.25, 0.9, x));
    c = mix(c, vec3(1.0, 0.95, 0.9), smoothstep(1.15, 1.5, D) * 0.6);
    c = mix(c, uColor2 * vec3(1.0, 0.55, 0.4), smoothstep(0.9, 0.6, D) * 0.6);
    return c * I * beam * sqrt(max(1.0 - 1.0 / r, 0.0)) * 3.2;
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float D = 30.0 / uScale;
    float cosT = dot(dir, BH_DIR);
    float sinT = sqrt(max(1.0 - cosT * cosT, 1e-6));
    float b = D * sinT;

    if (cosT < 0.0 || b > R_OUT * 1.55) {
        float alpha = (1.0 + cosT) / max(b, 0.5);
        vec3  toward = normalize(BH_DIR - dir * cosT);
        vec3  bent = normalize(dir * cos(alpha) + toward * sin(alpha));
        return bhBackground(bent);
    }

    vec3 side = normalize(cross(BH_DIR, vec3(0.0, 1.0, 0.0)));
    vec3 upv  = cross(side, BH_DIR);
    vec3 n    = normalize(upv - BH_DIR * 0.20 + side * 0.12);
    vec3 e1   = normalize(cross(n, upv + side));
    vec3 e2   = cross(n, e1);

    vec3 pos = -BH_DIR * D;
    vec3 vel = dir;
    vec3 L   = cross(pos, vel);
    float h2 = dot(L, L);
    float t  = uTime;

    vec3  acc = vec3(0.0);
    float a   = 0.0;
    bool  captured = false;
    for (int i = 0; i < BH_STEPS; i++) {
        float r2 = dot(pos, pos);
        float r  = sqrt(r2);
        float dt = clamp(0.11 * r, 0.03, 3.0);
        vel += -1.5 * h2 * pos / (r2 * r2 * r) * dt;
        vec3 np = pos + vel * dt;

        float s0 = dot(pos, n), s1 = dot(np, n);
        if (s0 * s1 < 0.0) {
            vec3 hit = mix(pos, np, s0 / (s0 - s1));
            float hr = length(hit);
            if (hr > R_IN * 0.95 && hr < R_OUT) {
                vec3  dc = diskShade(hit, n, e1, e2, normalize(vel), t) * uIntensity;
                float da = clamp(luma(dc) * 1.6 + 0.15, 0.0, 0.92) * smoothstep(R_OUT, R_OUT - 3.0, hr);
                acc += (1.0 - a) * dc;
                a   += (1.0 - a) * da;
            }
        }
        pos = np;
        if (r < 1.0)  { captured = true; break; }
        if (r > D * 1.05 && dot(pos, vel) > 0.0) break;
        if (a > 0.985) break;
    }

    vec3 bg = captured ? vec3(0.0) : bhBackground(normalize(vel));
    vec3 col = acc + (1.0 - a) * bg;
    if (!captured) col += uColor2 * 0.03 * uIntensity * exp(-b * 0.22);
    return col;
}
