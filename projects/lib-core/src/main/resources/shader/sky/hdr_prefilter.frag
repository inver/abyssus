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

// GGX prefilter of the source cube for one roughness (split sum, N = V = R), importance sampled; each sample reads the
// source mip matching its solid angle so a small bright sun does not alias into fireflies.
uniform samplerCube u_source;
uniform int u_face;
uniform float u_roughness;
uniform float u_sourceSize;
uniform float u_maxLod;
varying vec2 v_ndc;
const uint SAMPLES = 128u;
void main() {
    vec3 n = cubeDir(u_face, v_ndc);
    if (u_roughness <= 0.0) {
        gl_FragColor = vec4(textureLod(u_source, n, 0.0).rgb, 1.0);
        return;
    }
    vec3 t;
    vec3 b;
    basis(n, t, b);
    float a = u_roughness * u_roughness;
    float a2 = a * a;
    vec3 sum = vec3(0.0);
    float weight = 0.0;
    for (uint i = 0u; i < SAMPLES; i++) {
        vec2 xi = hammersley(i, SAMPLES);
        float phi = 2.0 * PI * xi.x;
        float cosTheta = sqrt((1.0 - xi.y) / (1.0 + (a2 - 1.0) * xi.y));
        float sinTheta = sqrt(1.0 - cosTheta * cosTheta);
        vec3 h = normalize(t * (sinTheta * cos(phi)) + b * (sinTheta * sin(phi)) + n * cosTheta);
        vec3 l = normalize(2.0 * dot(n, h) * h - n);
        float nDotL = dot(n, l);
        if (nDotL > 0.0) {
            float nDotH = max(dot(n, h), 0.0);
            float d = nDotH * nDotH * (a2 - 1.0) + 1.0;
            float pdf = a2 / (PI * d * d) / 4.0 + 1e-4; // D * NdotH / (4 * HdotV) with V = N
            sum += textureLod(u_source, l, sampleLod(pdf, float(SAMPLES), u_sourceSize, u_maxLod)).rgb * nDotL;
            weight += nDotL;
        }
    }
    gl_FragColor = vec4(sum / max(weight, 1e-4), 1.0);
}
