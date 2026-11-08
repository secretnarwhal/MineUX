#version 330 core

// The hand in big pixels of a few colours, each block edged dark against the world.

in vec2 uv;
out vec4 outColor;

uniform sampler2D ColorTexture;
uniform sampler2D DepthTexture;
uniform vec2 texel;
uniform float pxScale;
uniform float pixelSize;

bool hand(vec2 at) {
    return 1.0 - texture(DepthTexture, at).r < 0.99;
}

void main() {
    vec2 cell = texel * max(2.0, pixelSize * max(pxScale, 0.5));
    vec2 q = (floor(uv / cell) + 0.5) * cell;
    bool inBlock = hand(q);
    if (!inBlock && !hand(uv)) discard;
    if (!inBlock) {
        outColor = vec4(texture(ColorTexture, uv).rgb, 1.0);
        return;
    }
    vec3 c = floor(texture(ColorTexture, q).rgb * 6.0 + 0.5) / 6.0;
    vec2 f = fract(uv / cell);
    float edge = 0.0;
    if (!hand(q - vec2(cell.x, 0.0)) && f.x < 0.2) edge = 1.0;
    if (!hand(q + vec2(cell.x, 0.0)) && f.x > 0.8) edge = 1.0;
    if (!hand(q - vec2(0.0, cell.y)) && f.y < 0.2) edge = 1.0;
    if (!hand(q + vec2(0.0, cell.y)) && f.y > 0.8) edge = 1.0;
    outColor = vec4(mix(c, c * 0.25, edge), 1.0);
}
