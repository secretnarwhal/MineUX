
const int FOG_STEPS = 9;

float flicker(float lt) {
    float f = exp(-lt * 14.0);
    f += 0.7 * exp(-max(lt - 0.12, 0.0) * 18.0) * step(0.12, lt);
    f += 0.5 * exp(-max(lt - 0.29, 0.0) * 12.0) * step(0.29, lt);
    return f;
}

float jag(float x, float seed) {
    float i = floor(x), f = fract(x);
    return mix(hash11(i + seed), hash11(i + 1.0 + seed), f) - 0.5;
}

float rainLayer(float az, float el, float cols, float rows, float speed, float slant, float len, float seed, float t) {
    float u  = az * cols + el * slant;
    float ci = floor(u);
    float sp = speed * (0.8 + 0.4 * hash11(ci * 3.7 + seed));
    float v  = el * rows + t * sp + hash11(ci * 1.7 + seed) * 31.0;
    float ri = floor(v);
    vec2  f  = vec2(fract(u), fract(v));
    vec3  h  = hash33(vec3(ci, ri, seed));
    if (h.x > 0.30 * clamp(uIntensity, 0.2, 2.0)) return 0.0;
    float x = 0.15 + 0.7 * h.y;
    float w = max(fwidth(u) * 0.9, 0.035);
    float across = 1.0 - smoothstep(0.0, w, abs(f.x - x));
    float l  = len * (0.5 + 0.5 * h.z);
    float y0 = hash11(h.y * 71.0 + ri) * (1.0 - l);
    float along = smoothstep(y0, y0 + 0.04, f.y) * (1.0 - smoothstep(y0 + l * 0.45, y0 + l, f.y));
    return across * along * (0.35 + 0.65 * h.z);
}

float fogDensity(vec3 p, float t, bool far) {
    vec3 wind = vec3(t * 0.11, -t * 0.015, t * 0.045);
    vec3 q = p * (0.6 / uScale) + wind;
    vec3 warp = vec3(vnoise3(q * 0.5 + 3.0), 0.0, vnoise3(q * 0.5 + 11.0)) * 1.3;
    float banks = far ? fbm3_3(q + warp) : fbm3_5(q + warp);
    float scud = vnoise3(q * 3.1 - wind * 0.6);
    float d = smoothstep(0.22, 0.8, banks * 0.8 + scud * 0.35);
    float h = clamp(p.y, 0.0, 1.0);
    return mix(0.25, 1.35, d) * (0.55 + 0.45 * exp(-h * 1.2));
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t  = uTime;
    float y  = dir.y;
    float el = asin(clamp(y, -1.0, 1.0));
    float az = atan(dir.z, dir.x) / TAU + 0.5;
    vec3 fogC = pow(clamp(uHorizon, 0.0, 1.0), vec3(2.2));
    vec3 bolt = uColor1;

    float sheet = 0.0;
    vec3  local = vec3(0.0);
    vec3  boltLight = vec3(0.0);
    float rate = clamp(uIntensity, 0.1, 2.0);
    for (int k = 0; k < 2; k++) {
        float fk = float(k);
        float period = (4.3 + fk * 3.1) / rate;
        float tt = t + fk * 9.3;
        float slot = floor(tt / period);
        float lt = tt - slot * period;
        vec3 h = hash33(vec3(slot, fk, 5.1));
        if (h.x > 0.75 || lt > 1.0) continue;
        float env = flicker(lt);
        float faz = (h.y * 2.0 - 1.0) * PI;
        float fel = 0.05 + h.z * 0.30;
        vec3 fdir = vec3(cos(faz) * cos(fel), sin(fel), sin(faz) * cos(fel));
        float c = dot(dir, fdir);
        sheet += env * (0.35 + 0.65 * h.z);
        local += bolt * env * (exp((c - 1.0) * 7.0) * 1.3 + exp((c - 1.0) * 40.0) * 1.6);

        if (h.y > 0.45) {
            float da = mod(atan(dir.z, dir.x) - faz + PI, TAU) - PI;
            float x = da * cos(el);
            float top = fel * 0.9;
            if (el < top && el > -0.01) {
                float s = (top - el) / max(top, 0.04);
                float off = (jag(el * 34.0, slot) * 0.035 + jag(el * 140.0, slot + 3.0) * 0.008) * smoothstep(0.0, 0.2, s);
                float wd = max(0.002, uPixel);
                float bx = x - off;
                float core = exp(-bx * bx / (wd * wd));
                float halo = exp(-abs(bx) * 55.0);
                boltLight += bolt * (core * 2.2 + halo * 0.5) * min(env * 1.6, 1.0) * step(lt, 0.5);
            }
        }
    }

    vec3 deck;
    {
        vec2 p = dir.xz / (max(y, 0.0) + 0.09) * (0.8 / uScale) + vec2(t * 0.05, t * 0.018);
        float n = fbm2_5(p + vnoise2(p * 0.4 - t * 0.02) * 2.0);
        float lumps = smoothstep(0.3, 0.9, n);
        float gaps = smoothstep(0.55, 0.85, vnoise2hq(p * 0.55 + vec2(-t * 0.03, 7.0)));
        deck = fogC * (0.14 + 0.75 * lumps * lumps + 0.35 * gaps);
        deck += local * 0.25 * (0.5 + lumps) + bolt * sheet * 0.05 * (0.4 + lumps);
    }

    if (y < 0.0) {
        vec3 low = fogC * mix(0.95, 0.55, smoothstep(0.0, -0.5, y));
        return low * (1.0 + sheet * 0.5) + boltLight * 0.12;
    }

    float L = min(1.0 / max(y, 0.015), 34.0);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float tau = 0.0;
    vec3  scat = vec3(0.0);
    float thick = 1.35;
    for (int i = 0; i < FOG_STEPS; i++) {
        float a0 = (float(i) + jitter) / float(FOG_STEPS);
        float a1 = (float(i) + 1.0 + jitter) / float(FOG_STEPS);
        float s0 = L * a0 * a0, ds = L * (a1 * a1 - a0 * a0);
        vec3  p  = vec3(0.0, 0.05, 0.0) + dir * s0;
        float d  = fogDensity(p, t, i >= 4) * thick;
        float T  = exp(-tau);
        if (T < 0.003) break;
        float top = clamp(p.y, 0.0, 1.0);
        float shade = mix(1.15, 0.45, smoothstep(0.4, 1.4, d / thick));
        vec3 light = fogC * (0.45 + 0.6 * top) * shade
                   + bolt * sheet * 0.22 + local * 0.35;
        scat += T * (1.0 - exp(-d * ds)) * light;
        tau  += d * ds;
    }
    float T = exp(-tau);
    vec3 col = scat + (deck + boltLight * 0.5) * T + boltLight * 0.35 * (1.0 - T);

    {
        vec2 sp = dir.xz / (y + 0.06) * (1.25 / uScale) + vec2(t * 0.16, t * 0.06);
        float n  = fbm2_5(sp + vnoise2(sp * 0.7 + t * 0.05) * 1.6);
        float shred = smoothstep(0.52, 0.78, n);
        float edge  = smoothstep(0.45, 0.6, n) - shred;
        vec3 scud = fogC * (0.28 + 0.9 * edge) + local * 0.3 + bolt * sheet * 0.08;
        float vis = smoothstep(0.02, 0.18, y) * (1.0 - 0.4 * smoothstep(0.5, 1.0, y));
        col = mix(col, scud, shred * vis * 0.75);
    }

    col += fogC * 0.45 * exp(-abs(y - 0.03) * 28.0) * (1.0 + sheet);

    if (y < 0.6) {
    float curtain = vnoise2hq(vec2(az * 48.0 + t * 0.25, 0.0)) * vnoise2hq(vec2(az * 17.0 - t * 0.12, 4.0));
    float streaky = 0.7 + 0.3 * vnoise2hq(vec2(az * 900.0, el * 4.0 + t * 1.5));
    col = mix(col, fogC * 0.62 * streaky, smoothstep(0.15, 0.5, curtain) * exp(-y * 9.0) * 0.55);
    }

    float rainVis = smoothstep(0.55, 0.05, y) * smoothstep(-0.02, 0.04, y);
    if (rainVis > 0.0) {
    float r = rainLayer(az, el, 700.0, 7.0, 6.0, 26.0, 0.9, 1.0, t) * 1.0
            + rainLayer(az, el, 1300.0, 12.0, 8.5, 40.0, 0.7, 7.0, t) * 0.6
            + rainLayer(az, el, 2400.0, 22.0, 11.0, 60.0, 0.55, 13.0, t) * 0.35;
    col += (fogC * 0.55 + vec3(0.02, 0.022, 0.026) + bolt * sheet * 0.35) * r * rainVis * clamp(uIntensity, 0.0, 2.0);
    }

    col *= 1.0 + sheet * 0.6;
    return mix(fogC, col, smoothstep(0.0, 0.05, y));
}
