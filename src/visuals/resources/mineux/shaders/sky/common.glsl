
in vec4 vNear;
in vec4 vFar;
out vec4 fragColor;

uniform sampler2D uNoise;
uniform float uTime;
uniform vec3  uColor1;
uniform vec3  uColor2;
uniform float uIntensity;
uniform float uScale;
uniform float uStars;
uniform float uBrightness;
uniform vec3  uHorizon;
uniform float uHorizonFog;
uniform float uDirSign;
uniform mat3  uCelestial;
uniform float uPixel;

const float PI  = 3.14159265;
const float TAU = 6.28318531;

float hash11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float hash12(vec2 p)  { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
vec2  hash22(vec2 p)  { vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973)); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.xx + p3.yz) * p3.zy); }
vec3  hash33(vec3 p3) { p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973)); p3 += dot(p3, p3.yxz + 33.33); return fract((p3.xxy + p3.yxx) * p3.zyx); }

float vnoise2(vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return textureLod(uNoise, (p + f + 0.5) * (1.0 / 256.0), 0.0).r;
}

float vnoise2hq(vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    ivec2 i = ivec2(p) & 255;
    float a = texelFetch(uNoise, i, 0).r;
    float b = texelFetch(uNoise, (i + ivec2(1, 0)) & 255, 0).r;
    float c = texelFetch(uNoise, (i + ivec2(0, 1)) & 255, 0).r;
    float d = texelFetch(uNoise, (i + ivec2(1, 1)) & 255, 0).r;
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float vnoise3(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xy + vec2(37.0, 17.0) * p.z + f.xy;
    vec2 rg = textureLod(uNoise, (uv + 0.5) * (1.0 / 256.0), 0.0).yx;
    return mix(rg.x, rg.y, f.z);
}

const mat2 ROT2 = mat2(0.80, 0.60, -0.60, 0.80);
const mat3 ROT3 = mat3( 0.00,  0.80,  0.60,
                       -0.80,  0.36, -0.48,
                       -0.60, -0.48,  0.64);

float fbm2_4(vec2 p) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 4; i++) { s += a * vnoise2(p); p = ROT2 * p * 2.03; a *= 0.5; }
    return s * (1.0 / 0.9375);
}

float fbm2_5(vec2 p) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 5; i++) { s += a * vnoise2(p); p = ROT2 * p * 2.01; a *= 0.5; }
    return s * (1.0 / 0.96875);
}

float fbm3_3(vec3 p) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 3; i++) { s += a * vnoise3(p); p = ROT3 * p * 2.02; a *= 0.5; }
    return s * (1.0 / 0.875);
}

float fbm3_5(vec3 p) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 5; i++) { s += a * vnoise3(p); p = ROT3 * p * 2.02; a *= 0.5; }
    return s * (1.0 / 0.96875);
}

vec3 starTint(float t) {
    return mix(mix(vec3(0.62, 0.74, 1.00), vec3(1.0), smoothstep(0.0, 0.45, t)),
               vec3(1.00, 0.72, 0.45), smoothstep(0.65, 1.0, t));
}

vec3 starLayer(vec3 d, float cells, float prob, float size, float seed, float twinkle) {
    vec3 a = abs(d);
    vec2 uv; float face; float s;
    if (a.x > a.y && a.x > a.z) { uv = d.yz / a.x; s = sign(d.x); face = 0.0 + step(0.0, d.x); }
    else if (a.y > a.z)         { uv = d.xz / a.y; s = sign(d.y); face = 2.0 + step(0.0, d.y); }
    else                        { uv = d.xy / a.z; s = sign(d.z); face = 4.0 + step(0.0, d.z); }

    vec2 g  = (uv * 0.5 + 0.5) * cells;
    vec2 id = floor(g);
    vec3 h  = hash33(vec3(id, face * 31.7 + seed));
    if (h.z > prob) return vec3(0.0);

    vec2 sp = (id + 0.3 + 0.4 * h.xy) / cells * 2.0 - 1.0;
    vec3 sd;
    if (face < 1.5)      sd = vec3(s, sp.x, sp.y);
    else if (face < 3.5) sd = vec3(sp.x, s, sp.y);
    else                 sd = vec3(sp.x, sp.y, s);
    sd = normalize(sd);

    vec3  r   = d - sd;
    float d2  = dot(r, r);
    float sigMax = min(max(size * 2.0, uPixel * 0.75), (2.0 / cells) * 0.12);
    if (d2 > 25.0 * sigMax * sigMax) return vec3(0.0);
    float h2  = hash12(id * 1.37 + seed + face);
    float mag = pow(h2, 6.0);
    float sig = max(size * (0.6 + 1.4 * mag), uPixel * 0.75);
    float cellA = 2.0 / cells;
    sig = min(sig, cellA * 0.12);
    float k   = (size * size) / (sig * sig);
    float I   = (0.12 + 3.0 * mag) * k * exp(-d2 / (2.0 * sig * sig));
    float tw  = 1.0 - twinkle * (0.5 + 0.5 * sin(uTime * (2.0 + 5.0 * h.y) + h.x * TAU));
    return starTint(hash11(h2 * 91.3)) * I * tw;
}

vec3 starLayerBloom(vec3 d, float cells, float prob, float size, float bloomSize, float bloomK, float seed, float twinkle) {
    vec3 a = abs(d);
    vec2 uv; float face; float s;
    if (a.x > a.y && a.x > a.z) { uv = d.yz / a.x; s = sign(d.x); face = 0.0 + step(0.0, d.x); }
    else if (a.y > a.z)         { uv = d.xz / a.y; s = sign(d.y); face = 2.0 + step(0.0, d.y); }
    else                        { uv = d.xy / a.z; s = sign(d.z); face = 4.0 + step(0.0, d.z); }
    vec2 g  = (uv * 0.5 + 0.5) * cells;
    vec2 id = floor(g);
    vec3 h  = hash33(vec3(id, face * 31.7 + seed));
    if (h.z > prob) return vec3(0.0);
    vec2 sp = (id + 0.3 + 0.4 * h.xy) / cells * 2.0 - 1.0;
    vec3 sd;
    if (face < 1.5)      sd = vec3(s, sp.x, sp.y);
    else if (face < 3.5) sd = vec3(sp.x, s, sp.y);
    else                 sd = vec3(sp.x, sp.y, s);
    sd = normalize(sd);
    vec3  r   = d - sd;
    float d2  = dot(r, r);
    float cellA = 2.0 / cells;
    float bMax = min(max(bloomSize * 2.0, uPixel * 0.75), cellA * 0.12);
    if (d2 > 25.0 * bMax * bMax) return vec3(0.0);
    float h2  = hash12(id * 1.37 + seed + face);
    float mag = pow(h2, 6.0);
    float tw  = 1.0 - twinkle * (0.5 + 0.5 * sin(uTime * (2.0 + 5.0 * h.y) + h.x * TAU));
    vec3  tint = starTint(hash11(h2 * 91.3));
    float I0  = (0.12 + 3.0 * mag);
    float sig = min(max(size * (0.6 + 1.4 * mag), uPixel * 0.75), cellA * 0.12);
    float sgb = min(max(bloomSize * (0.6 + 1.4 * mag), uPixel * 0.75), cellA * 0.12);
    float core  = (size * size) / (sig * sig) * exp(-d2 / (2.0 * sig * sig));
    float bloom = (bloomSize * bloomSize) / (sgb * sgb) * exp(-d2 / (2.0 * sgb * sgb));
    return tint * I0 * tw * (core + bloom * bloomK);
}

vec3 starField(vec3 d, float density, float twinkle) {
    vec3 c = starLayer(d, 300.0, 0.16 * density, 0.00045, 1.0, twinkle) * 0.5;
    c     += starLayer(d, 120.0, 0.16 * density, 0.00070, 7.0, twinkle);
    c     += starLayer(d,  44.0, 0.20 * density, 0.00110, 13.0, twinkle * 0.6) * 1.6;
    return c;
}

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 saturateColor(vec3 c, float s) { return mix(vec3(luma(c)), c, s); }

vec3 tonemap(vec3 c) {
    vec3 x = max(c - 0.6, 0.0);
    return min(c, vec3(0.6)) + x / (1.0 + x * 2.5);
}

vec3 skyColor(vec3 dir, vec3 cdir);

void main() {
    vec3 ray = (vFar.xyz * vNear.w - vNear.xyz * vFar.w) * uDirSign;
    vec3 dir = normalize(ray);
    vec3 cdir = uCelestial * dir;

    vec3 c = skyColor(dir, cdir) * uBrightness;
    c = tonemap(max(c, 0.0));
    c = pow(c, vec3(1.0 / 2.2));

    float haze = uHorizonFog * exp(-abs(dir.y) * (dir.y > 0.0 ? 11.0 : 7.0));
    c = mix(c, uHorizon, haze);

    vec2 fc = gl_FragCoord.xy;
    c += (hash12(fc) + hash12(fc + 71.3) - 1.0) * (1.0 / 255.0);

    fragColor = vec4(c, 1.0);
}
