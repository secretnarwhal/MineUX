
float jag(float x, float seed) {
    float i = floor(x), f = fract(x);
    return mix(hash11(i + seed), hash11(i + 1.0 + seed), f) - 0.5;
}

float flicker(float lt) {
    float f = exp(-lt * 16.0);
    f += 0.75 * exp(-max(lt - 0.13, 0.0) * 20.0) * step(0.13, lt);
    f += 0.55 * exp(-max(lt - 0.31, 0.0) * 13.0) * step(0.31, lt);
    return f;
}

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t  = uTime;
    float el = asin(clamp(dir.y, -1.0, 1.0));
    float az = atan(dir.z, dir.x);

    float denom = max(dir.y, 0.0) + 0.07;
    vec2  p  = dir.xz / denom * (1.1 / uScale) + vec2(t * 0.020, t * 0.008);
    vec2  w  = vec2(vnoise2(p * 0.35 + t * 0.01), vnoise2(p * 0.35 + 17.0)) * 2.2;
    float d  = fbm2_5(p * 1.1 + w);
    float d2 = vnoise2(p * 2.6 + w * 1.3 - t * 0.02);
    float dens = smoothstep(0.25, 0.85, d);

    vec3 tint  = uColor2;
    vec3 base  = tint * mix(0.035, 0.16, smoothstep(0.8, 0.0, dir.y));
    float shape = dens * dens * 0.9 + d2 * 0.35 - 0.1;
    vec3 cloud = base * (0.25 + 1.8 * max(shape, 0.0));

    vec3 light = vec3(0.0);
    float ambient = 0.0;
    float rate = max(uIntensity, 0.05);
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float period = (3.1 + fk * 2.3) / rate;
        float tt = t + fk * 7.7;
        float slot = floor(tt / period);
        float lt = tt - slot * period;
        vec3  h  = hash33(vec3(slot, fk, 3.7));
        if (h.x > 0.72 || lt > 0.9) continue;
        float env = flicker(lt);

        float faz = (h.y * 2.0 - 1.0) * PI;
        float fel = 0.10 + h.z * 0.45;
        vec3  fdir = vec3(cos(faz) * cos(fel), sin(fel), sin(faz) * cos(fel));
        vec2  fp = fdir.xz / (fdir.y + 0.07) * (0.55 / uScale);
        vec2  pp = dir.xz / denom * (0.55 / uScale);
        float dist = length(pp - fp) / (1.0 + length(fp) * 0.35);

        float glow = exp(-dist * 1.6) * (0.35 + 1.4 * dens) + exp(-dist * 6.0) * 1.5;
        light   += uColor1 * glow * env * 0.9;
        ambient += env * 0.07;

        float hb = hash11(slot * 3.1 + fk);
        if (hb > 0.3) {
            float da = az - faz;
            da = mod(da + PI, TAU) - PI;
            float x  = da * cos(el);
            float top = fel * 0.92;
            if (el < top && el > -0.02) {
                float s  = (top - el) / max(top, 0.05);
                float off = jag(el * 30.0, slot) * 0.040 + jag(el * 120.0, slot + 5.0) * 0.010;
                off *= smoothstep(0.0, 0.15, s);
                float wdt = max(0.0016, uPixel * 0.9);
                float bx = x - off;
                float bolt = exp(-bx * bx / (wdt * wdt)) + exp(-abs(bx) * 90.0) * 0.18;
                float bs = 0.25 + hb * 0.3;
                if (s > bs && s < bs + 0.35) {
                    float u  = (s - bs) / 0.35;
                    float bo = off + (hb - 0.7) * 0.25 * u + jag(el * 90.0, slot + 9.0) * 0.008;
                    float bx2 = x - bo;
                    bolt += (exp(-bx2 * bx2 / (wdt * wdt * 0.5)) + exp(-abs(bx2) * 120.0) * 0.1) * (1.0 - u) * 0.6;
                }
                light += uColor1 * bolt * 5.0 * min(env * 1.5, 1.0) * step(lt, 0.55);
            }
        }
    }

    vec3 col = cloud * (1.0 + ambient * 3.0) + light;

    float rain = vnoise2(vec2(az * 260.0 + el * 30.0, el * 6.0 + t * 9.0));
    col += tint * 0.05 * smoothstep(0.62, 1.0, rain) * smoothstep(0.6, 0.0, dir.y) * smoothstep(-0.01, 0.08, dir.y);

    col = mix(col, base * 0.3 + light * 0.1, smoothstep(0.0, -0.12, dir.y));
    return col;
}
