#version 330
// Armour shaders (self/ArmorShade): one material per pipeline, picked by MATERIAL --
// 0 glass, 1 crystal, 2 hologram, 3 molten, 4 void, 5 chrome, 6 neon, 7 frost.
#moj_import <minecraft:globals.glsl>
#define CL_TIME (GameTime * 1200.0)

uniform sampler2D Sampler0;

in vec4 vColor;
in vec2 vUV;
in vec3 vNormal;
in vec3 vView;
in vec4 vLight;

out vec4 fragColor;

float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
vec2 hash22(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973)); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.xx + p3.yz) * p3.zy); }
float vnoise(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y); }
float fbm(vec2 p) { return vnoise(p) * 0.5 + vnoise(p * 2.03 + 7.1) * 0.3 + vnoise(p * 4.07 + 3.3) * 0.2; }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 hue(float h) { return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0); }

/** Strong lines in the armour's own drawing (plate seams, rivets), not every texel edge. */
float seams(vec2 uv) {
    vec2 ts = 1.0 / vec2(textureSize(Sampler0, 0));
    float l = luma(texture(Sampler0, uv - vec2(ts.x, 0.0)).rgb), r = luma(texture(Sampler0, uv + vec2(ts.x, 0.0)).rgb);
    float d = luma(texture(Sampler0, uv - vec2(0.0, ts.y)).rgb), u = luma(texture(Sampler0, uv + vec2(0.0, ts.y)).rgb);
    return smoothstep(0.18, 0.4, abs(r - l) + abs(u - d));
}

void main() {
    vec4 tex = texture(Sampler0, vUV);
    if (tex.a < 0.1) discard;
    vec3 c1 = vColor.rgb;                        // the material colour (from the armour tint)
    float opacity = vColor.a;
    float T = CL_TIME;
    vec3 N = normalize(vNormal);
    vec3 V = normalize(-vView);
    if (dot(N, V) < 0.0) N = -N;
    float ndv = clamp(dot(N, V), 0.0, 1.0);
    float fres = pow(1.0 - ndv, 3.0);
    vec3 L = normalize(vec3(-0.35, 0.8, 0.45));
    float diff = 0.5 + 0.5 * clamp(dot(N, L), 0.0, 1.0);
    vec3 R = reflect(-V, N);
    float spec = pow(clamp(dot(R, L), 0.0, 1.0), 40.0);
    float lum = luma(tex.rgb);
    vec2 px = vUV * vec2(textureSize(Sampler0, 0));     // texture pixels: a stable coordinate on the armour
    vec3 light = max(vLight.rgb, vec3(0.1));
    vec3 col; float a;
#if MATERIAL == 0
    float sweep = smoothstep(0.08, 0.0, abs(fract((gl_FragCoord.x + gl_FragCoord.y) / 520.0 - T * 0.18) - 0.5) - 0.42);
    vec3 tint = mix(vec3(1.0), c1, 0.55);
    col = (tint * 0.08 + tint * fres * 1.2 + vec3(1.0) * spec * 1.4 + vec3(1.0) * sweep * 0.35 + tint * seams(vUV) * 0.25) * light;
    a = 0.1 + fres * 0.6 + spec * 0.6 + sweep * 0.25;
#elif MATERIAL == 1
    vec2 p = px / 5.0;
    vec2 cell = floor(p), f = fract(p);
    float d1 = 9.0, d2 = 9.0; vec2 id = vec2(0.0);
    for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
        vec2 o = vec2(i, j), h = hash22(cell + o);
        float d = length(o + h - f);
        if (d < d1) { d2 = d1; d1 = d; id = cell + o; } else if (d < d2) d2 = d;
    }
    float facet = hash12(id);
    float edge = smoothstep(0.06, 0.0, d2 - d1);
    float flash = pow(clamp(dot(R, normalize(vec3(facet - 0.5, 1.0, hash12(id + 3.0) - 0.5))), 0.0, 1.0), 24.0);
    float sparkle = step(0.97, hash12(id + floor(T * 2.0))) * smoothstep(0.25, 0.0, d1);
    vec3 base = c1 * (0.25 + 0.75 * facet) * diff;
    col = (base + hue(facet * 0.25 + fres + T * 0.03) * fres * 0.8 + vec3(1.0) * (edge * 0.6 + flash * 0.9 + sparkle * 1.5)) * light;
    a = 0.82 + fres * 0.18;
#elif MATERIAL == 2
    float lines = 0.6 + 0.4 * step(0.5, fract(gl_FragCoord.y / 3.0));
    float band = smoothstep(0.06, 0.0, abs(fract(gl_FragCoord.y / 300.0 - T * 0.35) - 0.5) - 0.44);
    float glitch = step(0.93, hash12(vec2(floor(T * 12.0), floor(gl_FragCoord.y / 18.0))));
    float column = step(0.8, hash12(vec2(floor(px.x / 2.0), 7.0))) * step(0.55, fract(px.y / 6.0 - T * 1.5 + hash12(vec2(floor(px.x / 2.0), 3.0))));
    vec3 c = mix(c1, vec3(1.0), 0.15);
    col = c * (0.18 * lum + 0.1) * lines + c * fres * 1.3 + c * band * 0.7 + c * column * 0.7 + vec3(1.0, 0.2, 0.6) * glitch * 0.4;
    a = (0.08 + 0.12 * lum) * lines + fres * 0.55 + band * 0.35 + column * 0.4 + glitch * 0.25;
#elif MATERIAL == 3
    vec2 q = px / 9.0;
    float n = fbm(q + vec2(0.0, -T * 0.12)) + 0.35 * fbm(q * 2.3 + vec2(T * 0.08, 0.0));
    float crack = 1.0 - smoothstep(0.015, 0.07, abs(n - 0.62));
    float glow = crack * (0.75 + 0.35 * sin(T * 2.2 + n * 14.0));
    vec3 crust = mix(vec3(0.06, 0.045, 0.04), tex.rgb * 0.3, 0.35) * diff * light;
    vec3 lava = mix(c1, vec3(1.0, 0.92, 0.6), glow * 0.6);
    col = crust + lava * glow * 1.7 + c1 * fres * 0.2;
    a = 1.0;
#elif MATERIAL == 4
    vec2 sp = gl_FragCoord.xy;
    float stars = step(0.996, hash12(floor(sp / 2.0))) * (0.55 + 0.45 * sin(T * 3.0 + hash12(floor(sp / 2.0)) * 40.0));
    float big = step(0.9993, hash12(floor(sp / 4.0) + 11.0));
    float neb = fbm(sp / 160.0 + vec2(T * 0.02, 0.0));
    vec3 space = mix(vec3(0.004, 0.0, 0.012), c1 * 0.45, smoothstep(0.45, 0.9, neb)) + mix(c1, vec3(0.3, 0.6, 1.0), 0.5) * smoothstep(0.7, 1.0, neb) * 0.25;
    col = space + vec3(stars + big * 1.5) + c1 * pow(1.0 - ndv, 2.0) * 1.6;
    a = 1.0;
#elif MATERIAL == 5
    float up = R.y;
    vec3 skyC = mix(vec3(0.85, 0.9, 1.0), mix(c1, vec3(0.35, 0.55, 0.95), 0.5), smoothstep(0.0, 0.8, up));
    vec3 groundC = mix(vec3(0.22, 0.2, 0.18), vec3(0.06, 0.06, 0.07), smoothstep(0.0, -0.6, up));
    vec3 env = up > 0.0 ? skyC : groundC;
    env += vec3(1.0) * smoothstep(0.02, 0.0, abs(up)) * 0.6;          // the horizon line
    env += vec3(1.0, 0.95, 0.85) * pow(clamp(dot(R, L), 0.0, 1.0), 120.0) * 3.0;
    col = env * (0.85 + 0.15 * lum) * mix(vec3(1.0), light, 0.5) - seams(vUV) * 0.18;
    a = 1.0;
#elif MATERIAL == 6
    vec2 g = px / 4.0;
    vec2 cell = floor(g), f = fract(g);
    float h = hash12(cell);
    float trace = h < 0.5 ? smoothstep(0.1, 0.0, abs(f.y - 0.5)) : smoothstep(0.1, 0.0, abs(f.x - 0.5));
    trace *= step(0.35, hash12(cell + 9.0));
    float pulse = smoothstep(0.85, 1.0, fract((h < 0.5 ? f.x : f.y) * 0.5 + h * 3.0 - T * 0.8));
    float s = seams(vUV);
    vec3 body = tex.rgb * 0.05 * light;
    col = body + c1 * (s * 1.4 + trace * 0.55 + trace * pulse * 1.4) + c1 * fres * 0.5;
    a = 1.0;
#elif MATERIAL == 7
    float frost = fbm(px / 3.5);
    float feather = smoothstep(0.52, 0.72, frost) + smoothstep(0.012, 0.0, abs(fbm(px / 7.0) - 0.5)) * 0.6;
    float glint = step(0.985, hash12(floor(px) + floor(T * 1.5))) * 1.3;
    col = (mix(c1 * 0.55, vec3(0.95, 0.98, 1.0), clamp(feather * 0.85 + lum * 0.15, 0.0, 1.0)) * diff + vec3(1.0) * (spec * 0.6 + glint) + c1 * fres * 0.5) * light;
    a = 0.72 + feather * 0.2 + fres * 0.15;
#endif
    fragColor = vec4(col, clamp(a * opacity, 0.0, 1.0));
}
