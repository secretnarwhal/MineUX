
// Quantum Tunnel: rings of light spiral up into a glowing vortex at the zenith.
vec3 skyColor(vec3 dir, vec3 cdir) {
    float t = uTime;
    vec3 col = vec3(0.003, 0.002, 0.009);
    float el = dir.y;
    float r = acos(clamp(el, -1.0, 1.0)) / 1.5708;      // 0 at zenith, 1 at horizon
    float a = atan(cdir.z, cdir.x);
    float depth = 1.0 / max(r, 0.02);
    float z = depth * 0.6 / uScale - t * 0.9;
    float spiral = a * 3.0 / 6.2832 + z;
    float ring = exp(-pow(fract(z) - 0.5, 2.0) * 60.0);
    float arms = pow(0.5 + 0.5 * sin(a * 6.0 + z * 4.0), 6.0);
    float swirl = exp(-pow(fract(spiral * 1.0) - 0.5, 2.0) * 40.0);
    float n = fbm2_4(vec2(a * 3.0, z * 0.8));
    vec3 c = mix(uColor1, uColor2, 0.5 + 0.5 * sin(z * 0.7 + a));
    float fadeH = smoothstep(-0.05, 0.35, el);
    float core = exp(-r * 9.0);
    vec3 tunnel = c * (ring * 1.2 + swirl * 0.9 + arms * 0.4) * (0.4 + 0.8 * n) * smoothstep(0.0, 0.25, r);
    tunnel += mix(uColor1, vec3(1.0), 0.6) * core * 2.5 + c * exp(-r * 3.0) * 0.4;
    col += tunnel * fadeH * 0.45 * uIntensity;
    col += starField(cdir, uStars, 0.6) * 0.7 * (1.0 - fadeH * 0.5);
    col += c * 0.04 * exp(-abs(el) * 10.0) * uIntensity;
    return col;
}
