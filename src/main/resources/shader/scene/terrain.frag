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

// Splat-blended terrain layers lit by the ambient, up to 5 directional and 5 point lights, then fogged.
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
uniform vec3 u_fogColor;
uniform int u_numDirectional;
uniform vec3 u_dirDirection[5];
uniform vec3 u_dirColor[5];
uniform int u_numPoint;
uniform vec3 u_pointPosition[5];
uniform vec3 u_pointColor[5];
varying vec3 v_normal;
varying vec3 v_world;
varying vec2 v_uv;
varying vec2 v_splat;
varying float v_fog;
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
    vec3 light = u_ambient;
    for (int i = 0; i < 5; i++) {
        if (i >= u_numDirectional) break;
        light += u_dirColor[i] * max(dot(n, -normalize(u_dirDirection[i])), 0.0);
    }
    for (int i = 0; i < 5; i++) {
        if (i >= u_numPoint) break;
        vec3 toLight = u_pointPosition[i] - v_world;
        float dist = length(toLight);
        light += u_pointColor[i] * max(dot(n, toLight / max(dist, 0.0001)), 0.0) * (100.0 / (1.0 + dist * dist));
    }
    vec3 rgb = color.rgb * light;
    gl_FragColor = vec4(mix(rgb, u_fogColor, v_fog), 1.0);
}
