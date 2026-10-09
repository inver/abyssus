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

// Layered clouds: one band as a flat layer where the view ray meets its density centre, 2D coverage only. Needs
// clouds_common.glsl and clouds_light.glsl before it.
varying vec3 v_dir;

void main() {
    vec3 d = normalize(v_dir);
    float mid = mix(u_bandBase, u_bandTop, cloudCentre(u_bandProfile));
    float t = cloudSphere(d, mid);
    if (t <= 0.0 || cloudBehindGround(d, t)) discard;
    vec3 up = cloudUp(d, t);
    float footprint = cloudFootprint(d, t, up);
    float coverage = cloudBandCoverageLod(d.xz * t, footprint);
    float density = u_bandDensity * cloudColumn(u_bandProfile, coverage);
    if (density <= 0.0) discard;
    float thickness = u_bandTop - u_bandBase;
    float tau = CLOUD_EXTINCTION * density * cloudSlant(d, up, thickness);
    float alpha = (1.0 - exp(-tau)) * cloudDistanceFade(t) * cloudFootprintFade(footprint);
    float sunTau = CLOUD_EXTINCTION * density * 0.5 * cloudSlant(normalize(u_sunDir), up, thickness);
    float skyTau = CLOUD_EXTINCTION * density * thickness * 0.5;
    vec3 radiance = cloudAerial(cloudRadiance(d, sunTau, skyTau, 0.5, up), t);
    gl_FragColor = vec4(cloudToneMap(radiance) * alpha, alpha);
}
