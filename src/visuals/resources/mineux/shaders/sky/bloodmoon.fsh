
const vec3 BM_DIR = vec3(0.3483, 0.4180, -0.8391);

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;
    vec3 col = mix(uColor2 * 0.55, vec3(0.006, 0.0015, 0.002), smoothstep(-0.05, 0.6, y));
    col += starField(cdir, 0.6, 0.3) * uStars * 0.35 * vec3(1.0, 0.6, 0.55) * smoothstep(-0.05, 0.3, y);

    float R = 0.16 * uScale;
    float c = dot(dir, BM_DIR);
    float ang = acos(clamp(c, -1.0, 1.0));

    float h = max(ang - R, 0.0);
    col += uColor1 * (0.30 * exp(-h * 16.0) + 0.07 * exp(-h * 3.5)) * uIntensity;

    if (c > 0.0 && ang < R * 1.2) {
        vec3 right = normalize(cross(BM_DIR, vec3(0.0, 1.0, 0.0)));
        vec3 up    = cross(right, BM_DIR);
        vec2 uv = vec2(dot(dir, right), dot(dir, up)) / c / R;
        float r2 = dot(uv, uv);
        float aa = uPixel / R * 1.5;
        float disc = smoothstep(1.0 + aa, 1.0 - aa, sqrt(r2));
        vec3 nrm = vec3(uv, sqrt(max(1.0 - r2, 0.0)));
        float maria = smoothstep(0.45, 0.7, fbm3_3(nrm * 1.6 + 4.0));
        float crater = fbm3_5(nrm * 4.5);
        float surf = (0.75 - 0.35 * maria) * (0.7 + 0.5 * crater);
        float limb = pow(nrm.z, 0.45);
        float umbra = 0.55 + 0.45 * smoothstep(-1.0, 0.9, uv.x * 0.6 + uv.y * 0.8);
        vec3 moon = uColor1 * surf * limb * umbra * 2.1 + uColor1 * vec3(1.0, 0.6, 0.3) * 0.25 * pow(1.0 - nrm.z, 3.0);
        col = mix(col, moon, disc);
    }

    if (y > -0.02) {
        vec2 p = dir.xz / (y + 0.10) * 0.5 + vec2(t * 0.025, t * 0.01);
        float m = fbm2_5(p * 1.4 + vnoise2(p * 0.4 + t * 0.02) * 1.5);
        float mist = smoothstep(0.38, 0.78, m) * smoothstep(-0.02, 0.15, y);
        float lit = exp(-max(ang - R * 0.6, 0.0) * 7.0);
        vec3 mc = uColor2 * 0.22 + uColor1 * lit * 0.7 * uIntensity;
        col = mix(col, mc, mist * 0.8);
    }
    return col;
}
