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
#ifdef shadowAtlasFlag
uniform sampler2D u_shadowAtlas;
uniform float u_shadowEnabled;
uniform vec2 u_shadowTexel;
uniform float u_shadowBias[16];
uniform mat4 u_shadowMatrices[16];
uniform vec4 u_shadowTiles[16];
uniform vec3 u_shadowPositions[16];
uniform float u_shadowFars[16];
uniform float u_dirShadowTile[2];
uniform float u_pointShadowTiles[30];
uniform float u_spotShadowTile[5];
float readPackedShadow(vec2 uv) {
    vec4 c = texture2D(u_shadowAtlas, uv);
    return c.r / 16581375.0 + c.g / 65025.0 + c.b / 255.0 + c.a;
}
// Compare at actual texel centers, compensating for the receiver plane's depth slope.
float shadowCompare(vec2 sampleUv, vec2 centerUv, float depth, vec2 gradient, vec2 lo, vec2 hi) {
    sampleUv = clamp(sampleUv, lo, hi);
    return step(depth + dot(gradient, sampleUv - centerUv), readPackedShadow(sampleUv));
}
float shadowBilinear(vec2 sampleUv, vec2 centerUv, float depth, vec2 gradient, vec2 lo, vec2 hi) {
    vec2 pixel = sampleUv / u_shadowTexel - 0.5;
    vec2 f = fract(pixel);
    vec2 p = (floor(pixel) + 0.5) * u_shadowTexel;
    float a = shadowCompare(p, centerUv, depth, gradient, lo, hi);
    float b = shadowCompare(p + vec2(u_shadowTexel.x, 0.0), centerUv, depth, gradient, lo, hi);
    float c = shadowCompare(p + vec2(0.0, u_shadowTexel.y), centerUv, depth, gradient, lo, hi);
    float d = shadowCompare(p + u_shadowTexel, centerUv, depth, gradient, lo, hi);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float shadowTileVisibility(float tileValue, vec3 worldPos, float radialDepth, float ndotl) {
    if (u_shadowEnabled < 0.5 || tileValue < 0.0) return 1.0;
    int tile = int(tileValue + 0.5);
    vec4 clip = u_shadowMatrices[tile] * vec4(worldPos, 1.0);
    vec3 ndc = clip.xyz / (abs(clip.w) < 1e-6 ? 1e-6 : clip.w);
    vec4 rect = u_shadowTiles[tile];
    vec2 uv = rect.xy + (ndc.xy * 0.5 + 0.5) * rect.zw;
    float compareDepth = radialDepth >= 0.0 ? radialDepth : ndc.z * 0.5 + 0.5;
    compareDepth -= u_shadowBias[tile] * (0.2 + 2.0 * (1.0 - clamp(ndotl, 0.0, 1.0)));
    vec2 dx = dFdx(uv), dy = dFdy(uv);
    float zx = dFdx(compareDepth), zy = dFdy(compareDepth);
    float determinant = dx.x * dy.y - dx.y * dy.x;
    vec2 gradient = vec2(0.0);
    if (abs(determinant) > 1e-12)
        gradient = vec2(dy.y * zx - dx.y * zy, dx.x * zy - dy.x * zx) / determinant;
    // Derivatives must be evaluated before a per-fragment coverage branch; otherwise neighboring
    // fragments outside the map leave undefined slopes and produce flickering border artifacts.
    if (abs(clip.w) < 1e-6 || any(greaterThan(abs(ndc.xy), vec2(1.0))) || ndc.z < -1.0 || ndc.z > 1.0) return 1.0;
    vec2 texel = u_shadowTexel;
    // Limit discontinuous derivatives at silhouettes/cube-face boundaries; retain a small numerical bias.
    gradient = clamp(gradient, vec2(-0.05) / texel, vec2(0.05) / texel);
    compareDepth -= max(0.000002, dot(abs(gradient), texel) * 0.02);
    vec2 lo = rect.xy + texel * 0.5;
    vec2 hi = rect.xy + rect.zw - texel * 0.5;
    float visible = 0.0;
    visible += shadowBilinear(uv + vec2(-texel.x, -texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(texel.x, -texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(-texel.x, texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(texel.x, texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    return visible * 0.25;
}
float pointShadowVisibility(int lightIndex, vec3 worldPos, float ndotl) {
    float firstTile = u_pointShadowTiles[lightIndex * 6];
    if (u_shadowEnabled < 0.5 || firstTile < 0.0) return 1.0;
    int firstIndex = int(firstTile + 0.5);
    vec3 ray = worldPos - u_shadowPositions[firstIndex];
    vec3 a = abs(ray);
    int face = a.x >= a.y && a.x >= a.z ? (ray.x >= 0.0 ? 0 : 1) :
               (a.y >= a.z ? (ray.y >= 0.0 ? 2 : 3) : (ray.z >= 0.0 ? 4 : 5));
    float tile = u_pointShadowTiles[lightIndex * 6 + face];
    int ti = int(tile + 0.5);
    return shadowTileVisibility(tile, worldPos, length(worldPos - u_shadowPositions[ti]) / max(u_shadowFars[ti], 1e-4), ndotl);
}
#endif

void main() {
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
    for (int i = 0; i < 2; i++) {
        if (i >= u_numDirectional) break;
        float ndotl = max(dot(n, -normalize(u_dirDirection[i])), 0.0);
        light += u_dirColor[i] * ndotl * shadowTileVisibility(u_dirShadowTile[i], v_world, -1.0, ndotl);
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
