// Copyright 2023-2026 Alexey Nevinsky
// SPDX-License-Identifier: Apache-2.0

// PBR (metallic-roughness) fragment shader. Pair it with default.vertex.glsl.
//
// Cook-Torrance GGX for directional and point lights, ambient from the environment ambient cubemap, or, with
// environmentLightFlag, image based lighting from an irradiance cube and a prefiltered specular cube. Light units and the color space (linear, no gamma/tone mapping) are the same as in the default shader,
// so a white dielectric lit head on looks the same in both. Spot lights are not implemented.

#if !defined(normalFlag)
// PBR needs normals: without them the surface is shaded as unlit base color
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

const float PI = 3.14159265359;
const float MIN_ROUGHNESS = 0.045;

#ifdef normalFlag
varying vec3 v_normal;
varying HIGH vec3 v_worldPos;
#endif //normalFlag

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

#ifdef emissiveTextureFlag
varying MED vec2 v_emissiveUV;
uniform sampler2D u_emissiveTexture;
#endif

#ifdef metallicRoughnessTextureFlag
varying MED vec2 v_metallicRoughnessUV;
uniform sampler2D u_metallicRoughnessTexture;
#endif

#ifdef occlusionTextureFlag
varying MED vec2 v_occlusionUV;
uniform sampler2D u_occlusionTexture;
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
// base color factor
uniform vec4 u_diffuseColor;
#endif

#ifdef emissiveColorFlag
uniform vec4 u_emissiveColor;
#endif

#ifdef metallicFactorFlag
uniform float u_metallicFactor;
#endif

#ifdef roughnessFactorFlag
uniform float u_roughnessFactor;
#endif

#ifdef lightingFlag

uniform vec4 u_cameraPosition;

#ifdef ambientCubemapFlag
uniform vec3 u_ambientCubemap[6];
#endif // ambientCubemapFlag

#ifdef environmentLightFlag
uniform samplerCube u_envIrradiance;
uniform samplerCube u_envSpecular;
uniform float u_envMaxLod;
#if __VERSION__ >= 130
#define textureCubeLodCompat textureLod
#else
#define textureCubeLodCompat textureCubeLod
#endif
#endif // environmentLightFlag

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

#ifdef shadowAtlasFlag
uniform sampler2D u_shadowAtlas;
uniform float u_shadowEnabled;
uniform vec2 u_shadowTexel;
uniform float u_shadowBias[16];
uniform mat4 u_shadowMatrices[16];
uniform vec4 u_shadowTiles[16];
uniform vec3 u_shadowPositions[16];
uniform float u_shadowFars[16];
uniform float u_dirShadowTile[2];
uniform float u_pointShadowTiles[30];
uniform float u_spotShadowTile[5];
float readPackedShadow(vec2 uv) {
    vec4 c = texture2D(u_shadowAtlas, uv);
    return c.r / 16581375.0 + c.g / 65025.0 + c.b / 255.0 + c.a;
}
// Compare at actual texel centers, compensating for the receiver plane's depth slope.
float shadowCompare(vec2 sampleUv, vec2 centerUv, float depth, vec2 gradient, vec2 lo, vec2 hi) {
    sampleUv = clamp(sampleUv, lo, hi);
    return step(depth + dot(gradient, sampleUv - centerUv), readPackedShadow(sampleUv));
}
float shadowBilinear(vec2 sampleUv, vec2 centerUv, float depth, vec2 gradient, vec2 lo, vec2 hi) {
    vec2 pixel = sampleUv / u_shadowTexel - 0.5;
    vec2 f = fract(pixel);
    vec2 p = (floor(pixel) + 0.5) * u_shadowTexel;
    float a = shadowCompare(p, centerUv, depth, gradient, lo, hi);
    float b = shadowCompare(p + vec2(u_shadowTexel.x, 0.0), centerUv, depth, gradient, lo, hi);
    float c = shadowCompare(p + vec2(0.0, u_shadowTexel.y), centerUv, depth, gradient, lo, hi);
    float d = shadowCompare(p + u_shadowTexel, centerUv, depth, gradient, lo, hi);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float shadowTileVisibility(float tileValue, vec3 worldPos, float radialDepth, float ndotl) {
    if (u_shadowEnabled < 0.5 || tileValue < 0.0) return 1.0;
    int tile = int(tileValue + 0.5);
    vec4 clip = u_shadowMatrices[tile] * vec4(worldPos, 1.0);
    vec3 ndc = clip.xyz / (abs(clip.w) < 1e-6 ? 1e-6 : clip.w);
    vec4 rect = u_shadowTiles[tile];
    vec2 uv = rect.xy + (ndc.xy * 0.5 + 0.5) * rect.zw;
    float compareDepth = radialDepth >= 0.0 ? radialDepth : ndc.z * 0.5 + 0.5;
    compareDepth -= u_shadowBias[tile] * (0.2 + 2.0 * (1.0 - clamp(ndotl, 0.0, 1.0)));
    vec2 dx = dFdx(uv), dy = dFdy(uv);
    float zx = dFdx(compareDepth), zy = dFdy(compareDepth);
    float determinant = dx.x * dy.y - dx.y * dy.x;
    vec2 gradient = vec2(0.0);
    if (abs(determinant) > 1e-12)
        gradient = vec2(dy.y * zx - dx.y * zy, dx.x * zy - dy.x * zx) / determinant;
    // Derivatives must be evaluated before a per-fragment coverage branch; otherwise neighboring
    // fragments outside the map leave undefined slopes and produce flickering border artifacts.
    if (abs(clip.w) < 1e-6 || any(greaterThan(abs(ndc.xy), vec2(1.0))) || ndc.z < -1.0 || ndc.z > 1.0) return 1.0;
    vec2 texel = u_shadowTexel;
    // Limit discontinuous derivatives at silhouettes/cube-face boundaries; retain a small numerical bias.
    gradient = clamp(gradient, vec2(-0.05) / texel, vec2(0.05) / texel);
    compareDepth -= max(0.000002, dot(abs(gradient), texel) * 0.02);
    vec2 lo = rect.xy + texel * 0.5;
    vec2 hi = rect.xy + rect.zw - texel * 0.5;
    float visible = 0.0;
    visible += shadowBilinear(uv + vec2(-texel.x, -texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(texel.x, -texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(-texel.x, texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    visible += shadowBilinear(uv + vec2(texel.x, texel.y) * 0.5, uv, compareDepth, gradient, lo, hi);
    return visible * 0.25;
}
float pointShadowVisibility(int lightIndex, vec3 worldPos, float ndotl) {
    float firstTile = u_pointShadowTiles[lightIndex * 6];
    if (u_shadowEnabled < 0.5 || firstTile < 0.0) return 1.0;
    int firstIndex = int(firstTile + 0.5);
    vec3 ray = worldPos - u_shadowPositions[firstIndex];
    vec3 a = abs(ray);
    int face = a.x >= a.y && a.x >= a.z ? (ray.x >= 0.0 ? 0 : 1) :
               (a.y >= a.z ? (ray.y >= 0.0 ? 2 : 3) : (ray.z >= 0.0 ? 4 : 5));
    float tile = u_pointShadowTiles[lightIndex * 6 + face];
    int ti = int(tile + 0.5);
    return shadowTileVisibility(tile, worldPos, length(worldPos - u_shadowPositions[ti]) / max(u_shadowFars[ti], 1e-4), ndotl);
}
#endif

// GGX normal distribution
float distributionGGX(float NdotH, float alpha)
{
	float a2 = alpha * alpha;
	float d = NdotH * NdotH * (a2 - 1.0) + 1.0;
	return a2 / (PI * d * d);
}

// height correlated Smith visibility term (includes the 1 / (4 NdotL NdotV) factor)
float visibilitySmith(float NdotL, float NdotV, float alpha)
{
	float a2 = alpha * alpha;
	float gv = NdotL * sqrt(NdotV * NdotV * (1.0 - a2) + a2);
	float gl = NdotV * sqrt(NdotL * NdotL * (1.0 - a2) + a2);
	return 0.5 / max(gv + gl, 1e-5);
}

vec3 fresnelSchlick(vec3 f0, float VdotH)
{
	return f0 + (1.0 - f0) * pow(clamp(1.0 - VdotH, 0.0, 1.0), 5.0);
}

// Direct light contribution. radiance is the light color already attenuated, in the units of the default shader.
vec3 shadeLight(vec3 radiance, vec3 L, vec3 N, vec3 V, float NdotV, vec3 diffuseColor, vec3 f0, float alpha)
{
	float NdotL = clamp(dot(N, L), 0.0, 1.0);
	if (NdotL <= 0.0) {
		return vec3(0.0);
	}
	vec3 H = normalize(L + V);
	float NdotH = clamp(dot(N, H), 0.0, 1.0);
	float VdotH = clamp(dot(V, H), 0.0, 1.0);
	vec3 F = fresnelSchlick(f0, VdotH);
	// diffuse has no 1/PI, like the default shader; the specular lobe is scaled by PI to match
	vec3 diffuse = (1.0 - F) * diffuseColor;
	vec3 specular = F * (distributionGGX(NdotH, alpha) * visibilitySmith(NdotL, NdotV, alpha) * PI);
	return (diffuse + specular) * radiance * NdotL;
}
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

	// base color
	#if defined(diffuseTextureFlag) && defined(diffuseColorFlag) && defined(colorFlag)
		vec4 baseColor = texture2D(u_diffuseTexture, v_diffuseUV) * u_diffuseColor * v_color;
	#elif defined(diffuseTextureFlag) && defined(diffuseColorFlag)
		vec4 baseColor = texture2D(u_diffuseTexture, v_diffuseUV) * u_diffuseColor;
	#elif defined(diffuseTextureFlag) && defined(colorFlag)
		vec4 baseColor = texture2D(u_diffuseTexture, v_diffuseUV) * v_color;
	#elif defined(diffuseTextureFlag)
		vec4 baseColor = texture2D(u_diffuseTexture, v_diffuseUV);
	#elif defined(diffuseColorFlag) && defined(colorFlag)
		vec4 baseColor = u_diffuseColor * v_color;
	#elif defined(diffuseColorFlag)
		vec4 baseColor = u_diffuseColor;
	#elif defined(colorFlag)
		vec4 baseColor = v_color;
	#else
		vec4 baseColor = vec4(1.0);
	#endif

	// metallic (B) and roughness (G) share one texture, like in glTF
	#ifdef metallicFactorFlag
		float metallic = u_metallicFactor;
	#else
		float metallic = 0.0;
	#endif
	#ifdef roughnessFactorFlag
		float roughness = u_roughnessFactor;
	#else
		float roughness = 1.0;
	#endif
	#ifdef metallicRoughnessTextureFlag
		vec3 metallicRoughness = texture2D(u_metallicRoughnessTexture, v_metallicRoughnessUV).rgb;
		roughness *= metallicRoughness.g;
		metallic *= metallicRoughness.b;
	#endif
	metallic = clamp(metallic, 0.0, 1.0);
	roughness = clamp(roughness, MIN_ROUGHNESS, 1.0);

	#if defined(emissiveTextureFlag) && defined(emissiveColorFlag)
		vec3 emissive = texture2D(u_emissiveTexture, v_emissiveUV).rgb * u_emissiveColor.rgb;
	#elif defined(emissiveTextureFlag)
		vec3 emissive = texture2D(u_emissiveTexture, v_emissiveUV).rgb;
	#elif defined(emissiveColorFlag)
		vec3 emissive = u_emissiveColor.rgb;
	#else
		vec3 emissive = vec3(0.0);
	#endif

	#ifdef occlusionTextureFlag
		float occlusion = texture2D(u_occlusionTexture, v_occlusionUV).r;
	#else
		float occlusion = 1.0;
	#endif

	#if (!defined(lightingFlag))
		gl_FragColor.rgb = baseColor.rgb + emissive;
	#else
		vec3 V = normalize(u_cameraPosition.xyz - v_worldPos);
		float NdotV = clamp(dot(normal, V), 1e-4, 1.0);
		float alpha = roughness * roughness;
		vec3 f0 = mix(vec3(0.04), baseColor.rgb, metallic);
		vec3 diffuseColor = baseColor.rgb * (1.0 - metallic);

		vec3 direct = vec3(0.0);

		#if numDirectionalLights > 0
			for (int i = 0; i < numDirectionalLights; i++) {
				float visibility = 1.0;
				#ifdef shadowAtlasFlag
					visibility = shadowTileVisibility(u_dirShadowTile[i], v_worldPos, -1.0, max(dot(normal, -u_dirLights[i].direction), 0.0));
				#endif
				direct += visibility * shadeLight(u_dirLights[i].color, -u_dirLights[i].direction, normal, V, NdotV, diffuseColor, f0, alpha);
			}
		#endif // numDirectionalLights

		#if numPointLights > 0
			for (int i = 0; i < numPointLights; i++) {
                if (u_pointLights[i].intensity <= 0.0) continue;
				vec3 toLight = u_pointLights[i].position - v_worldPos;
				float dist2 = dot(toLight, toLight);
				vec3 L = toLight * inversesqrt(max(dist2, 1e-8));
				float visibility = 1.0;
				#ifdef shadowAtlasFlag
					visibility = pointShadowVisibility(i, v_worldPos, max(dot(normal, L), 0.0));
				#endif
				direct += visibility * shadeLight(u_pointLights[i].color * (1.0 - smoothstep(0.75 * u_pointLights[i].intensity, u_pointLights[i].intensity, sqrt(dist2))) / (1.0 + dist2), L, normal, V, NdotV, diffuseColor, f0, alpha);
			}
		#endif // numPointLights
        #if numSpotLights > 0
            for (int i = 0; i < numSpotLights; i++) {
                if (u_spotLights[i].intensity <= 0.0) continue;
                vec3 toLight = u_spotLights[i].position - v_worldPos;
                float dist2 = dot(toLight, toLight);
                vec3 L = toLight * inversesqrt(max(dist2, 1e-8));
                float cutoff = 1.0 - smoothstep(0.75 * u_spotLights[i].intensity, u_spotLights[i].intensity, sqrt(dist2));
                float visibility = 1.0;
                #ifdef shadowAtlasFlag
                    visibility = shadowTileVisibility(u_spotShadowTile[i], v_worldPos, -1.0, max(dot(normal, L), 0.0));
                #endif
                direct += visibility * shadeLight(u_spotLights[i].color * cutoff * spotCone(u_spotLights[i], L) / (1.0 + dist2), L,
                    normal, V, NdotV, diffuseColor, f0, alpha);
            }
        #endif


		#ifdef shadowMapFlag
			#ifndef shadowAtlasFlag
			direct *= getShadow();
			#endif
		#endif //shadowMapFlag

		// ambient: diffuse part plus a split sum style environment BRDF approximation for the specular part
		vec3 ambient = vec3(0.0);
		#ifdef ambientCubemapFlag
			vec3 squaredNormal = normal * normal;
			vec3 isPositive = step(0.0, normal);
			ambient = squaredNormal.x * mix(u_ambientCubemap[0], u_ambientCubemap[1], isPositive.x) +
					squaredNormal.y * mix(u_ambientCubemap[2], u_ambientCubemap[3], isPositive.y) +
					squaredNormal.z * mix(u_ambientCubemap[4], u_ambientCubemap[5], isPositive.z);
		#endif // ambientCubemapFlag
		const vec4 c0 = vec4(-1.0, -0.0275, -0.572, 0.022);
		const vec4 c1 = vec4(1.0, 0.0425, 1.04, -0.04);
		vec4 r = roughness * c0 + c1;
		float a004 = min(r.x * r.x, exp2(-9.28 * NdotV)) * r.x + r.y;
		vec2 envBrdf = vec2(-1.04, 1.04) * a004 + r.zw;
		#ifdef environmentLightFlag
			// the sky replaces the ambient: diffuse by normal, specular by reflection at the roughness's mip
			ambient = textureCube(u_envIrradiance, normal).rgb;
			vec3 prefiltered = textureCubeLodCompat(u_envSpecular, reflect(-V, normal), roughness * u_envMaxLod).rgb;
			vec3 ambientSpecular = prefiltered * (f0 * envBrdf.x + envBrdf.y);
		#else
			vec3 ambientSpecular = ambient * (f0 * envBrdf.x + envBrdf.y);
		#endif // environmentLightFlag

		gl_FragColor.rgb = direct + (ambient * diffuseColor + ambientSpecular) * occlusion + emissive;
	#endif //lightingFlag

	#ifdef fogFlag
		gl_FragColor.rgb = mix(gl_FragColor.rgb, u_fogColor.rgb, v_fog);
	#endif // end fogFlag

	float surfaceAlpha = baseColor.a;
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
