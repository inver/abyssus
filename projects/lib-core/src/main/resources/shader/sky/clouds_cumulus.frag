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

// Cumulus clouds: one band as a flat layer with a heaped look, from a single coverage sample plus three more taps. A
// finer noise erodes the edge (ragged rims), the coverage value is the dome (thick centre, thin rim), and the taps
// toward the sun tell which side of a puff faces it: that side thins and shines, the other side and the base go grey.
// Needs clouds_common.glsl and clouds_light.glsl before it.
varying vec3 v_dir;

const float CUMULUS_ERODE = 0.9;        // how far the fine noise moves the edge, in coverage
const float CUMULUS_DETAIL_SCALE = 6.0; // fine noise cells per coverage cell
const float CUMULUS_SUN_TAP = 0.12;     // coverage cells toward the sun the lit-side tap reaches
const float CUMULUS_OPACITY = 4.0;      // how fast a puff goes opaque toward its centre

// Coverage at noise-space point (u, v) with the edge eroded by fine noise, 0..1: ragged where the cloud ends.
float cumulusDensity(vec2 xz, float footprint) {
    float c = cloudBandCoverageLod(xz, footprint);
    float u = xz.x / u_bandScale.x + u_bandOffset.x;
    float v = xz.y / u_bandScale.y + u_bandOffset.y;
    float fine = cloudFbmLod(u * CUMULUS_DETAIL_SCALE, v * CUMULUS_DETAIL_SCALE, u_bandSeed + 5, 3,
                             footprint * CUMULUS_DETAIL_SCALE);
    return smoothstep(0.08, 0.7, c + (fine - 0.5) * CUMULUS_ERODE * (1.0 - c));
}

void main() {
    vec3 d = normalize(v_dir);
    float mid = mix(u_bandBase, u_bandTop, cloudCentre(u_bandProfile));
    float t = cloudSphere(d, mid);
    if (t <= 0.0 || cloudBehindGround(d, t)) discard;
    vec3 up = cloudUp(d, t);
    float footprint = cloudFootprint(d, t, up);
    vec2 xz = d.xz * t;
    float dome = cumulusDensity(xz, footprint);
    if (dome <= 0.0) discard;

    vec3 sun = normalize(u_sunDir);
    vec2 toSun = normalize(sun.xz + vec2(1e-4)) * (CUMULUS_SUN_TAP * u_bandScale.y);
    float towardSun = cumulusDensity(xz + toSun, footprint);
    // 1 where the puff slopes down toward the sun (this side faces it), 0 where it climbs away from it
    float lit = clamp(0.5 + (dome - towardSun) * 3.0, 0.0, 1.0);

    float density = u_bandDensity;
    float thickness = u_bandTop - u_bandBase;
    float slant = cloudSlant(d, up, thickness);
    float tau = CLOUD_EXTINCTION * density * slant * dome * CUMULUS_OPACITY;
    float alpha = (1.0 - exp(-tau)) * cloudDistanceFade(t) * cloudFootprintFade(footprint);
    float sunTau = CLOUD_EXTINCTION * density * 0.5 * cloudSlant(sun, up, thickness) * dome * mix(1.1, 0.1, lit);
    float skyTau = CLOUD_EXTINCTION * density * thickness * 0.5 * dome;
    float underside = smoothstep(0.3, 1.0, dome) * 0.45 + (1.0 - lit) * 0.15;
    vec3 radiance = cloudAerial(cloudRadiance(d, sunTau, skyTau, underside, density * dome, up), t, d);
    gl_FragColor = vec4(cloudToneMap(radiance) * alpha, alpha);
}
