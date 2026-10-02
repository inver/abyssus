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

attribute vec3 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord0;
uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform mat3 u_normalMatrix;
uniform vec3 u_cameraPos;
uniform float u_terrainSize;
uniform float u_fogK;
varying vec3 v_normal;
varying vec3 v_world;
varying vec2 v_uv;
varying vec2 v_splat;
varying float v_fog;
void main() {
    vec4 world = u_worldTrans * vec4(a_position, 1.0);
    gl_Position = u_projViewTrans * world;
    v_world = world.xyz;
    v_normal = normalize(u_normalMatrix * a_normal);
    v_uv = a_texCoord0;
    v_splat = a_position.xz / u_terrainSize;
    vec3 d = world.xyz - u_cameraPos;
    v_fog = min(dot(d, d) * u_fogK, 1.0);
}
