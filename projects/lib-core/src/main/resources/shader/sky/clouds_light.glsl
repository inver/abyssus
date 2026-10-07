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

// What every cloud technique shares after clouds_common.glsl: the band being drawn, where a view ray meets it, and how
// clouds are lit (the sun after the atmosphere, Henyey-Greenstein scattering, Beer-Lambert extinction, the sky's zenith
// and horizon light). Colors are tone mapped like the fixture sky (1 - exp(-c), gamma 2.2) and returned premultiplied.

uniform vec3 u_sunDir;          // unit vector toward the sun
uniform float u_planetRadius;   // metres
uniform float u_cameraHeight;   // metres above the ground
uniform vec3 u_sunlight;        // the sun's light at the clouds, linear
uniform vec3 u_zenith;          // sky radiance straight up, linear
uniform vec3 u_horizon;         // sky radiance at the horizon, linear

uniform vec2 u_bandScale;       // (type scale * stretch, type scale), metres per noise cell
uniform vec2 u_bandOffset;      // wrapped wind drift, noise cells
uniform int u_bandSeed;
uniform int u_bandOctaves;
uniform int u_bandProfile;
uniform float u_bandCoverage;
uniform float u_bandDensity;
uniform float u_bandBase;       // metres
uniform float u_bandTop;
uniform float u_pixelAngle;     // radians one pixel of the target spans

const float CLOUD_PI = 3.14159265;
const float CLOUD_FADE_DISTANCE = 60000.0; // metres over which far clouds fade into the horizon light
const float CLOUD_FADE_START = 25000.0;    // metres beyond which clouds thin out: finer than a pixel, they would alias
const float CLOUD_FADE_END = 70000.0;

// Distance along the unit d from the viewer to the sphere `altitude` metres above the ground (the way out); -1 when it
// is missed. Worked around the viewer, not the planet's centre: a planet radius in metres squared leaves a float no
// digits for the camera height.
float cloudSphere(vec3 d, float altitude) {
    float oy = u_planetRadius + u_cameraHeight;
    float b = oy * d.y;
    float c = (u_cameraHeight - altitude) * (2.0 * u_planetRadius + u_cameraHeight + altitude);
    float h = b * b - c;
    if (h < 0.0) return -1.0;
    float s = sqrt(h);
    return b > 0.0 ? -c / (b + s) : s - b;
}

// True when the ground is in front of the point t along d.
bool cloudBehindGround(vec3 d, float t) {
    float oy = u_planetRadius + u_cameraHeight;
    float b = oy * d.y;
    if (b >= 0.0) return false;
    float c = u_cameraHeight * (2.0 * u_planetRadius + u_cameraHeight);
    float h = b * b - c;
    if (h < 0.0) return false;
    return c / (sqrt(h) - b) < t;
}

// The up direction of the planet at t along d (the curvature that lets clouds meet the horizon).
vec3 cloudUp(vec3 d, float t) {
    return normalize(vec3(d.x * t, u_planetRadius + u_cameraHeight + d.y * t, d.z * t));
}

float cloudBandCoverage(vec2 xz) {
    return cloudCoverage(xz, u_bandScale, u_bandOffset, u_bandSeed, u_bandOctaves, u_bandCoverage);
}

// The size, in first-octave noise cells, of one pixel where the view ray d meets a band at distance t: at grazing
// angles a pixel covers kilometres of cloud along the ray.
float cloudFootprint(vec3 d, float t, vec3 up) {
    return t * u_pixelAngle / max(abs(dot(d, up)), 0.02) / u_bandScale.y;
}

// The band's coverage with detail finer than the pixel's [footprint] left out, so far clouds do not sparkle.
float cloudBandCoverageLod(vec2 xz, float footprint) {
    float u = xz.x / u_bandScale.x + u_bandOffset.x;
    float v = xz.y / u_bandScale.y + u_bandOffset.y;
    return cloudRemap(cloudFbmLod(u, v, u_bandSeed, u_bandOctaves, footprint), u_bandCoverage);
}

// What is kept of a cloud whose first noise octave is finer than its pixel: it thins out instead of aliasing.
float cloudFootprintFade(float footprint) {
    return 1.0 - smoothstep(0.3, 1.0, footprint);
}

float cloudHg(float mu, float g) {
    return (1.0 - g * g) / (4.0 * CLOUD_PI * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
}

// Forward scattering toward the sun with a little back scattering.
float cloudPhase(float mu) {
    return mix(cloudHg(mu, 0.6), cloudHg(mu, -0.25), 0.3);
}

// Light reaching a cloud point through optical depth tau toward the sun; the second term stands in for light
// scattered more than once, so thick clouds are dark but never black.
float cloudSunTransmittance(float tau) {
    return max(exp(-tau), 0.25 * exp(-tau * 0.25));
}

// Linear radiance of a cloud point: the sun scattered toward the viewer through sunTau, and the sky's light from above
// through skyTau (the cloud over the point), dimmed further by `underside` (0 at the top of a cloud, 1 at its base).
vec3 cloudRadiance(vec3 d, float sunTau, float skyTau, float underside, vec3 up) {
    float mu = dot(d, normalize(u_sunDir));
    vec3 sky = mix(u_horizon, u_zenith, clamp(up.y * 0.5 + 0.5, 0.0, 1.0)) * 2.0;
    float skyLight = max(exp(-skyTau * 0.25), 0.12) * (1.0 - 0.4 * underside);
    return u_sunlight * cloudPhase(mu) * cloudSunTransmittance(sunTau) + sky * skyLight;
}

// How much of a cloud at distance t is kept: far clouds thin out instead of aliasing at the horizon.
float cloudDistanceFade(float t) {
    return 1.0 - smoothstep(CLOUD_FADE_START, CLOUD_FADE_END, t);
}

// A fixed per-pixel offset in 0..1 (interleaved gradient noise) that turns banding into fine grain.
float cloudDither(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

// Far clouds fade toward the horizon light, as the atmosphere between hides them.
vec3 cloudAerial(vec3 radiance, float t) {
    float fog = 1.0 - exp(-t / CLOUD_FADE_DISTANCE);
    return mix(radiance, u_horizon * 1.5, fog * 0.7);
}

vec3 cloudToneMap(vec3 radiance) {
    return pow(1.0 - exp(-radiance), vec3(1.0 / 2.2));
}

// The slant path through a slab of the given thickness along d where the planet's up is `up`.
float cloudSlant(vec3 d, vec3 up, float thickness) {
    return thickness / max(abs(dot(d, up)), 0.05);
}
