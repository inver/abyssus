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

// The cloud coverage field shared by every cloud technique: the GLSL twin of CloudField.kt in core. Both do the same
// 32-bit integer hash and float operations in the same order (CloudFieldParityGlTest keeps them equal), so change
// both together. Needs GLSL 1.50 (unsigned integers).

const float CLOUD_NOISE_PERIOD = 64.0;
const float CLOUD_EDGE = 0.2;
const float CLOUD_EXTINCTION = 0.003;
const int CLOUD_PROFILE_HEAPED = 0;
const int CLOUD_PROFILE_LAYERED = 1;
const int CLOUD_PROFILE_WISPY = 2;

uint cloudHash(int x, int y, int seed) {
    uint h = (uint(x) * 0x8da6b343u) ^ (uint(y) * 0xd8163841u) ^ (uint(seed) * 0xcb1ab31fu);
    h = h ^ (h >> 16u);
    h = h * 0x7feb352du;
    h = h ^ (h >> 15u);
    h = h * 0x846ca68bu;
    h = h ^ (h >> 16u);
    return h;
}

float cloudMod(float x, float y) { return x - y * floor(x / y); }

float cloudLerp(float a, float b, float t) { return a + (b - a) * t; }

float cloudFade(float t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }

float cloudGrad(float x, float y, int seed, float dx, float dy) {
    uint g = cloudHash(int(x), int(y), seed) & 7u;
    if (g == 0u) return dx;
    if (g == 1u) return -dx;
    if (g == 2u) return dy;
    if (g == 3u) return -dy;
    if (g == 4u) return (dx + dy) * 0.70710677;
    if (g == 5u) return (-dx + dy) * 0.70710677;
    if (g == 6u) return (dx - dy) * 0.70710677;
    return (-dx - dy) * 0.70710677;
}

// gradient noise in 0..1 repeating every `period` cells
float cloudNoise(float u, float v, int seed, float period) {
    float iu = floor(u);
    float iv = floor(v);
    float fu = u - iu;
    float fv = v - iv;
    float x0 = cloudMod(iu, period);
    float y0 = cloudMod(iv, period);
    float x1 = cloudMod(iu + 1.0, period);
    float y1 = cloudMod(iv + 1.0, period);
    float a = cloudGrad(x0, y0, seed, fu, fv);
    float b = cloudGrad(x1, y0, seed, fu - 1.0, fv);
    float c = cloudGrad(x0, y1, seed, fu, fv - 1.0);
    float d = cloudGrad(x1, y1, seed, fu - 1.0, fv - 1.0);
    float su = cloudFade(fu);
    float sv = cloudFade(fv);
    float n = cloudLerp(cloudLerp(a, b, su), cloudLerp(c, d, su), sv);
    return clamp(0.5 + n * 0.7071, 0.0, 1.0);
}

float cloudFbm(float u, float v, int seed, int octaves) {
    float sum = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    float frequency = 1.0;
    float period = CLOUD_NOISE_PERIOD;
    for (int k = 0; k < octaves; k++) {
        sum += amplitude * cloudNoise(u * frequency, v * frequency, seed * 16 + k, period);
        total += amplitude;
        amplitude *= 0.5;
        frequency *= 2.0;
        period *= 2.0;
    }
    return sum / total;
}

// cloudFbm with octaves finer than a pixel faded out: `footprint` is the pixel's size in first-octave cells. A
// footprint of 0 gives exactly cloudFbm.
float cloudFbmLod(float u, float v, int seed, int octaves, float footprint) {
    float sum = 0.0;
    float amplitude = 0.5;
    float total = 0.0;
    float frequency = 1.0;
    float period = CLOUD_NOISE_PERIOD;
    for (int k = 0; k < octaves; k++) {
        float weight = 1.0 - smoothstep(0.25, 0.5, footprint * frequency);
        if (k > 0) {
            sum += amplitude * cloudLerp(0.5, cloudNoise(u * frequency, v * frequency, seed * 16 + k, period), weight);
        } else {
            sum += amplitude * cloudNoise(u, v, seed * 16, period);
        }
        total += amplitude;
        amplitude *= 0.5;
        frequency *= 2.0;
        period *= 2.0;
    }
    return sum / total;
}

float cloudRemap(float fbm, float coverage) {
    return clamp((fbm - 1.0 + (1.0 + CLOUD_EDGE) * coverage) / CLOUD_EDGE, 0.0, 1.0);
}

// Coverage 0..1 above the horizontal point xz (metres from the camera). scale is (type scale * stretch, type scale),
// offset the band's wrapped wind drift (CloudField.windOffset), seed the type's ordinal.
float cloudCoverage(vec2 xz, vec2 scale, vec2 offset, int seed, int octaves, float coverage) {
    float u = xz.x / scale.x + offset.x;
    float v = xz.y / scale.y + offset.y;
    return cloudRemap(cloudFbm(u, v, seed, octaves), coverage);
}

// The share of the band's density at height h (0 at base, 1 at top) where the coverage is `coverage`.
float cloudShape(int profile, float coverage, float h) {
    if (h < 0.0 || h > 1.0) return 0.0;
    if (profile == CLOUD_PROFILE_HEAPED) return clamp((coverage - h * h * 0.8) / 0.2, 0.0, 1.0) * smoothstep(0.0, 0.08, h);
    if (profile == CLOUD_PROFILE_LAYERED) return coverage * smoothstep(0.0, 0.15, h) * (1.0 - smoothstep(0.85, 1.0, h));
    return coverage * smoothstep(0.0, 0.3, h) * (1.0 - smoothstep(0.7, 1.0, h));
}

// Where in the band (0 base, 1 top) most of a profile's density is: heaped clouds are densest low down.
float cloudCentre(int profile) {
    return profile == CLOUD_PROFILE_HEAPED ? 0.4 : 0.5;
}

// The band's shape averaged over its height: how dense a column of it is where the coverage is \`coverage\`.
float cloudColumn(int profile, float coverage) {
    return 0.25 * (cloudShape(profile, coverage, 0.125) + cloudShape(profile, coverage, 0.375) +
        cloudShape(profile, coverage, 0.625) + cloudShape(profile, coverage, 0.875));
}
