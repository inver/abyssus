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

// A gray veil over the scene and a ring of radius ~4% of the short side whose arc fades out behind its head.
#ifdef GL_ES
precision mediump float;
#endif
uniform vec2 u_resolution;
uniform float u_angle;
void main() {
    vec2 p = gl_FragCoord.xy - 0.5 * u_resolution;
    float radius = clamp(0.04 * min(u_resolution.x, u_resolution.y), 14.0, 60.0);
    float thickness = max(radius * 0.18, 2.0);
    float ring = 1.0 - smoothstep(thickness * 0.5 - 0.75, thickness * 0.5 + 0.75, abs(length(p) - radius));
    float behind = mod(u_angle - atan(p.y, p.x), 6.2831853) / 6.2831853; // 0 at the head, 1 just ahead of it
    float arc = ring * (1.0 - behind);
    gl_FragColor = vec4(mix(vec3(0.5), vec3(1.0), arc), mix(0.6, 1.0, arc));
}
