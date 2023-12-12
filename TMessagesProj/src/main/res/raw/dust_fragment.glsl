#version 300 es

uniform sampler2D ourTexture;

in highp vec2 texCoord;
in highp float ourAlpha;

out highp vec4 fragColor;

void main(void)
{
    fragColor = texture(ourTexture, texCoord) * ourAlpha;
}