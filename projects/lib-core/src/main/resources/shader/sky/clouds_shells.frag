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

// Shell clouds: one band as CLOUD_SHELLS stacked layers from its base to its top, composited front to back, each lit
// through the cloud above it (darker bases) and seen where the view ray crosses its own altitude (parallax). Needs
// clouds_common.glsl and clouds_light.glsl before it.
varying vec3 v_dir;

const int CLOUD_SHELLS = 8;

void main() {
    vec3 d = normalize(v_dir);
    vec3 sun = normalize(u_sunDir);
    float thickness = u_bandTop - u_bandBase;
    float slice = thickness / float(CLOUD_SHELLS);
    vec3 color = vec3(0.0);
    float transmittance = 1.0;
    float dither = cloudDither(gl_FragCoord.xy);
    for (int i = 0; i < CLOUD_SHELLS; i++) {
        // from the base up: the viewer is below every band, so lower shells are nearer
        float h = (float(i) + 0.25 + 0.5 * dither) / float(CLOUD_SHELLS);
        float t = cloudSphere(d, u_bandBase + h * thickness);
        if (t <= 0.0 || cloudBehindGround(d, t)) continue;
        vec3 up = cloudUp(d, t);
        float footprint = cloudFootprint(d, t, up);
        float coverage = cloudBandCoverageLod(d.xz * t, footprint);
        float density = u_bandDensity * cloudShape(u_bandProfile, coverage, h);
        if (density <= 0.0) continue;
        float alpha = (1.0 - exp(-CLOUD_EXTINCTION * density * cloudSlant(d, up, slice))) * cloudDistanceFade(t) *
            cloudFootprintFade(footprint);
        float above = CLOUD_EXTINCTION * u_bandDensity * coverage * (1.0 - h) * thickness;
        float sunTau = above / max(dot(sun, up), 0.05);
        vec3 radiance = cloudAerial(cloudRadiance(d, sunTau, above, 1.0 - h, up), t);
        color += transmittance * alpha * cloudToneMap(radiance);
        transmittance *= 1.0 - alpha;
    }
    float alpha = 1.0 - transmittance;
    if (alpha <= 0.0) discard;
    gl_FragColor = vec4(color, alpha);
}
