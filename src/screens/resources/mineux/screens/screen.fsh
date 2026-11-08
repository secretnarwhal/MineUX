#version 330

uniform sampler2D Sampler0;

in vec2 imageCoord;
in vec4 tint;

out vec4 fragColor;

void main() {
    // Captured frames have no mipmaps. Averaging four samples spread over the area this pixel
    // covers keeps text on a far away screen from shimmering.
    vec2 quarterX = dFdx(imageCoord) * 0.25;
    vec2 quarterY = dFdy(imageCoord) * 0.25;

    vec3 image = texture(Sampler0, imageCoord - quarterX - quarterY).rgb
            + texture(Sampler0, imageCoord + quarterX - quarterY).rgb
            + texture(Sampler0, imageCoord - quarterX + quarterY).rgb
            + texture(Sampler0, imageCoord + quarterX + quarterY).rgb;

    // The tint is white for the image and black for the back of a screen, its alpha the opacity
    fragColor = vec4(image * 0.25 * tint.rgb, tint.a);
}
