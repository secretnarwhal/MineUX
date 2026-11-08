#version 330

// The uniform blocks are written out rather than imported: Screens hands the game its sources
// itself (ScreenRenderer.Sources), outside the game's resource packs. Same blocks as vanilla's.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 imageCoord;
out vec4 tint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    imageCoord = UV0;
    tint = Color;
}
