#version 330 core

// Drifting pools of light in your two accent colours, washed over the hand.

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform float time;
uniform float aspect;
uniform vec3 uAccent;
uniform vec3 uAccent2;

float hand(vec2 at) {
    return 1.0 - texture(DepthTexture, at).r;
}

vec3 prismField(vec2 p, float t) {
    vec3 light = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 c = vec2(sin(t * (0.21 + fi * 0.05) + fi * 1.7) * 0.55 * aspect,
                      cos(t * (0.17 + fi * 0.04) + fi * 2.3) * 0.38);
        float r = 0.34 + 0.1 * sin(t * 0.3 + fi);
        float g = exp(-dot(p - c, p - c) / (r * r));
        float pulse = 0.5 + 0.5 * sin(t * 1.4 + fi * 1.9);
        light += mix(uAccent, uAccent2, 0.5 + 0.5 * sin(fi * 1.9 + t * 0.35)) * g * (0.4 + 0.6 * pulse);
    }
    float rays = 0.5 + 0.5 * sin((p.x + p.y * 0.4) * 9.0 + t * 0.6);
    return light * (0.6 + 0.3 * rays);
}

void main() {
    vec2 ts = 1.0 / vec2(textureSize(DepthTexture, 0));
    float d = min(hand(uv), min(hand(uv + vec2(-ts.x, 0.0)), hand(uv + vec2(0.0, -ts.y))));
    float mask = smoothstep(0.99, 0.98, d);
    if (mask < 0.01) discard;

    vec4 original = texture(ColorTexture, uv);
    vec2 p = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    vec3 mid = mix(uAccent, uAccent2, 0.5);
    vec3 col = mid * 0.08 + prismField(p, time) * 1.25 + mid * dot(original.rgb, vec3(0.299, 0.587, 0.114)) * 0.25;
    outColor = vec4(mix(original.rgb, col, 0.82), mask);
}
