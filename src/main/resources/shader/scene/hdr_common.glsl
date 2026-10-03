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

// Shared by the HDR sky programs; joined in front of each fragment shader (Shaders.load). Needs GLSL 1.30+ for uint.

const float PI = 3.14159265358979;

// Equirectangular (u, v) of a direction: centre column faces -Z, top row is +Y. Same formulas as Equirect.kt.
vec2 equirectUv(vec3 d) {
    d = normalize(d);
    return vec2(0.5 + atan(d.x, -d.z) / (2.0 * PI), acos(clamp(d.y, -1.0, 1.0)) / PI);
}

// The direction through point p (-1..1 on both axes, as rendered into the face) of cube face 0..5 (+X, -X, +Y, -Y, +Z, -Z).
vec3 cubeDir(int face, vec2 p) {
    if (face == 0) return normalize(vec3(1.0, -p.y, -p.x));
    if (face == 1) return normalize(vec3(-1.0, -p.y, p.x));
    if (face == 2) return normalize(vec3(p.x, 1.0, p.y));
    if (face == 3) return normalize(vec3(p.x, -1.0, -p.y));
    if (face == 4) return normalize(vec3(p.x, -p.y, 1.0));
    return normalize(vec3(-p.x, -p.y, -1.0));
}

vec2 hammersley(uint i, uint n) {
    uint b = i;
    b = (b << 16u) | (b >> 16u);
    b = ((b & 0x55555555u) << 1u) | ((b & 0xAAAAAAAAu) >> 1u);
    b = ((b & 0x33333333u) << 2u) | ((b & 0xCCCCCCCCu) >> 2u);
    b = ((b & 0x0F0F0F0Fu) << 4u) | ((b & 0xF0F0F0F0u) >> 4u);
    b = ((b & 0x00FF00FFu) << 8u) | ((b & 0xFF00FF00u) >> 8u);
    return vec2(float(i) / float(n), float(b) * 2.3283064365386963e-10);
}

// Two tangents completing n to an orthonormal basis.
void basis(vec3 n, out vec3 t, out vec3 b) {
    vec3 up = abs(n.y) < 0.999 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    t = normalize(cross(up, n));
    b = cross(n, t);
}

// Mip level of a source cube (size px per face) that matches a sample covering 1 / (count * pdf) steradians.
float sampleLod(float pdf, float count, float size, float maxLod) {
    float texel = 4.0 * PI / (6.0 * size * size);
    float sampleArea = 1.0 / (count * pdf + 1e-4);
    return clamp(0.5 * log2(sampleArea / texel) + 1.0, 0.0, maxLod);
}
