// Copyright 2023-2026 Alexey Nevinsky
// SPDX-License-Identifier: Apache-2.0

// Default fragment shader: per-pixel Blinn-Phong lighting with optional tangent space normal mapping.
// Spot beams and atlas visibility are evaluated independently for each direct light.

#if defined(lightingFlag) && !defined(normalFlag)
// lighting needs normals
#undef lightingFlag
#endif

#ifdef GL_ES
#define LOWP lowp
#define MED mediump
#ifdef GL_FRAGMENT_PRECISION_HIGH
#define HIGH highp
precision highp float;
#else
#define HIGH mediump
precision mediump float;
#endif
#else
#define MED
#define LOWP
#define HIGH
#endif

#if defined(normalTextureFlag) && defined(normalFlag) && !defined(tangentFlag)
// no tangents in the mesh: the tangent frame is derived from screen space derivatives
#ifdef GL_ES
#extension GL_OES_standard_derivatives : enable
#endif
#endif

#if defined(specularTextureFlag) || defined(specularColorFlag)
#define specularFlag
#endif

#ifdef normalFlag
varying vec3 v_normal;
#if defined(lightingFlag) || defined(normalTextureFlag)
varying HIGH vec3 v_worldPos;
#endif
#endif //normalFlag

#ifdef clipPlaneFlag
uniform vec4 u_clipPlane; // a, b, c, d: fragments with a x + b y + c z + d < 0 are discarded
#if !(defined(normalFlag) && (defined(lightingFlag) || defined(normalTextureFlag)))
varying HIGH vec3 v_worldPos;
#endif
#endif //clipPlaneFlag

#if defined(colorFlag)
varying vec4 v_color;
#endif

#ifdef blendedFlag
varying float v_opacity;
#endif //blendedFlag
#ifdef alphaTestFlag
varying float v_alphaTest;
#endif //alphaTestFlag

#ifdef diffuseTextureFlag
varying MED vec2 v_diffuseUV;
uniform sampler2D u_diffuseTexture;
#endif

#ifdef specularTextureFlag
varying MED vec2 v_specularUV;
uniform sampler2D u_specularTexture;
#endif

#ifdef emissiveTextureFlag
varying MED vec2 v_emissiveUV;
uniform sampler2D u_emissiveTexture;
#endif

#if defined(normalTextureFlag) && defined(normalFlag)
varying MED vec2 v_normalUV;
uniform sampler2D u_normalTexture;
#ifdef tangentFlag
varying vec3 v_tangent;
#ifdef binormalFlag
varying vec3 v_binormal;
#endif
#endif
#endif

#ifdef diffuseColorFlag
uniform vec4 u_diffuseColor;
#endif

#ifdef specularColorFlag
uniform vec4 u_specularColor;
#endif

#ifdef emissiveColorFlag
uniform vec4 u_emissiveColor;
#endif

#ifdef lightingFlag

#ifdef shininessFlag
uniform float u_shininess;
#else
const float u_shininess = 20.0;
#endif // shininessFlag

uniform vec4 u_cameraPosition;

#if defined(ambientLightFlag) || defined(ambientCubemapFlag) || defined(sphericalHarmonicsFlag)
#define ambientFlag
#endif //ambientFlag

#ifdef ambientLightFlag
uniform vec3 u_ambientLight;
#endif // ambientLightFlag

#ifdef ambientCubemapFlag
uniform vec3 u_ambientCubemap[6];
#endif // ambientCubemapFlag

#ifdef sphericalHarmonicsFlag
uniform vec3 u_sphericalHarmonics[9];
#endif //sphericalHarmonicsFlag

#if numDirectionalLights > 0
struct DirectionalLight
{
	vec3 color;
	vec3 direction;
};
uniform DirectionalLight u_dirLights[numDirectionalLights];
#endif // numDirectionalLights

#if numPointLights > 0
struct PointLight
{
	vec3 color;
	vec3 position;
	float intensity;
};
uniform PointLight u_pointLights[numPointLights];
#endif // numPointLights

#if numSpotLights > 0
struct SpotLight {
    vec3 color;
    vec3 position;
    vec3 direction;
    float intensity; // range; color is already scaled by the binder
    float cutoffAngle; // half angle in degrees
    float exponent; // inward edge softness fraction
};
uniform SpotLight u_spotLights[numSpotLights];
float spotCone(SpotLight light, vec3 L) {
    float outer = cos(radians(light.cutoffAngle));
    float inner = cos(radians(light.cutoffAngle * (1.0 - light.exponent)));
    float c = dot(normalize(light.direction), -L);
    return inner <= outer ? step(outer, c) : smoothstep(outer, inner, c);
}
#endif

#ifdef shadowMapFlag
uniform sampler2D u_shadowTexture;
uniform float u_shadowPCFOffset;
varying vec3 v_shadowMapUv;

float getShadowness(vec2 offset)
{
	const vec4 bitShifts = vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0);
	return step(v_shadowMapUv.z, dot(texture2D(u_shadowTexture, v_shadowMapUv.xy + offset), bitShifts));
}

float getShadow()
{
	return (getShadowness(vec2(u_shadowPCFOffset, u_shadowPCFOffset)) +
			getShadowness(vec2(-u_shadowPCFOffset, u_shadowPCFOffset)) +
			getShadowness(vec2(u_shadowPCFOffset, -u_shadowPCFOffset)) +
			getShadowness(vec2(-u_shadowPCFOffset, -u_shadowPCFOffset))) * 0.25;
}
#endif //shadowMapFlag

#pragma include "shadow_visibility.glsl"
#pragma include "output_transform.glsl"

#endif //lightingFlag

#ifdef fogFlag
uniform vec4 u_fogColor;
varying float v_fog;
#endif // fogFlag

#if defined(normalTextureFlag) && defined(normalFlag) && !defined(tangentFlag)
// Cotangent frame from screen space derivatives (http://www.thetenthplanet.de/archives/1180).
// V is mirrored: the loader flips V, the tangent attribute convention (+V up, OpenGL normal maps) needs it back.
mat3 cotangentFrame(vec3 n, vec3 p, vec2 uv)
{
	vec3 dp1 = dFdx(p);
	vec3 dp2 = dFdy(p);
	vec2 duv1 = dFdx(uv);
	vec2 duv2 = dFdy(uv);
	vec3 dp2perp = cross(dp2, n);
	vec3 dp1perp = cross(n, dp1);
	vec3 t = dp2perp * duv1.x + dp1perp * duv2.x;
	vec3 b = dp2perp * duv1.y + dp1perp * duv2.y;
	float invmax = inversesqrt(max(max(dot(t, t), dot(b, b)), 1e-12));
	return mat3(t * invmax, b * invmax, n);
}
#endif

void main() {
	#ifdef clipPlaneFlag
		if (dot(v_worldPos, u_clipPlane.xyz) + u_clipPlane.w < 0.0) discard;
	#endif
	#if defined(normalFlag)
		vec3 normal = normalize(v_normal);
		#if defined(normalTextureFlag)
			vec3 tangentNormal = texture2D(u_normalTexture, v_normalUV).xyz * 2.0 - 1.0;
			#if defined(tangentFlag)
				vec3 tangent = normalize(v_tangent);
				#if defined(binormalFlag)
					vec3 binormal = normalize(v_binormal);
				#else
					vec3 binormal = cross(normal, tangent);
				#endif
				normal = normalize(mat3(tangent, binormal, normal) * tangentNormal);
			#else
				normal = normalize(cotangentFrame(normal, v_worldPos, vec2(v_normalUV.x, -v_normalUV.y)) * tangentNormal);
			#endif
		#endif
	#endif // normalFlag

	#if defined(diffuseTextureFlag) && defined(diffuseColorFlag) && defined(colorFlag)
		vec4 diffuse = sampleColor(u_diffuseTexture, v_diffuseUV) * u_diffuseColor * v_color;
	#elif defined(diffuseTextureFlag) && defined(diffuseColorFlag)
		vec4 diffuse = sampleColor(u_diffuseTexture, v_diffuseUV) * u_diffuseColor;
	#elif defined(diffuseTextureFlag) && defined(colorFlag)
		vec4 diffuse = sampleColor(u_diffuseTexture, v_diffuseUV) * v_color;
	#elif defined(diffuseTextureFlag)
		vec4 diffuse = sampleColor(u_diffuseTexture, v_diffuseUV);
	#elif defined(diffuseColorFlag) && defined(colorFlag)
		vec4 diffuse = u_diffuseColor * v_color;
	#elif defined(diffuseColorFlag)
		vec4 diffuse = u_diffuseColor;
	#elif defined(colorFlag)
		vec4 diffuse = v_color;
	#else
		vec4 diffuse = vec4(1.0);
	#endif

	#if defined(emissiveTextureFlag) && defined(emissiveColorFlag)
		vec4 emissive = sampleColor(u_emissiveTexture, v_emissiveUV) * u_emissiveColor;
	#elif defined(emissiveTextureFlag)
		vec4 emissive = sampleColor(u_emissiveTexture, v_emissiveUV);
	#elif defined(emissiveColorFlag)
		vec4 emissive = u_emissiveColor;
	#else
		vec4 emissive = vec4(0.0);
	#endif

	#if (!defined(lightingFlag))
		gl_FragColor.rgb = diffuse.rgb + emissive.rgb;
	#else
		#ifdef ambientLightFlag
			vec3 ambientLight = u_ambientLight;
		#elif defined(ambientFlag)
			vec3 ambientLight = vec3(0.0);
		#else
			const vec3 ambientLight = vec3(0.0);
		#endif

		#ifdef ambientCubemapFlag
			vec3 squaredNormal = normal * normal;
			vec3 isPositive = step(0.0, normal);
			ambientLight += squaredNormal.x * mix(u_ambientCubemap[0], u_ambientCubemap[1], isPositive.x) +
					squaredNormal.y * mix(u_ambientCubemap[2], u_ambientCubemap[3], isPositive.y) +
					squaredNormal.z * mix(u_ambientCubemap[4], u_ambientCubemap[5], isPositive.z);
		#endif // ambientCubemapFlag

		#ifdef sphericalHarmonicsFlag
			ambientLight += u_sphericalHarmonics[0];
			ambientLight += u_sphericalHarmonics[1] * normal.x;
			ambientLight += u_sphericalHarmonics[2] * normal.y;
			ambientLight += u_sphericalHarmonics[3] * normal.z;
			ambientLight += u_sphericalHarmonics[4] * (normal.x * normal.z);
			ambientLight += u_sphericalHarmonics[5] * (normal.z * normal.y);
			ambientLight += u_sphericalHarmonics[6] * (normal.y * normal.x);
			ambientLight += u_sphericalHarmonics[7] * (3.0 * normal.z * normal.z - 1.0);
			ambientLight += u_sphericalHarmonics[8] * (normal.x * normal.x - normal.y * normal.y);
		#endif // sphericalHarmonicsFlag

		vec3 lightDiffuse = vec3(0.0);
		vec3 lightSpecular = vec3(0.0);
		vec3 viewVec = normalize(u_cameraPosition.xyz - v_worldPos);

		#if numDirectionalLights > 0
			for (int i = 0; i < numDirectionalLights; i++) {
				vec3 lightDir = -u_dirLights[i].direction;
				float NdotL = clamp(dot(normal, lightDir), 0.0, 1.0);
				vec3 value = u_dirLights[i].color * NdotL;
				#ifdef shadowAtlasFlag
					value *= shadowTileVisibility(u_dirShadowTile[i], v_worldPos, -1.0, NdotL);
				#endif
				#ifdef directionalVisibilityFlag
					if (i == u_dirVisIndex) value *= directionalVisibility(v_worldPos);
				#endif
				lightDiffuse += value;
				#ifdef specularFlag
					float halfDotView = max(0.0, dot(normal, normalize(lightDir + viewVec)));
					lightSpecular += value * pow(halfDotView, u_shininess);
				#endif // specularFlag
			}
		#endif // numDirectionalLights

		#if numPointLights > 0
			for (int i = 0; i < numPointLights; i++) {
                if (u_pointLights[i].intensity <= 0.0) continue;
				vec3 lightDir = u_pointLights[i].position - v_worldPos;
				float dist2 = dot(lightDir, lightDir);
				lightDir *= inversesqrt(max(dist2, 1e-8));
				float NdotL = clamp(dot(normal, lightDir), 0.0, 1.0);
				vec3 value = u_pointLights[i].color * (NdotL / (1.0 + dist2)) * (1.0 - smoothstep(0.75 * u_pointLights[i].intensity, u_pointLights[i].intensity, sqrt(dist2)));
				#ifdef shadowAtlasFlag
					value *= pointShadowVisibility(i, v_worldPos, NdotL);
				#endif
				lightDiffuse += value;
				#ifdef specularFlag
					float halfDotView = max(0.0, dot(normal, normalize(lightDir + viewVec)));
					lightSpecular += value * pow(halfDotView, u_shininess);
				#endif // specularFlag
			}
		#endif // numPointLights
        #if numSpotLights > 0
            for (int i = 0; i < numSpotLights; i++) {
                if (u_spotLights[i].intensity <= 0.0) continue;
                vec3 toLight = u_spotLights[i].position - v_worldPos;
                float dist2 = dot(toLight, toLight);
                vec3 L = toLight * inversesqrt(max(dist2, 1e-8));
                float cutoff = 1.0 - smoothstep(0.75 * u_spotLights[i].intensity, u_spotLights[i].intensity, sqrt(dist2));
                vec3 value = u_spotLights[i].color * (max(dot(normal, L), 0.0) / (1.0 + dist2)) * cutoff * spotCone(u_spotLights[i], L);
                #ifdef shadowAtlasFlag
                    value *= shadowTileVisibility(u_spotShadowTile[i], v_worldPos, -1.0, max(dot(normal, L), 0.0));
                #endif
                lightDiffuse += value;
                #ifdef specularFlag
                    float halfDotView = max(0.0, dot(normal, normalize(L + viewVec)));
                    lightSpecular += value * pow(halfDotView, u_shininess);
                #endif
            }
        #endif


		#ifdef shadowMapFlag
			#ifdef shadowAtlasFlag
				const float shadow = 1.0;
			#else
			float shadow = getShadow();
			#endif
		#else
			const float shadow = 1.0;
		#endif //shadowMapFlag

		#ifdef specularFlag
			#if defined(specularTextureFlag) && defined(specularColorFlag)
				vec3 specular = texture2D(u_specularTexture, v_specularUV).rgb * u_specularColor.rgb * lightSpecular;
			#elif defined(specularTextureFlag)
				vec3 specular = texture2D(u_specularTexture, v_specularUV).rgb * lightSpecular;
			#elif defined(specularColorFlag)
				vec3 specular = u_specularColor.rgb * lightSpecular;
			#else
				vec3 specular = lightSpecular;
			#endif
			gl_FragColor.rgb = diffuse.rgb * (ambientLight + shadow * lightDiffuse) + shadow * specular + emissive.rgb;
		#else
			gl_FragColor.rgb = diffuse.rgb * (ambientLight + shadow * lightDiffuse) + emissive.rgb;
		#endif
	#endif //lightingFlag

	#ifdef fogFlag
		#ifdef outputTransformFlag
			gl_FragColor.rgb = mix(gl_FragColor.rgb, outputSrgbToLinear(u_fogColor.rgb), v_fog);
		#else
			gl_FragColor.rgb = mix(gl_FragColor.rgb, u_fogColor.rgb, v_fog);
		#endif
	#endif // end fogFlag

	#ifdef outputTransformFlag
		gl_FragColor.rgb = outputTransform(gl_FragColor.rgb);
	#endif

	float surfaceAlpha = diffuse.a;
	#ifdef blendedFlag
		surfaceAlpha *= v_opacity;
	#endif
	#ifdef alphaTestFlag
		if (surfaceAlpha < v_alphaTest) discard;
	#endif
	#ifdef blendedFlag
		gl_FragColor.a = surfaceAlpha;
	#else
		gl_FragColor.a = 1.0;
	#endif
}
