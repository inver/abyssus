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

// A unit cube around the camera, at the far plane (xyww), so everything else is drawn in front of it.
attribute vec3 a_position;
uniform mat4 u_viewProj;
varying vec3 v_dir;
void main() {
    v_dir = a_position;
    gl_Position = (u_viewProj * vec4(a_position, 1.0)).xyww;
}
