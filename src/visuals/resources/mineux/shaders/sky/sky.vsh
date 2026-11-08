#version 330 core

// One triangle past the screen's corners, from the vertex index alone: no vertex buffer.
uniform mat4 uInvViewProj;

out vec4 vNear;
out vec4 vFar;

void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0;
    gl_Position = vec4(p, 0.0, 1.0);
    vNear = uInvViewProj * vec4(p, -1.0, 1.0);
    vFar  = uInvViewProj * vec4(p,  1.0, 1.0);
}
