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

// The cloud shadow map: for each texel (a ground point of a square of u_extent metres around u_center) the share of the
// sun's light the clouds let through, as SunOcclusion.instant computes it on the CPU: the transmittance along the sun
// ray through every band (the extinction of the band's column where the ray crosses it mid-band, times its density and the
// ray's path through it), never below u_floor. Needs clouds_common.glsl before it. The per-band shift (the sun's horizontal
// travel to the band), coefficient (extinction * density * path) and wind offset are computed on the CPU.
const int MAX_BANDS = 3;
uniform int u_bandCount;
uniform vec2 u_bandScale[MAX_BANDS];   // (scale * stretch, scale), metres per noise cell
uniform vec2 u_bandOffset[MAX_BANDS];  // wrapped wind drift, noise cells
uniform vec2 u_bandShift[MAX_BANDS];   // metres the sun ray travels horizontally from the ground to the band
uniform int u_bandSeed[MAX_BANDS];
uniform int u_bandOctaves[MAX_BANDS];
uniform int u_bandProfile[MAX_BANDS];
uniform float u_bandCoverage[MAX_BANDS];
uniform float u_bandCoeff[MAX_BANDS];
uniform vec2 u_center;                 // world x and z of the map's middle
uniform float u_extent;                // metres across
uniform float u_floor;
varying vec2 v_uv;

void main() {
    vec2 ground = u_center + (v_uv - 0.5) * u_extent;
    float depth = 0.0;
    for (int i = 0; i < MAX_BANDS; i++) {
        if (i >= u_bandCount) break;
        float coverage = cloudCoverage(ground + u_bandShift[i], u_bandScale[i], u_bandOffset[i], u_bandSeed[i],
            u_bandOctaves[i], u_bandCoverage[i]);
        depth += u_bandCoeff[i] * cloudColumn(u_bandProfile[i], coverage);
    }
    gl_FragColor = vec4(max(exp(-depth), u_floor), 0.0, 0.0, 1.0);
}
