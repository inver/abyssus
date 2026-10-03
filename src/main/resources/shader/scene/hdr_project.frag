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

// Projects the equirectangular image onto a cube face, averaging 4 x 4 taps per texel so a 4096-wide image is not
// point-sampled into a 256 face.
uniform sampler2D u_equirect;
uniform int u_face;
uniform float u_texel; // one face texel in v_ndc units
varying vec2 v_ndc;
void main() {
    vec3 sum = vec3(0.0);
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            vec2 offset = (vec2(float(x), float(y)) - 1.5) / 4.0 * u_texel;
            sum += textureLod(u_equirect, equirectUv(cubeDir(u_face, v_ndc + offset)), 0.0).rgb;
        }
    }
    gl_FragColor = vec4(sum / 16.0, 1.0);
}
