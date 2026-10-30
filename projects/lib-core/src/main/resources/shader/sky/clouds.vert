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

// Fullscreen triangle at the far plane for the cloud passes; v_dir is the world view direction of each pixel (camera
// rotation only), like the procedural sky's own vertex shader.
attribute vec2 a_position;
uniform mat4 u_invViewProj;
varying vec3 v_dir;
varying vec2 v_uv;
void main() {
    vec4 farPoint = u_invViewProj * vec4(a_position, 1.0, 1.0);
    v_dir = farPoint.xyz / farPoint.w;
    v_uv = a_position * 0.5 + 0.5;
    gl_Position = vec4(a_position, 1.0, 1.0);
}
