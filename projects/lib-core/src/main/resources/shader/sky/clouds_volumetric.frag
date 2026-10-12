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

// Volumetric clouds: one band ray-marched in 3D between its base and top spheres, CLOUD_STEPS steps with a per-pixel
// jitter, each lit by CLOUD_LIGHT_STEPS steps toward the sun. The coverage field decides where the band has clouds;
// the 3D noise only erodes density inside it, never adds any outside. Drawn at half resolution into an offscreen
// target. Needs clouds_common.glsl and clouds_light.glsl before it.
uniform sampler3D u_baseNoise;
uniform sampler3D u_detailNoise;
uniform float u_frame;          // frame counter, moves the jitter
varying vec3 v_dir;

const int CLOUD_STEPS = 48;
const int CLOUD_LIGHT_STEPS = 6;
const float CLOUD_MAX_MARCH = 30000.0; // metres of band a ray marches at most (grazing rays)

// the per-pixel dither, shifted every frame for the temporal filter
float cloudJitter(vec2 pixel) {
    return cloudDither(pixel + u_frame * 5.588238);
}

// metres above the ground of the point p (relative to the viewer); curvature to first order
float cloudAltitude(vec3 p) {
    return u_cameraHeight + p.y + dot(p.xz, p.xz) / (2.0 * u_planetRadius);
}

vec3 cloudNoiseCoord(vec3 p, float altitude) {
    return vec3(p.x / u_bandScale.x + u_bandOffset.x, altitude / u_bandScale.y * 2.0, p.z / u_bandScale.y + u_bandOffset.y);
}

// density at p where the band's coverage is `coverage`: the shape eroded by the noise, never more than the shape
float cloudVolumeDensity(vec3 p, float coverage, bool detail) {
    float altitude = cloudAltitude(p);
    float h = (altitude - u_bandBase) / (u_bandTop - u_bandBase);
    float shape = cloudShape(u_bandProfile, coverage, h);
    if (shape <= 0.0) return 0.0;
    vec3 q = cloudNoiseCoord(p, altitude);
    float base = texture(u_baseNoise, q * 0.25).r;
    float eroded = shape * (0.55 + 0.45 * base);
    if (detail) eroded -= (1.0 - texture(u_detailNoise, q * 1.7).r) * 0.2 * (1.0 - shape);
    return u_bandDensity * max(eroded, 0.0);
}

void main() {
    vec3 d = normalize(v_dir);
    vec3 sun = normalize(u_sunDir);
    float t0 = cloudSphere(d, u_bandBase);
    float t1 = cloudSphere(d, u_bandTop);
    if (t0 <= 0.0 || t1 <= t0 || cloudBehindGround(d, t0)) discard;
    t1 = min(t1, t0 + CLOUD_MAX_MARCH);
    float step = (t1 - t0) / float(CLOUD_STEPS);
    float jitter = cloudJitter(gl_FragCoord.xy);
    float thickness = u_bandTop - u_bandBase;
    vec3 up0 = cloudUp(d, t0);
    float lightStep = thickness * 0.5 / float(CLOUD_LIGHT_STEPS) / max(dot(sun, up0), 0.1);
    float footprint = cloudFootprint(d, t0, up0);
    float keep = cloudFootprintFade(footprint);
    vec3 color = vec3(0.0);
    float transmittance = 1.0;
    for (int i = 0; i < CLOUD_STEPS; i++) {
        float t = t0 + (float(i) + jitter) * step;
        vec3 p = d * t;
        float coverage = cloudBandCoverageLod(p.xz, footprint);
        if (coverage <= 0.0) continue;
        float density = cloudVolumeDensity(p, coverage, true);
        if (density <= 0.0) continue;
        float sunTau = 0.0;
        for (int j = 1; j <= CLOUD_LIGHT_STEPS; j++) {
            sunTau += cloudVolumeDensity(p + sun * (lightStep * float(j)), coverage, false);
        }
        sunTau *= CLOUD_EXTINCTION * lightStep;
        float h = clamp((cloudAltitude(p) - u_bandBase) / thickness, 0.0, 1.0);
        float alpha = (1.0 - exp(-CLOUD_EXTINCTION * density * step)) * cloudDistanceFade(t) * keep;
        float skyTau = CLOUD_EXTINCTION * u_bandDensity * coverage * (1.0 - h) * thickness;
        vec3 radiance = cloudAerial(cloudRadiance(d, sunTau, skyTau, 1.0 - h, cloudUp(d, t)), t);
        color += transmittance * alpha * cloudToneMap(radiance);
        transmittance *= 1.0 - alpha;
        if (transmittance < 0.01) break;
    }
    float alpha = 1.0 - transmittance;
    if (alpha <= 0.0) discard;
    gl_FragColor = vec4(color, alpha);
}
