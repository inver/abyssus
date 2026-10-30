// Copyright 2023-2026 Alexey Nevinsky
// SPDX-License-Identifier: Apache-2.0

// Abyssus-owned lighting radiance; never tone mapped.
// Single-scattering Rayleigh + Mie atmosphere (Nishita), ray-marched per pixel. World Y is up. Lengths are worked in
// kilometres: 32-bit floats cannot hold a planet radius in metres and a camera a few metres above it.
#ifdef GL_ES
precision highp float;
#endif
uniform vec3 u_sunDir;           // unit vector toward the sun
uniform float u_cameraHeight;    // metres above the ground
uniform float u_planetRadius;    // metres
uniform float u_atmosphereRadius;
uniform vec3 u_betaRayleigh;     // per metre
uniform float u_betaMie;
uniform float u_heightRayleigh;  // scale heights, metres
uniform float u_heightMie;
uniform float u_mieG;
uniform float u_sunIntensity;
varying vec3 v_dir;

const int PRIMARY = 16;
const int LIGHT = 8;
const float PI = 3.14159265;
const float KM = 1000.0;
const float SUN_ANGULAR_RADIUS = 0.00465;
const float GROUND_ALBEDO = 0.04;

// distances along d to the sphere of radius r around the origin; x > y when it is missed
vec2 raySphere(vec3 o, vec3 d, float r) {
    float b = dot(o, d);
    float h = b * b - (dot(o, o) - r * r);
    if (h < 0.0) return vec2(1.0, -1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

void main() {
    float R = u_planetRadius / KM;
    float Ra = u_atmosphereRadius / KM;
    float hR = u_heightRayleigh / KM;
    float hM = u_heightMie / KM;
    vec3 betaR = u_betaRayleigh * KM;
    float betaM = u_betaMie * KM;

    vec3 dir = normalize(v_dir);
    vec3 sun = normalize(u_sunDir);
    vec3 origin = vec3(0.0, R + u_cameraHeight / KM, 0.0);

    vec2 atmosphere = raySphere(origin, dir, Ra);
    vec2 planet = raySphere(origin, dir, R);
    bool ground = planet.x > 0.0 && planet.x < planet.y;
    float tMin = max(atmosphere.x, 0.0);
    float tMax = ground ? min(atmosphere.y, planet.x) : atmosphere.y;
    float segment = (tMax - tMin) / float(PRIMARY);

    float mu = dot(dir, sun);
    float g = u_mieG;
    float phaseR = 3.0 / (16.0 * PI) * (1.0 + mu * mu);
    float phaseM = 3.0 / (8.0 * PI) * ((1.0 - g * g) * (1.0 + mu * mu)) /
        ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));

    vec3 sumR = vec3(0.0);
    vec3 sumM = vec3(0.0);
    float depthR = 0.0;
    float depthM = 0.0;
    for (int i = 0; i < PRIMARY; i++) {
        vec3 p = origin + dir * (tMin + segment * (float(i) + 0.5));
        float height = length(p) - R;
        float dR = exp(-height / hR) * segment;
        float dM = exp(-height / hM) * segment;
        depthR += dR;
        depthM += dM;

        float lightStep = raySphere(p, sun, Ra).y / float(LIGHT);
        float lightR = 0.0;
        float lightM = 0.0;
        bool shadowed = false;
        for (int j = 0; j < LIGHT; j++) {
            float lh = length(p + sun * (lightStep * (float(j) + 0.5))) - R;
            if (lh < 0.0) {
                shadowed = true;
                break;
            }
            lightR += exp(-lh / hR) * lightStep;
            lightM += exp(-lh / hM) * lightStep;
        }
        if (!shadowed) {
            vec3 attenuation = exp(-(betaR * (depthR + lightR) + 1.1 * betaM * (depthM + lightM)));
            sumR += attenuation * dR;
            sumM += attenuation * dM;
        }
    }

    vec3 transmittance = exp(-(betaR * depthR + 1.1 * betaM * depthM));
    vec3 color = u_sunIntensity * (sumR * betaR * phaseR + sumM * betaM * phaseM);
    if (ground) {
        // a dim, sunlit ground so the half below the horizon is neither empty nor brighter than the sky
        vec3 sunAtGround = exp(-(betaR * hR + 1.1 * betaM * hM) / max(sun.y, 0.02));
        color += transmittance * sunAtGround * (u_sunIntensity * GROUND_ALBEDO * max(sun.y, 0.0));
    } else {
        float disc = smoothstep(cos(SUN_ANGULAR_RADIUS * 1.3), cos(SUN_ANGULAR_RADIUS), mu);
        color += transmittance * (disc * u_sunIntensity * 20.0);
    }
    gl_FragColor = vec4(color, 1.0);
}
