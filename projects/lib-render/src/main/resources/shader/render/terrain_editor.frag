// Copyright 2023-2026 Alexey Nevinsky
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Splat-blended terrain layers lit by the ambient (or an HDR sky's irradiance), up to 2 directional and 5 local lights, then fogged.
#ifdef GL_ES
precision mediump float;
#endif
uniform sampler2D u_splatBase;
uniform sampler2D u_splatR;
uniform sampler2D u_splatG;
uniform sampler2D u_splatB;
uniform sampler2D u_splatA;
uniform sampler2D u_splat;
uniform int u_hasSplat;
uniform int u_has_splatBase;
uniform int u_has_splatR;
uniform int u_has_splatG;
uniform int u_has_splatB;
uniform int u_has_splatA;
uniform vec3 u_ambient;
uniform samplerCube u_irradiance; // a built HDR sky's irradiance; replaces u_ambient when u_hasSky is 1
uniform int u_hasSky;
uniform samplerCube u_irradianceTo; // the sky light fading in; used while u_skyFade is above 0
uniform float u_skyFade; // 0 to 1: how far u_irradianceTo has replaced u_irradiance
uniform vec3 u_fogColor;
uniform int u_numDirectional;
uniform vec3 u_dirDirection[5];
uniform vec3 u_dirColor[5];
uniform int u_numPoint;
uniform vec3 u_pointPosition[5];
uniform vec3 u_pointColor[5];
uniform float u_pointRange[5];
uniform int u_numSpot;
uniform vec3 u_spotPosition[5];
uniform vec3 u_spotDirection[5];
uniform vec3 u_spotColor[5];
uniform float u_spotRange[5];
uniform float u_spotOuter[5];
uniform float u_spotInner[5];
varying vec3 v_normal;
varying vec3 v_world;
varying vec2 v_uv;
varying vec2 v_splat;
varying float v_fog;
#define shadowAtlasFlag
#define directionalVisibilityFlag
#pragma include "shadow_visibility.glsl"

uniform int u_clipEnabled; // 1 in a planar reflection pass: fragments with a x + b y + c z + d < 0 are discarded
uniform vec4 u_clipPlane;

void main() {
    if (u_clipEnabled == 1 && dot(v_world, u_clipPlane.xyz) + u_clipPlane.w < 0.0) discard;
    vec4 color = vec4(0.8, 0.8, 0.8, 1.0);
    if (u_has_splatBase == 1) color = texture2D(u_splatBase, v_uv);
    if (u_hasSplat == 1) {
        vec4 s = texture2D(u_splat, v_splat);
        if (u_has_splatR == 1) color = mix(color, texture2D(u_splatR, v_uv), s.r);
        if (u_has_splatG == 1) color = mix(color, texture2D(u_splatG, v_uv), s.g);
        if (u_has_splatB == 1) color = mix(color, texture2D(u_splatB, v_uv), s.b);
        if (u_has_splatA == 1) color = mix(color, texture2D(u_splatA, v_uv), s.a);
    }
    vec3 n = normalize(v_normal);
    vec3 light = u_hasSky == 1 ? textureCube(u_irradiance, n).rgb : u_ambient;
    if (u_hasSky == 1 && u_skyFade > 0.0) light = mix(light, textureCube(u_irradianceTo, n).rgb, u_skyFade);
    for (int i = 0; i < 2; i++) {
        if (i >= u_numDirectional) break;
        float ndotl = max(dot(n, -normalize(u_dirDirection[i])), 0.0);
        float visibility = shadowTileVisibility(u_dirShadowTile[i], v_world, -1.0, ndotl);
        if (i == u_dirVisIndex) visibility *= directionalVisibility(v_world);
        light += u_dirColor[i] * ndotl * visibility;
    }
    for (int i = 0; i < 5; i++) {
        if (i >= u_numPoint) break;
        vec3 toLight = u_pointPosition[i] - v_world;
        float dist = length(toLight);
        light += pointShadowVisibility(i, v_world, max(dot(n, toLight / max(dist, 0.0001)), 0.0)) * u_pointColor[i] * max(dot(n, toLight / max(dist, 0.0001)), 0.0) * (u_pointRange[i] / (1.0 + dist * dist)) * (1.0 - smoothstep(0.75 * u_pointRange[i], u_pointRange[i], dist));
    }
    for (int i = 0; i < 5; i++) {
        if (i >= u_numSpot) break;
        vec3 toLight = u_spotPosition[i] - v_world;
        float dist = length(toLight);
        vec3 L = toLight / max(dist, 0.0001);
        float c = dot(normalize(u_spotDirection[i]), -L);
        float cone = u_spotInner[i] <= u_spotOuter[i] ? step(u_spotOuter[i], c) : smoothstep(u_spotOuter[i], u_spotInner[i], c);
        light += shadowTileVisibility(u_spotShadowTile[i], v_world, -1.0, max(dot(n, L), 0.0)) * u_spotColor[i] * max(dot(n, L), 0.0) * (u_spotRange[i] / (1.0 + dist * dist)) *
            (1.0 - smoothstep(0.75 * u_spotRange[i], u_spotRange[i], dist)) * cone;
    }
    vec3 rgb = color.rgb * light;
    gl_FragColor = vec4(mix(rgb, u_fogColor, v_fog), 1.0);
}
