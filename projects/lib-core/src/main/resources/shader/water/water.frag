// Copyright 2023-2026 Alexey Nevinsky
// SPDX-License-Identifier: Apache-2.0

// Water seen from above: Fresnel mix of a planar reflection and the refracted scene behind the surface, which fades to
// the tint with the distance light travels through the water (depth-dependent absorption), sun glints and animated
// foam where terrain meets shallow water. The scene behind is read from a copy of the frame (u_sceneColor) and from
// packed linear view depth targets (u_sceneDepth for everything opaque, u_terrainDepth for terrain alone: only terrain
// makes a shore). A pixel nothing drew has depth 1: open deep water, never a shoreline.
#ifdef GL_ES
precision highp float;
#endif

#if __VERSION__ >= 130
#define textureCubeLodCompat textureLod
#else
#define textureCubeLodCompat textureCubeLod
#endif

uniform sampler2D u_sceneColor;
uniform sampler2D u_sceneDepth;
uniform sampler2D u_terrainDepth;
uniform sampler2D u_reflection;
uniform int u_hasReflection;  // 1: u_reflection is a planar reflection of this level
uniform samplerCube u_skyCube; // the sky's lighting cube (clouds included), shown where there is no planar reflection
uniform int u_hasSkyCube;
uniform int u_hasDepth;       // 0: no scene copy or depth targets (a resource failure): plain tinted water

uniform vec3 u_cameraPos;
uniform vec3 u_cameraForward;
uniform mat4 u_invProjView;
uniform vec2 u_viewport;      // size of the frame the scene copy and depth targets cover, in pixels
uniform float u_far;          // the far distance the packed depths are shares of

uniform vec4 u_waves[4];
uniform vec4 u_phases;
uniform float u_time;         // seconds, reduced to keep float precision
uniform float u_level;
uniform float u_amplitude;
uniform vec3 u_tint;
uniform float u_clarity;
uniform float u_foamAmount;
uniform float u_foamWidth;
uniform float u_calmRange;    // how far below the mean level the terrain must be before the waves are at full strength

uniform vec3 u_ambient;
uniform vec3 u_skyColor;      // what a missing reflection shows
uniform int u_numDirectional;
uniform vec3 u_dirDirection[5];
uniform vec3 u_dirColor[5];
uniform vec3 u_fogColor;
uniform float u_fogK;
varying vec3 v_world;

float unpackDepth(vec4 c) {
    return c.r / 16581375.0 + c.g / 65025.0 + c.b / 255.0 + c.a;
}

// The world position of the pixel at uv (0 to 1) whose view depth is viewDepth, along the camera's forward axis.
vec3 worldFromDepth(vec2 uv, float viewDepth) {
    vec4 n = u_invProjView * vec4(uv * 2.0 - 1.0, -1.0, 1.0);
    vec4 f = u_invProjView * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 ray = normalize(f.xyz / f.w - n.xyz / n.w);
    return u_cameraPos + ray * (viewDepth / max(dot(ray, u_cameraForward), 1e-4));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// How well a feature of wave number k is resolved when a pixel covers footprint world units: 1 when well sampled, 0 when
// the pixel is wider than half a wavelength (it would only alias, so it fades to its average instead).
float resolved(float k, float footprint) {
    return clamp(1.0 - footprint * k / 3.14159, 0.0, 1.0);
}

// The unit surface normal: the analytic slope of the four waves plus a few small ripples (the same table, finer).
vec3 surfaceNormal(vec2 xz, float footprint) {
    vec2 slope = vec2(0.0);
    for (int i = 0; i < 4; i++) {
        float k = u_waves[i].z;
        slope += resolved(k, footprint) * u_waves[i].w * k * cos(k * dot(u_waves[i].xy, xz) + u_phases[i]) * u_waves[i].xy;
        float kr = k * 7.0;
        vec2 across = u_waves[i].yx * vec2(1.0, -1.0);
        slope += resolved(kr, footprint) * u_amplitude * 0.04 * kr * cos(kr * dot(across, xz) + u_phases[i] * 3.1 + 1.7) * across;
    }
    return normalize(vec3(-slope.x, 1.0, -slope.y));
}

float foamNoise(vec2 p, float footprint) {
    float coarse = valueNoise(p * 0.9 + vec2(u_time * 0.17, -u_time * 0.11));
    float fine = valueNoise(p * 2.4 - vec2(u_time * 0.23, u_time * 0.07));
    return mix(0.5, coarse, resolved(0.9 * 6.28, footprint)) * 0.6 + mix(0.5, fine, resolved(2.4 * 6.28, footprint)) * 0.4;
}

void main() {
    vec3 P = v_world;
    // derivatives first, outside any branch: how many world units one pixel covers
    vec2 spread = fwidth(P.xz);
    float footprint = max(spread.x, spread.y);
    vec2 uv = gl_FragCoord.xy / u_viewport;
    vec3 toEye = u_cameraPos - P;
    float dist = length(toEye);
    vec3 V = toEye / max(dist, 1e-4);
    float viewDepth = dot(P - u_cameraPos, u_cameraForward);
    bool hasDepth = u_hasDepth == 1;

    float sceneD = 1.0;
    float terrainD = 1.0;
    if (hasDepth) {
        sceneD = unpackDepth(texture2D(u_sceneDepth, uv));
        terrainD = unpackDepth(texture2D(u_terrainDepth, uv));
    }
    bool sceneEmpty = sceneD >= 0.999;
    bool terrainEmpty = terrainD >= 0.999;

    // Anything opaque nearer than the surface hides it. The packed depth is far more exact than the window depth buffer
    // (which is coarse at a distance), so the surface is drawn with a depth bias and the exact test decides the shoreline.
    float viewTolerance = 0.01 + viewDepth * 0.0001;
    if (!sceneEmpty && sceneD * u_far < viewDepth - viewTolerance) discard;

    // Land above the mean level hides the surface: a crest over a beach is not shown over the dry sand.
    if (!sceneEmpty && worldFromDepth(uv, sceneD * u_far).y > u_level + 0.002) discard;

    // Vertical separation between the mean level and the terrain under the surface along this pixel's ray.
    float shoreSep = 1000.0;
    if (!terrainEmpty) shoreSep = u_level - worldFromDepth(uv, terrainD * u_far).y;

    // Waves calm down as they reach the shore.
    float calm = smoothstep(0.0, max(u_calmRange * 2.0, 0.1), shoreSep);
    vec3 N = normalize(mix(vec3(0.0, 1.0, 0.0), surfaceNormal(P.xz, footprint), mix(0.35, 1.0, calm)));

    float cosTheta = clamp(dot(N, V), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosTheta, 5.0);

    // Illumination of the water body and of the foam.
    vec3 light = u_ambient;
    for (int i = 0; i < 5; i++) {
        if (i >= u_numDirectional) break;
        light += u_dirColor[i] * max(-normalize(u_dirDirection[i]).y, 0.0);
    }
    vec3 body = u_tint * (light * 0.8 + 0.2);

    // Refraction: the scene behind the surface, shifted by the wave normal, attenuated by the distance through water.
    vec3 refracted = body;
    if (hasDepth) {
        vec2 ruv = clamp(uv + N.xz * (0.045 / (1.0 + 0.015 * dist)), vec2(0.002), vec2(0.998));
        float rd = unpackDepth(texture2D(u_sceneDepth, ruv));
        bool rEmpty = rd >= 0.999;
        vec3 bottom = P;
        if (!rEmpty) {
            float rDepth = rd * u_far;
            bottom = worldFromDepth(ruv, rDepth);
            // a sample in front of the water (something above it) or on land is not part of the water's view: use the straight ray
            if (rDepth < viewDepth - 0.02 || bottom.y > u_level + 0.02) {
                ruv = uv;
                rEmpty = sceneEmpty;
                if (!sceneEmpty) bottom = worldFromDepth(uv, sceneD * u_far);
            }
        }
        if (!rEmpty) {
            // the distance light travels in the water, measured from the mean level so the waves do not stripe it
            vec3 along = normalize(P - u_cameraPos);
            vec3 surfaceAtLevel = u_cameraPos + along * ((u_level - u_cameraPos.y) / min(along.y, -1e-4));
            float path = distance(bottom, surfaceAtLevel);
            vec3 reach = u_clarity * (0.15 + 3.0 * u_tint);
            vec3 transmit = exp(-path / max(reach, vec3(1e-3)));
            refracted = texture2D(u_sceneColor, ruv).rgb * transmit + body * (1.0 - transmit);
        }
    }

    vec3 reflected = u_skyColor;
    if (u_hasReflection == 0 && u_hasSkyCube == 1) {
        reflected = textureCubeLodCompat(u_skyCube, reflect(-V, N), 1.0).rgb;
    }
    if (u_hasReflection == 1) {
        vec2 reflUV = clamp(uv + N.xz * 0.03, vec2(0.002), vec2(0.998));
        reflected = texture2D(u_reflection, reflUV).rgb;
    }

    vec3 color = mix(refracted, reflected, fresnel);

    // Sun glints.
    for (int i = 0; i < 5; i++) {
        if (i >= u_numDirectional) break;
        vec3 L = -normalize(u_dirDirection[i]);
        vec3 H = normalize(L + V);
        color += u_dirColor[i] * pow(max(dot(N, H), 0.0), 320.0) * 1.6;
    }

    // Foam: only where terrain meets shallow water; models, the extent edge and deep water make none.
    if (u_foamAmount > 0.0 && !terrainEmpty && shoreSep >= 0.0) {
        float band = 1.0 - smoothstep(0.0, max(u_foamWidth, 0.01), shoreSep);
        float lapping = 0.65 + 0.35 * sin(u_time * 1.3 + shoreSep * 5.0 / max(u_foamWidth, 0.1));
        float foam = clamp(band * (0.3 + 0.7 * foamNoise(P.xz, footprint)) * lapping * u_foamAmount * 1.7, 0.0, 1.0);
        color = mix(color, vec3(0.93, 0.97, 1.0) * (light * 0.6 + 0.4), foam);
    }

    float fog = min(dot(toEye, toEye) * u_fogK, 1.0);
    gl_FragColor = vec4(mix(color, u_fogColor, fog), 1.0);
}
