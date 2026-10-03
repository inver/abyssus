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

// Default vertex shader. Lighting is evaluated per pixel in default.fragment.glsl, this shader only prepares the data.

#if defined(lightingFlag) && !defined(normalFlag)
// lighting needs normals
#undef lightingFlag
#endif

#if defined(diffuseTextureFlag) || defined(specularTextureFlag) || defined(emissiveTextureFlag) || defined(normalTextureFlag) || defined(metallicRoughnessTextureFlag) || defined(occlusionTextureFlag)
#define textureFlag
#endif

#if defined(specularTextureFlag) || defined(specularColorFlag)
#define specularFlag
#endif

#if defined(fogFlag)
#define cameraPositionFlag
#endif

attribute vec3 a_position;
uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;

#if defined(colorFlag)
varying vec4 v_color;
attribute vec4 a_color;
#endif // colorFlag

#ifdef normalFlag
attribute vec3 a_normal;
uniform mat3 u_normalMatrix;
varying vec3 v_normal;

#if defined(lightingFlag) || defined(normalTextureFlag)
#define worldPositionFlag
varying vec3 v_worldPos;
#endif

#if defined(normalTextureFlag) && defined(tangentFlag)
attribute vec3 a_tangent;
varying vec3 v_tangent;
#if defined(binormalFlag)
attribute vec3 a_binormal;
varying vec3 v_binormal;
#endif
#endif
#endif // normalFlag

#ifdef textureFlag
attribute vec2 a_texCoord0;
#endif // textureFlag

#ifdef diffuseTextureFlag
uniform vec4 u_diffuseUVTransform;
varying vec2 v_diffuseUV;
#endif

#ifdef emissiveTextureFlag
uniform vec4 u_emissiveUVTransform;
varying vec2 v_emissiveUV;
#endif

#ifdef specularTextureFlag
uniform vec4 u_specularUVTransform;
varying vec2 v_specularUV;
#endif

#ifdef normalTextureFlag
uniform vec4 u_normalUVTransform;
varying vec2 v_normalUV;
#endif

// only used by the PBR shader
#ifdef metallicRoughnessTextureFlag
uniform vec4 u_metallicRoughnessUVTransform;
varying vec2 v_metallicRoughnessUV;
#endif

#ifdef occlusionTextureFlag
uniform vec4 u_occlusionUVTransform;
varying vec2 v_occlusionUV;
#endif

#ifdef boneWeight0Flag
#define boneWeightsFlag
attribute vec2 a_boneWeight0;
#endif //boneWeight0Flag

#ifdef boneWeight1Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight1;
#endif //boneWeight1Flag

#ifdef boneWeight2Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight2;
#endif //boneWeight2Flag

#ifdef boneWeight3Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight3;
#endif //boneWeight3Flag

#ifdef boneWeight4Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight4;
#endif //boneWeight4Flag

#ifdef boneWeight5Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight5;
#endif //boneWeight5Flag

#ifdef boneWeight6Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight6;
#endif //boneWeight6Flag

#ifdef boneWeight7Flag
#ifndef boneWeightsFlag
#define boneWeightsFlag
#endif
attribute vec2 a_boneWeight7;
#endif //boneWeight7Flag

#if defined(numBones) && defined(boneWeightsFlag)
#if (numBones > 0)
#define skinningFlag
#endif
#endif

#if defined(numBones)
#if numBones > 0
uniform mat4 u_bones[numBones];
#endif //numBones
#endif

#ifdef blendedFlag
uniform float u_opacity;
varying float v_opacity;
#endif // blendedFlag

#ifdef alphaTestFlag
uniform float u_alphaTest;
varying float v_alphaTest;
#endif //alphaTestFlag

#ifdef cameraPositionFlag
uniform vec4 u_cameraPosition;
#endif // cameraPositionFlag

#ifdef fogFlag
varying float v_fog;
#endif // fogFlag

#if defined(lightingFlag) && defined(shadowMapFlag)
uniform mat4 u_shadowMapProjViewTrans;
varying vec3 v_shadowMapUv;
#endif //shadowMapFlag

void main() {
	#ifdef diffuseTextureFlag
		v_diffuseUV = u_diffuseUVTransform.xy + a_texCoord0 * u_diffuseUVTransform.zw;
	#endif //diffuseTextureFlag

	#ifdef emissiveTextureFlag
		v_emissiveUV = u_emissiveUVTransform.xy + a_texCoord0 * u_emissiveUVTransform.zw;
	#endif //emissiveTextureFlag

	#ifdef specularTextureFlag
		v_specularUV = u_specularUVTransform.xy + a_texCoord0 * u_specularUVTransform.zw;
	#endif //specularTextureFlag

	#ifdef normalTextureFlag
		v_normalUV = u_normalUVTransform.xy + a_texCoord0 * u_normalUVTransform.zw;
	#endif //normalTextureFlag

	#ifdef metallicRoughnessTextureFlag
		v_metallicRoughnessUV = u_metallicRoughnessUVTransform.xy + a_texCoord0 * u_metallicRoughnessUVTransform.zw;
	#endif //metallicRoughnessTextureFlag

	#ifdef occlusionTextureFlag
		v_occlusionUV = u_occlusionUVTransform.xy + a_texCoord0 * u_occlusionUVTransform.zw;
	#endif //occlusionTextureFlag

	#if defined(colorFlag)
		v_color = a_color;
	#endif // colorFlag

	#ifdef blendedFlag
		v_opacity = u_opacity;
	#endif // blendedFlag
	#ifdef alphaTestFlag
		v_alphaTest = u_alphaTest;
	#endif //alphaTestFlag

	#ifdef skinningFlag
		mat4 skinning = mat4(0.0);
		#ifdef boneWeight0Flag
			skinning += (a_boneWeight0.y) * u_bones[int(a_boneWeight0.x)];
		#endif //boneWeight0Flag
		#ifdef boneWeight1Flag
			skinning += (a_boneWeight1.y) * u_bones[int(a_boneWeight1.x)];
		#endif //boneWeight1Flag
		#ifdef boneWeight2Flag
			skinning += (a_boneWeight2.y) * u_bones[int(a_boneWeight2.x)];
		#endif //boneWeight2Flag
		#ifdef boneWeight3Flag
			skinning += (a_boneWeight3.y) * u_bones[int(a_boneWeight3.x)];
		#endif //boneWeight3Flag
		#ifdef boneWeight4Flag
			skinning += (a_boneWeight4.y) * u_bones[int(a_boneWeight4.x)];
		#endif //boneWeight4Flag
		#ifdef boneWeight5Flag
			skinning += (a_boneWeight5.y) * u_bones[int(a_boneWeight5.x)];
		#endif //boneWeight5Flag
		#ifdef boneWeight6Flag
			skinning += (a_boneWeight6.y) * u_bones[int(a_boneWeight6.x)];
		#endif //boneWeight6Flag
		#ifdef boneWeight7Flag
			skinning += (a_boneWeight7.y) * u_bones[int(a_boneWeight7.x)];
		#endif //boneWeight7Flag
	#endif //skinningFlag

	#ifdef skinningFlag
		vec4 pos = u_worldTrans * skinning * vec4(a_position, 1.0);
	#else
		vec4 pos = u_worldTrans * vec4(a_position, 1.0);
	#endif

	gl_Position = u_projViewTrans * pos;

	#if defined(lightingFlag) && defined(shadowMapFlag)
		vec4 spos = u_shadowMapProjViewTrans * pos;
		v_shadowMapUv.xyz = (spos.xyz / spos.w) * 0.5 + 0.5;
		v_shadowMapUv.z = min(v_shadowMapUv.z, 0.998);
	#endif //shadowMapFlag

	#if defined(normalFlag)
		#if defined(skinningFlag)
			v_normal = normalize((u_worldTrans * skinning * vec4(a_normal, 0.0)).xyz);
		#else
			v_normal = normalize(u_normalMatrix * a_normal);
		#endif

		#ifdef worldPositionFlag
			v_worldPos = pos.xyz;
		#endif

		#if defined(normalTextureFlag) && defined(tangentFlag)
			#if defined(skinningFlag)
				v_tangent = normalize((u_worldTrans * skinning * vec4(a_tangent, 0.0)).xyz);
				#if defined(binormalFlag)
					v_binormal = normalize((u_worldTrans * skinning * vec4(a_binormal, 0.0)).xyz);
				#endif
			#else
				v_tangent = normalize((u_worldTrans * vec4(a_tangent, 0.0)).xyz);
				#if defined(binormalFlag)
					v_binormal = normalize((u_worldTrans * vec4(a_binormal, 0.0)).xyz);
				#endif
			#endif
		#endif
	#endif // normalFlag

	#ifdef fogFlag
		vec3 flen = u_cameraPosition.xyz - pos.xyz;
		float fog = dot(flen, flen) * u_cameraPosition.w;
		v_fog = min(fog, 1.0);
	#endif
}
