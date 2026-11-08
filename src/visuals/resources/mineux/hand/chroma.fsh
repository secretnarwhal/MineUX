#version 330 core

// The hand in moving rainbow bands, pulsing gently.

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform float time;
uniform float chromaSpeed;
uniform float chromaSaturation;
uniform float chromaBrightness;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// The depth buffer holds the hand alone, reversed-Z: 0 where there is no hand.
float hand(vec2 at) {
    return 1.0 - texture(DepthTexture, at).r;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(DepthTexture, 0));
    float d = min(hand(uv), min(hand(uv + vec2(-texel.x, 0.0)), hand(uv + vec2(0.0, -texel.y))));
    float mask = smoothstep(0.99, 0.98, d);
    if (mask < 0.01) discard;

    vec4 original = texture(ColorTexture, uv);
    float hue = fract(uv.y * 0.5 + uv.x * 0.3 + time * chromaSpeed) + sin(uv.y * 10.0 + time * 3.0) * 0.05;
    vec3 chroma = hsv2rgb(vec3(hue, chromaSaturation, chromaBrightness));
    chroma *= 0.88 + sin(time * 4.0) * sin(time * 4.0) * 0.12;
    outColor = vec4(mix(original.rgb, chroma, 0.8), mask);
}
