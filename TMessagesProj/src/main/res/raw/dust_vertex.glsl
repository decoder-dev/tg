#version 300 es

layout (location = 0) in vec2 in_pPos;
layout (location = 1) in vec2 in_pVelocity;
layout (location = 2) in float in_pLifetime;

// Uniforms
uniform int        pictureSizeX;
uniform int        pictureSizeY;

uniform int        screenSizeX;
uniform int        screenSizeY;

uniform float       phase;
uniform float       timeStep;
uniform vec2       particleSize;
uniform float reset;
uniform vec2 pos0;


out vec2 out_pPos;
out vec2 out_pVelocity;
out float out_pLifetime;

out vec2 texCoord;
out float ourAlpha;


float lokiSeed;


vec2 quadVertices[6] = vec2[6](
vec2(0.0, 0.0),
vec2(1.0, 0.0),
vec2(0.0, 1.0),
vec2(1.0, 0.0),
vec2(0.0, 1.0),
vec2(1.0, 1.0)
);


float particleEaseInValueAt(float fraction, float t) {
    float windowSize = 0.8;

    float effectiveT = t;
    float windowStartOffset = -windowSize;
    float windowEndOffset = 1.0;

    float windowPosition = (1.0 - fraction) * windowStartOffset + fraction * windowEndOffset;
    float windowT = max(0.0, min(windowSize, effectiveT - windowPosition)) / windowSize;
    float localT = 1.0 - windowT;

    return localT;
}

int TausStep(int z, int s1, int s2, int s3, int M) {
    int b=(((z << s1) ^ z) >> s2);
    return (((z & M) << s3) ^ b);
}

void initLoki(int seed1, int seed2, int seed3) {
    int seed = seed1 * 1099087573;
    int seedb = seed2 * 1099087573;
    int seedc = seed3 * 1099087573;

    // Round 1: Randomise seed
    int z1 = TausStep(seed, 13, 19, 12, 429496729);
    int z2 = TausStep(seed, 2, 25, 4, 4294967288);
    int z3 = TausStep(seed, 3, 11, 17, 429496280);
    int z4 = (1664525*seed + 1013904223);

    // Round 2: Randomise seed again using second seed
    int r1 = (z1^z2^z3^z4^seedb);

    z1 = TausStep(r1, 13, 19, 12, 429496729);
    z2 = TausStep(r1, 2, 25, 4, 4294967288);
    z3 = TausStep(r1, 3, 11, 17, 429496280);
    z4 = (1664525*r1 + 1013904223);

    // Round 3: Randomise seed again using third seed
    r1 = (z1^z2^z3^z4^seedc);

    z1 = TausStep(r1, 13, 19, 12, 429496729);
    z2 = TausStep(r1, 2, 25, 4, 4294967288);
    z3 = TausStep(r1, 3, 11, 17, 429496280);
    z4 = (1664525*r1 + 1013904223);

    lokiSeed = float(z1^z2^z3^z4) * 2.3283064365387e-10;
}

float rand() {
    int hashed_seed = int(lokiSeed * float(1099087573));

    int z1 = TausStep(hashed_seed, 13, 19, 12, 429496729);
    int z2 = TausStep(hashed_seed, 2, 25, 4, 4294967288);
    int z3 = TausStep(hashed_seed, 3, 11, 17, 429496280);
    int z4 = (1664525*hashed_seed + 1013904223);

    float old_seed = lokiSeed;

    lokiSeed = float(z1^z2^z3^z4) * 2.3283064365387e-10;

    return old_seed;
}

void main(void)
{
    int quadVerticeId = gl_VertexID % 6;
    int pId = gl_VertexID / 6;

    float x = (float(pId % pictureSizeX) + quadVertices[quadVerticeId].x) * particleSize.x + pos0.x;
    float y = (float(pId / pictureSizeX) + quadVertices[quadVerticeId].y) * particleSize.y + pos0.y;

    float easeInDuration = 0.8;
    float effectFraction = max(0.0, min(easeInDuration, phase)) / easeInDuration;

    float particleXFraction = float(pId % pictureSizeX) / float(pictureSizeX);
    float particleFraction = particleEaseInValueAt(effectFraction, particleXFraction);

    vec2 pPos = in_pPos;
    vec2 pVelocity = in_pVelocity;
    float pLifetime = in_pLifetime;
    if (reset > 0.5) {
        initLoki(pId, 1, 1);
        pPos = vec2(x, y);
        float direction = rand() * (3.14159265 * 2.0);
        float velocity = (0.1 + rand() * (0.2 - 0.1)) * 1400.0;
        pVelocity = vec2(cos(direction) * velocity, sin(direction) * velocity);
        pLifetime = 0.7 + rand() * (1.5 - 0.7);
    } else {
        pPos = pPos + (pVelocity * timeStep) * particleFraction;
        pVelocity = pVelocity - vec2(0.0, timeStep * 420.0) * particleFraction;
        pLifetime = max(0.0, pLifetime - timeStep * particleFraction);
    }

    vec2 pPos2 = vec2(pPos.x / float(screenSizeX), pPos.y / float(screenSizeY));

    float texX = x/float(screenSizeX);
    float texY = y/float(screenSizeY);

    pPos2 = vec2(pPos2.x * 2.0 - 1.0, 1.0 - pPos2.y * 2.0);

    ourAlpha = max(0.0, min(0.3, pLifetime) / 0.3);

    out_pPos = pPos;
    out_pVelocity = pVelocity;
    out_pLifetime = pLifetime;

    gl_Position = vec4(pPos2, 0.0, 1.0);

    texCoord = vec2(texX, texY);
}