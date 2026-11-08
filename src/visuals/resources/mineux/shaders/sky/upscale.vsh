#version 330 core

// One triangle past the screen's corners, from the vertex index alone: no vertex buffer.
out vec2 uv;

void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0;
    gl_Position = vec4(p, 0.0, 1.0);
    uv = p * 0.5 + 0.5;
}
