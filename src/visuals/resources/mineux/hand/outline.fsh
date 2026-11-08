#version 330 core

// A line round the hand, and the hand tinted if asked.

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform vec3 outlineColor;
uniform float outlineThickness;
uniform float pxScale;
uniform float tintEnabled;
uniform vec3 tintColor;
uniform float tintAmount;

float hand(vec2 at) {
    return 1.0 - texture(DepthTexture, at).r;
}

void main() {
    if (hand(uv) < 0.99) {
        vec4 original = texture(ColorTexture, uv);
        if (tintEnabled < 0.5) discard;
        outColor = vec4(mix(original.rgb, tintColor, tintAmount), 1.0);
        return;
    }

    vec2 texel = 1.0 / vec2(textureSize(DepthTexture, 0));
    float r = outlineThickness * max(pxScale, 0.5);
    const int N = 16;
    float nearest = r + 1.0;
    bool hit = false;
    for (int i = 0; i < N; i++) {
        float angle = (float(i) + 0.5) / float(N) * 6.28318530718;
        vec2 dir = vec2(cos(angle), sin(angle)) * texel;
        for (int ri = 1; ri <= 3; ri++) {
            float at = r * float(ri) / 3.0;
            if (hand(uv + dir * at) < 0.99) {
                nearest = min(nearest, at);
                hit = true;
                break;
            }
        }
        if (hit && nearest < 0.4 * r) break;
    }
    if (!hit) discard;

    outColor = vec4(outlineColor, clamp(smoothstep(r, 0.0, nearest), 0.0, 1.0));
}
