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

// Cosine-weighted average of the source cube over the hemisphere around each direction (irradiance / PI), so a
// uniform sky of radiance L gives L, the same units as the scene's ambient color.
uniform samplerCube u_source;
uniform int u_face;
uniform float u_sourceSize;
uniform float u_maxLod;
varying vec2 v_ndc;
const uint SAMPLES = 512u;
void main() {
    vec3 n = cubeDir(u_face, v_ndc);
    vec3 t;
    vec3 b;
    basis(n, t, b);
    vec3 sum = vec3(0.0);
    for (uint i = 0u; i < SAMPLES; i++) {
        vec2 xi = hammersley(i, SAMPLES);
        float phi = 2.0 * PI * xi.x;
        float cosTheta = sqrt(1.0 - xi.y);
        float sinTheta = sqrt(xi.y);
        vec3 l = t * (sinTheta * cos(phi)) + b * (sinTheta * sin(phi)) + n * cosTheta;
        float pdf = cosTheta / PI;
        sum += textureLod(u_source, l, sampleLod(pdf, float(SAMPLES), u_sourceSize, u_maxLod)).rgb;
    }
    gl_FragColor = vec4(sum / float(SAMPLES), 1.0);
}
