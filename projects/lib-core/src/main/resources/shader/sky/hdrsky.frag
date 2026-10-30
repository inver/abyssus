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

// HDR sky background: the equirectangular image at full resolution, times the exposure, through the Narkowicz ACES fit
// and gamma 1/2.2. Same constants as HdrToneMap.kt.
uniform sampler2D u_equirect;
uniform float u_exposure;
varying vec3 v_dir;
void main() {
    vec3 c = textureLod(u_equirect, equirectUv(v_dir), 0.0).rgb * u_exposure;
    vec3 mapped = clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
    gl_FragColor = vec4(pow(mapped, vec3(1.0 / 2.2)), 1.0);
}
