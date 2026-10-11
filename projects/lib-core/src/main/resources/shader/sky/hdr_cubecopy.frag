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

// Level 0 of the specular cube from a cube source: the source seen along each face direction (roughness 0).
uniform samplerCube u_source;
uniform int u_face;
varying vec2 v_ndc;
void main() {
    gl_FragColor = vec4(textureLod(u_source, cubeDir(u_face, v_ndc), 0.0).rgb, 1.0);
}
