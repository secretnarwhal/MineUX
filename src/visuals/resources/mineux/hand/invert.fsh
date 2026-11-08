#version 330 core

// The hand's colours inverted, with a glowing rim if asked.

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform float time;
uniform float invertIntensity;
uniform float edgeGlow;
uniform vec3 glowColor;

float hand(vec2 at) {
    return 1.0 - texture(DepthTexture, at).r;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(DepthTexture, 0));
    float center = hand(uv);
    float d = min(center, min(hand(uv + vec2(-texel.x, 0.0)), hand(uv + vec2(0.0, -texel.y))));
    float mask = smoothstep(0.99, 0.98, d);
    if (mask < 0.01) discard;

    vec3 base = texture(ColorTexture, uv).rgb;
    vec3 color = center < 0.99 ? mix(base, vec3(1.0) - base, invertIntensity) : base;

    if (edgeGlow > 0.01 && center < 0.99) {
        float edge = 0.0;
        if (hand(uv + vec2(texel.x, 0.0)) > 0.99) edge += 1.0;
        if (hand(uv - vec2(texel.x, 0.0)) > 0.99) edge += 1.0;
        if (hand(uv + vec2(0.0, texel.y)) > 0.99) edge += 1.0;
        if (hand(uv - vec2(0.0, texel.y)) > 0.99) edge += 1.0;
        vec2 diag = 0.7071 * texel;
        if (hand(uv + diag) > 0.99) edge += 0.5;
        if (hand(uv - diag) > 0.99) edge += 0.5;
        if (hand(uv + vec2(diag.x, -diag.y)) > 0.99) edge += 0.5;
        if (hand(uv + vec2(-diag.x, diag.y)) > 0.99) edge += 0.5;
        float pulse = 0.75 + sin(time * 3.0) * sin(time * 3.0) * 0.25;
        color += glowColor * clamp(edge / 6.0, 0.0, 1.0) * edgeGlow * pulse;
    }

    outColor = vec4(color, mask);
}
