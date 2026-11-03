#ifdef GL_ES
precision mediump float;
#endif

uniform int u_hasSplat;
uniform sampler2D u_splat;
uniform int u_has_splatBase;
uniform sampler2D u_splatBase;
uniform int u_has_splatR;
uniform sampler2D u_splatR;
uniform int u_has_splatG;
uniform sampler2D u_splatG;
uniform int u_has_splatB;
uniform sampler2D u_splatB;
uniform int u_has_splatA;
uniform sampler2D u_splatA;

// the output transform's functions are always compiled in; u_exposure above 0 switches the program onto them
#define outputTransformFlag
#pragma include "output_transform.glsl"

uniform samplerCube u_irradiance; // a built sky's irradiance; replaces u_ambient when u_hasSky is 1
uniform int u_hasSky;
uniform samplerCube u_irradianceTo; // the sky light fading in; used while u_skyFade is above 0
uniform float u_skyFade; // 0 to 1: how far u_irradianceTo has replaced u_irradiance

uniform vec3 u_lightDirection; // toward the light
uniform vec3 u_lightColor;
uniform vec3 u_ambient;
uniform vec3 u_fogColor;

// the sun's shadow: packed depth in a map seen through u_shadowMatrix (FieldShadows)
uniform sampler2D u_shadowTexture;
uniform mat4 u_shadowMatrix;
uniform float u_shadowTexel;
uniform float u_shadowBias;

varying vec2 v_uv;
varying vec2 v_splatUv;
varying vec3 v_normal;
varying float v_fog;
varying vec3 v_worldPos;

float readShadow(vec2 uv) {
    vec4 c = texture2D(u_shadowTexture, uv);
    return c.r / 16581375.0 + c.g / 65025.0 + c.b / 255.0 + c.a;
}

// 1 where the sun reaches the point, 0 in full shadow: 2x2 bilinear comparisons, four times (soft edges)
float sunVisibility(float ndotl) {
    vec4 clip = u_shadowMatrix * vec4(v_worldPos, 1.0);
    vec3 ndc = clip.xyz / clip.w;
    if (any(greaterThan(abs(ndc), vec3(1.0)))) return 1.0;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    float depth = ndc.z * 0.5 + 0.5 - u_shadowBias * (1.0 + 4.0 * (1.0 - clamp(ndotl, 0.0, 1.0)));
    float visible = 0.0;
    for (int i = 0; i < 4; i++) {
        vec2 at = uv + vec2(i == 0 || i == 2 ? -0.75 : 0.75, i < 2 ? -0.75 : 0.75) * u_shadowTexel;
        vec2 pixel = at / u_shadowTexel - 0.5;
        vec2 f = fract(pixel);
        vec2 p = (floor(pixel) + 0.5) * u_shadowTexel;
        float a = step(depth, readShadow(p));
        float b = step(depth, readShadow(p + vec2(u_shadowTexel, 0.0)));
        float c = step(depth, readShadow(p + vec2(0.0, u_shadowTexel)));
        float d = step(depth, readShadow(p + vec2(u_shadowTexel)));
        visible += mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    }
    return visible * 0.25;
}

// a layer is authored as display colour: linear light needs it decoded, the old path uses it as it is
vec3 layer(sampler2D s, vec2 uv) {
    vec3 c = texture2D(s, uv).rgb;
    return u_exposure > 0.0 ? outputSrgbToLinear(c) : c;
}

void main() {
    // the base layer, then each splat channel mixed over it in turn (neutral grey without a base)
    vec3 color = u_has_splatBase == 1 ? layer(u_splatBase, v_uv) : (u_exposure > 0.0 ? outputSrgbToLinear(vec3(0.5)) : vec3(0.5));
    if (u_hasSplat == 1) {
        vec4 splat = texture2D(u_splat, v_splatUv);
        if (u_has_splatR == 1) color = mix(color, layer(u_splatR, v_uv), splat.r);
        if (u_has_splatG == 1) color = mix(color, layer(u_splatG, v_uv), splat.g);
        if (u_has_splatB == 1) color = mix(color, layer(u_splatB, v_uv), splat.b);
        if (u_has_splatA == 1) color = mix(color, layer(u_splatA, v_uv), splat.a);
    }
    vec3 normal = normalize(v_normal);
    float ndotl = dot(normal, u_lightDirection);
    float diffuse = max(ndotl, 0.0) * sunVisibility(ndotl);
    vec3 ambient = u_hasSky == 1 ? textureCube(u_irradiance, normal).rgb : u_ambient;
    if (u_hasSky == 1 && u_skyFade > 0.0) ambient = mix(ambient, textureCube(u_irradianceTo, normal).rgb, u_skyFade);
    vec3 lit = color * (ambient + u_lightColor * diffuse);
    if (u_exposure > 0.0) {
        gl_FragColor = vec4(outputTransform(mix(lit, outputSrgbToLinear(u_fogColor), v_fog)), 1.0);
    } else {
        gl_FragColor = vec4(mix(lit, u_fogColor, v_fog), 1.0);
    }
}
