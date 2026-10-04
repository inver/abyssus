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

uniform vec3 u_lightDirection; // toward the light
uniform vec3 u_lightColor;
uniform vec3 u_ambient;
uniform vec3 u_fogColor;

varying vec2 v_uv;
varying vec2 v_splatUv;
varying vec3 v_normal;
varying float v_fog;

void main() {
    // the base layer, then each splat channel mixed over it in turn (neutral grey without a base)
    vec3 color = u_has_splatBase == 1 ? texture2D(u_splatBase, v_uv).rgb : vec3(0.5);
    if (u_hasSplat == 1) {
        vec4 splat = texture2D(u_splat, v_splatUv);
        if (u_has_splatR == 1) color = mix(color, texture2D(u_splatR, v_uv).rgb, splat.r);
        if (u_has_splatG == 1) color = mix(color, texture2D(u_splatG, v_uv).rgb, splat.g);
        if (u_has_splatB == 1) color = mix(color, texture2D(u_splatB, v_uv).rgb, splat.b);
        if (u_has_splatA == 1) color = mix(color, texture2D(u_splatA, v_uv).rgb, splat.a);
    }
    float diffuse = max(dot(normalize(v_normal), u_lightDirection), 0.0);
    vec3 lit = color * (u_ambient + u_lightColor * diffuse);
    gl_FragColor = vec4(mix(lit, u_fogColor, v_fog), 1.0);
}
