#version 330 core

in vec2 uv;
out vec4 fragColor;

uniform sampler2D uSource;

float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }

void main() {
    vec3 c = textureLod(uSource, uv, 0.0).rgb;
    vec2 fc = gl_FragCoord.xy;
    c += (hash12(fc) + hash12(fc + 71.3) - 1.0) * (1.0 / 255.0);
    fragColor = vec4(c, 1.0);
}
