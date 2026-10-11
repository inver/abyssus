// Copyright 2023-2026 Alexey Nevinsky
// SPDX-License-Identifier: Apache-2.0

// The cloud shadow map: for every texel, the share of the sun's light the clouds let through to the ground point
// above it. The GLSL twin of SunOcclusion.instant in core: the same coverage and column math (clouds_common.glsl),
// the same band path and extinction, the same floor. The CPU works out the parts that need doubles or a ray against
// the band spheres (the sun ray's horizontal shift to each band, its path through the band) and passes them in.
// Joined after clouds_common.glsl.
#ifdef GL_ES
precision highp float;
#endif
uniform vec2 u_origin;      // world x / z of the map's corner, metres
uniform float u_extent;     // metres across the map
uniform float u_mapSize;    // texels across the map
uniform float u_floor;      // the least share that passes the thickest clouds
// per band: (scale x, scale z, wind offset u, wind offset v)
uniform vec4 u_bandA[3];
// per band: (type seed, octaves, profile, coverage)
uniform vec4 u_bandB[3];
// per band: (density, path through the band in metres, sun ray shift x, shift z); a density of 0 is no band
uniform vec4 u_bandC[3];

void main() {
    vec2 xz = u_origin + gl_FragCoord.xy / u_mapSize * u_extent;
    float depth = 0.0;
    for (int i = 0; i < 3; i++) {
        vec4 a = u_bandA[i];
        vec4 b = u_bandB[i];
        vec4 c = u_bandC[i];
        if (c.x <= 0.0) continue;
        float coverage = cloudCoverage(xz + c.zw, a.xy, a.zw, int(b.x + 0.5), int(b.y + 0.5), b.w);
        depth += CLOUD_EXTINCTION * c.x * cloudColumn(int(b.z + 0.5), coverage) * c.y;
    }
    gl_FragColor = vec4(max(exp(-depth), u_floor), 0.0, 0.0, 1.0);
}
