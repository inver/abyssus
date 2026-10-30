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

// Bakes one period of a cloud type's coverage noise (cloudFbm, before the coverage remap) into a texture: the field
// repeats every CLOUD_NOISE_PERIOD cells, so the march reads it with a repeating, mipmapped lookup instead of summing
// the octaves at every step. Needs clouds_common.glsl before it.
uniform int u_seed;
uniform int u_octaves;
varying vec2 v_uv;

void main() {
    vec2 cell = v_uv * CLOUD_NOISE_PERIOD;
    gl_FragColor = vec4(cloudFbm(cell.x, cell.y, u_seed, u_octaves));
}
