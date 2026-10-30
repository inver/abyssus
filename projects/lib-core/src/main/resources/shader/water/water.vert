// Copyright 2023-2026 Alexey Nevinsky
// SPDX-License-Identifier: Apache-2.0

// One water surface: a grid of x/z offsets from the surface centre, lifted by the same few travelling waves the CPU's
// WaterWaves evaluates (vertical displacement only). The wave table and the reduced phases come in as uniforms.
attribute vec2 a_position;
uniform mat4 u_projViewTrans;
uniform vec3 u_origin;     // surface centre; y is the mean water level
uniform vec4 u_waves[4];   // per wave: direction x, direction z, wave number k, amplitude
uniform vec4 u_phases;     // per wave: phase offset at this time, in [0, 2 pi)
uniform float u_cell;      // the longest side of a grid cell: waves shorter than two cells would only alias
uniform sampler2D u_terrainDepth; // packed linear view depth of terrain alone
uniform int u_hasDepth;
uniform mat4 u_invProjView;
uniform vec3 u_cameraPos;
uniform vec3 u_cameraForward;
uniform float u_far;
uniform float u_calmRange; // how far below the mean level the terrain must be before the waves are at full height
varying vec3 v_world;

float unpackDepth(vec4 c) {
    return c.r / 16581375.0 + c.g / 65025.0 + c.b / 255.0 + c.a;
}

vec3 worldFromDepth(vec2 uv, float viewDepth) {
    vec4 n = u_invProjView * vec4(uv * 2.0 - 1.0, -1.0, 1.0);
    vec4 f = u_invProjView * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 ray = normalize(f.xyz / f.w - n.xyz / n.w);
    return u_cameraPos + ray * (viewDepth / max(dot(ray, u_cameraForward), 1e-4));
}

// Waves die out toward the shore, so a trough never sinks below terrain that a crest would show over (no ragged shoreline):
// 0 where terrain meets the mean level, 1 where it is u_calmRange or more below it.
float calmAt(vec3 flatPoint) {
    if (u_hasDepth == 0) return 1.0;
    vec4 clip = u_projViewTrans * vec4(flatPoint, 1.0);
    if (clip.w <= 0.0) return 1.0;
    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 1.0;
    float d = unpackDepth(textureLod(u_terrainDepth, uv, 0.0));
    if (d >= 0.999) return 1.0;
    return smoothstep(0.0, u_calmRange, u_origin.y - worldFromDepth(uv, d * u_far).y);
}

void main() {
    vec2 xz = u_origin.xz + a_position;
    float h = 0.0;
    for (int i = 0; i < 4; i++) {
        float resolved = clamp(1.0 - u_cell * u_waves[i].z / 3.14159, 0.0, 1.0);
        h += resolved * u_waves[i].w * sin(u_waves[i].z * dot(u_waves[i].xy, xz) + u_phases[i]);
    }
    vec3 world = vec3(xz.x, u_origin.y + h * calmAt(vec3(xz.x, u_origin.y, xz.y)), xz.y);
    v_world = world;
    gl_Position = u_projViewTrans * vec4(world, 1.0);
}
