#version 330
// Armour shaders (self/ArmorShade): vertex stage (vanilla ENTITY vertex format).
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out vec4 vColor;
out vec2 vUV;
out vec3 vNormal;
out vec3 vView;
out vec4 vLight;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vColor = Color;
    vUV = UV0;
    vNormal = normalize(mat3(ModelViewMat) * Normal);
    vView = view.xyz;
    vLight = texelFetch(Sampler2, UV2 / 16, 0);
}
