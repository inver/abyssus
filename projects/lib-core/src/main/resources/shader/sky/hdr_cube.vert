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

// Fullscreen triangle for rendering into one cube face; v_ndc is -1..1 across the face.
attribute vec2 a_position;
varying vec2 v_ndc;
void main() {
    v_ndc = a_position;
    gl_Position = vec4(a_position, 0.0, 1.0);
}
