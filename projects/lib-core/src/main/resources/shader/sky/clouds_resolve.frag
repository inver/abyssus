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

// Temporal accumulation for volumetric clouds: this frame's noisy march blended with the history, which is found where
// the same view direction fell in the previous frame (the sky is at infinity, so the camera's rotation is all that
// moves it). The history is clamped to this frame's 3x3 neighbourhood so moving clouds leave no ghosts.
uniform sampler2D u_current;
uniform sampler2D u_history;
uniform mat4 u_prevViewProj;    // previous frame's projection times its rotation-only view
uniform int u_hasHistory;
uniform float u_blend;          // weight of this frame
uniform vec2 u_texel;           // one texel of the targets in uv units
varying vec3 v_dir;
varying vec2 v_uv;

void main() {
    vec4 current = texture2D(u_current, v_uv);
    if (u_hasHistory == 0) {
        gl_FragColor = current;
        return;
    }
    vec4 clip = u_prevViewProj * vec4(normalize(v_dir), 1.0);
    if (clip.w <= 0.0) {
        gl_FragColor = current;
        return;
    }
    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {
        gl_FragColor = current;
        return;
    }
    vec4 low = current;
    vec4 high = current;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec4 c = texture2D(u_current, v_uv + vec2(float(x), float(y)) * u_texel);
        low = min(low, c);
        high = max(high, c);
    }
    vec4 history = clamp(texture2D(u_history, uv), low, high);
    gl_FragColor = mix(history, current, u_blend);
}
