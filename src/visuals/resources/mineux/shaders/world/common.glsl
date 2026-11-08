// World Shaders: shared helpers. Prepended to world_lo.fsh and world.fsh by WorldShaderRenderer.
uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform mat4  uInvViewProj;   // camera-relative world <- clip
uniform mat4  uViewProj;      // clip <- camera-relative world
uniform vec3  uCamPos;        // camera world position (wrapped for precision)
uniform vec3  uLightDir;
uniform vec3  uLightCol;
uniform vec2  uTexel;         // 1 / full resolution
uniform float uTime;
uniform float uDay;

float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float hash13(vec3 p3) { p3 = fract(p3 * 0.1031); p3 += dot(p3, p3.zyx + 31.32); return fract((p3.x + p3.y) * p3.z); }
float vnoise3(vec3 x) {
    vec3 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i), b = hash13(i + vec3(1, 0, 0)), c = hash13(i + vec3(0, 1, 0)), d = hash13(i + vec3(1, 1, 0));
    float e = hash13(i + vec3(0, 0, 1)), g = hash13(i + vec3(1, 0, 1)), h = hash13(i + vec3(0, 1, 1)), k = hash13(i + vec3(1, 1, 1));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}
float vnoise2(vec2 x) {
    vec2 i = floor(x), f = fract(x); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

// 26.2 renders reversed-Z with a [0, 1] clip range: the depth sample is NDC z as-is, near = 1, sky/far = 0.
const float SKY_DEPTH = 0.00001;
const float FAR_DEPTH = 0.0001;

vec3 posAt(vec2 p, float d) {
    vec4 w = uInvViewProj * vec4(p * 2.0 - 1.0, d, 1.0);
    return w.xyz / w.w;
}

/** Surface normal from the depth buffer around uv (picks the neighbour on the same surface). */
vec3 normalAt(vec2 uv, vec3 P, vec3 rd, vec2 texel) {
    vec3 pr = posAt(uv + vec2(texel.x, 0.0), texture(uDepth, uv + vec2(texel.x, 0.0)).r);
    vec3 pl = posAt(uv - vec2(texel.x, 0.0), texture(uDepth, uv - vec2(texel.x, 0.0)).r);
    vec3 pu = posAt(uv + vec2(0.0, texel.y), texture(uDepth, uv + vec2(0.0, texel.y)).r);
    vec3 pd = posAt(uv - vec2(0.0, texel.y), texture(uDepth, uv - vec2(0.0, texel.y)).r);
    vec3 dx = length(pr - P) < length(P - pl) ? pr - P : P - pl;
    vec3 dy = length(pu - P) < length(P - pd) ? pu - P : P - pd;
    vec3 n = normalize(cross(dx, dy));
    if (dot(n, -rd) < 0.0) n = -n;
    return n;
}
