
const vec3 SW_SUN = vec3(-0.9877, 0.1564, 0.0);

vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    float y = dir.y;

    vec3 zen = vec3(0.006, 0.002, 0.030);
    vec3 mid = vec3(0.055, 0.010, 0.140);
    vec3 hot = uColor1;
    vec3 col = mix(mid, zen, smoothstep(0.15, 0.75, y));
    col = mix(hot * 0.55 + vec3(0.12, 0.02, 0.0), col, smoothstep(-0.02, 0.22, y));

    vec3 right = normalize(cross(SW_SUN, vec3(0.0, 1.0, 0.0)));
    vec3 up    = cross(right, SW_SUN);
    float facing = dot(dir, SW_SUN);
    float R = 0.20 * uScale;
    if (facing > 0.0) {
        vec2 uv = vec2(dot(dir, right), dot(dir, up)) / facing / R;
        float r = length(uv);
        float aa = uPixel / R * 1.5;
        float disc = smoothstep(1.0 + aa, 1.0 - aa, r);
        float v = uv.y;
        float stripe = 1.0;
        if (v < 0.25) {
            float k = (0.25 - v) / 1.25;
            float s = fract(v * 5.0 + t * 0.25);
            float gap = mix(0.06, 0.55, k);
            float aa2 = uPixel / R * 5.0;
            stripe = smoothstep(gap - aa2, gap + aa2, s);
        }
        vec3 sunCol = mix(hot * 1.4, vec3(1.0, 0.85, 0.25) * 1.6, smoothstep(-0.9, 0.8, v));
        col = mix(col, sunCol, disc * stripe);
        float gl = exp(-max(r - 1.0, 0.0) * 2.4);
        col += hot * 0.35 * gl * (1.0 - disc * stripe) * uIntensity;
        col += vec3(1.0, 0.5, 0.2) * 0.12 * exp(-max(r - 1.0, 0.0) * 0.8) * uIntensity;
    }

    col += starField(cdir, 0.8, 0.5) * uStars * 0.5 * smoothstep(0.15, 0.5, y);

    col += hot * 0.9 * exp(-abs(y) * 140.0) * uIntensity;

    vec2 gp = dir.xz / max(abs(y), 0.002) * 0.6;
    gp.y += t * 1.2;
    vec2 fw = fwidth(gp);
    if (y < 0.0) {
        vec2 g  = abs(fract(gp) - 0.5);
        vec2 ln = 1.0 - smoothstep(vec2(0.0), fw * 1.5, 0.5 - g);
        float lines = max(ln.x, ln.y) * (1.0 - smoothstep(0.25, 0.9, max(fw.x, fw.y)));
        vec3 ground = vec3(0.02, 0.0, 0.05) * smoothstep(0.0, -0.3, y);
        ground += uColor2 * lines * 1.2;
        ground += hot * 0.25 * exp(y * 30.0);
        col = ground;
        col += hot * 0.9 * exp(-abs(y) * 140.0) * uIntensity;
    }
    return col;
}
