/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.ray

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelColor
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelMaterial
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayModelMesh
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayTerrainSnapshot
import net.nevinsky.abyssus.lib.core.editor.ray.snapshot.RayTextureWrap
import net.nevinsky.abyssus.lib.raytracing.*

// Model→ray conversion rules shared by `RaySceneConverter` (incremental edits) and `RaySceneSnapshot` (full scene), so the
// PBR defaulting and the tangent handedness exist once.

/** The ray sampler wrap for a model texture wrap. */
internal fun RayTextureWrap.toRayWrap(): RayWrap = if (this == RayTextureWrap.REPEAT) RayWrap.REPEAT else RayWrap.CLAMP_TO_EDGE

internal fun convertMaterial(source: RayModelMaterial, images: Map<String, Int>): RayMaterial {
    fun binding(usage: Int) = source.textures.firstOrNull { it.usage == usage }?.let {
        RayTextureBinding(images.getValue(it.fileName), it.offsetU, it.offsetV, it.scaleU, it.scaleV)
    }

    fun RayModelColor?.toRay(default: RayColor) = this?.let { RayColor(it.r, it.g, it.b, it.a) } ?: default
    return RayMaterial(
        kind = if (source.pbr) RayMaterialKind.PBR else RayMaterialKind.DEFAULT,
        baseColor = (if (source.pbr) source.baseColor ?: source.diffuse else source.diffuse).toRay(
            RayColor(
                1f,
                1f,
                1f
            )
        ),
        emissive = source.emissive.toRay(
            if (binding(ModelTexture.USAGE_EMISSIVE) != null) RayColor(
                1f,
                1f,
                1f
            ) else RayColor(0f, 0f, 0f)
        ),
        specular = source.specular.toRay(
            if (binding(ModelTexture.USAGE_SPECULAR) != null) RayColor(
                1f,
                1f,
                1f
            ) else RayColor(0f, 0f, 0f)
        ),
        shininess = source.shininess.takeIf { it > 0f } ?: 20f,
        metallic = source.metallic ?: 0f,
        roughness = source.roughness ?: 1f,
        opacity = source.opacity,
        alphaMode = RayAlphaMode.valueOf(source.alphaMode.name),
        alphaCutoff = source.alphaCutoff,
        doubleSided = source.doubleSided,
        baseTexture = binding(ModelTexture.USAGE_DIFFUSE),
        emissiveTexture = binding(ModelTexture.USAGE_EMISSIVE),
        specularTexture = binding(ModelTexture.USAGE_SPECULAR),
        normalTexture = binding(ModelTexture.USAGE_NORMAL),
        metallicRoughnessTexture = binding(100),
        occlusionTexture = binding(101))
}

internal fun channelOrNull(
    mesh: RayModelMesh,
    values: FloatArray,
    stride: Int,
    usage: Int,
    components: Int
): FloatArray? =
    if (mesh.attributes.none { it.usage == usage && it.unit == 0 }) null else channel(
        mesh,
        values,
        stride,
        usage,
        components
    )

internal fun channel(mesh: RayModelMesh, values: FloatArray, stride: Int, usage: Int, components: Int): FloatArray {
    val attribute =
        requireNotNull(mesh.attributes.firstOrNull { it.usage == usage && it.unit == 0 }) { "Missing position channel" }
    require(attribute.type == GL20.GL_FLOAT && attribute.components >= components && attribute.offsetBytes % 4 == 0 && values.size % stride == 0) {
        "Unsupported vertex channel"
    }
    val offset = attribute.offsetBytes / 4
    return FloatArray(values.size / stride * components) { values[it / components * stride + offset + it % components] }
}

internal fun tangent(mesh: RayModelMesh, values: FloatArray, stride: Int, normals: FloatArray?): FloatArray? {
    val attribute = mesh.attributes.firstOrNull { it.usage == VertexAttributes.Usage.Tangent } ?: return null
    if (attribute.components == 4) return channel(mesh, values, stride, VertexAttributes.Usage.Tangent, 4)
    val xyz = channel(mesh, values, stride, VertexAttributes.Usage.Tangent, 3)
    val binormal = channelOrNull(mesh, values, stride, VertexAttributes.Usage.BiNormal, 3)
    return FloatArray(xyz.size / 3 * 4) { index ->
        val vertex = index / 4
        if (index % 4 != 3) xyz[vertex * 3 + index % 4]
        else if (normals == null || binormal == null) 1f
        else {
            val i = vertex * 3
            val handed = (normals[i + 1] * xyz[i + 2] - normals[i + 2] * xyz[i + 1]) * binormal[i] +
                    (normals[i + 2] * xyz[i] - normals[i] * xyz[i + 2]) * binormal[i + 1] +
                    (normals[i] * xyz[i + 1] - normals[i + 1] * xyz[i]) * binormal[i + 2]
            if (handed < 0f) -1f else 1f
        }
    }
}

/** A converted mesh and the bytes it holds, kept so an unchanged asset keeps its [RayMesh] identity. */
internal class CachedMesh(val mesh: RayMesh, val bytes: Long)

/**
 * The [RayMesh] [key] for one part of [source], with [values] as its vertices (the bind pose, or a deformed copy) and
 * [indices] as the part's triangles: position, normal, uv and tangent channels copied out of the interleaved buffer.
 */
internal fun rayMeshOf(source: RayModelMesh, values: FloatArray, indices: IntArray, key: String): CachedMesh {
    val stride = source.vertexSizeBytes / 4
    val position = channel(source, values, stride, VertexAttributes.Usage.Position, 3)
    val normal = channelOrNull(source, values, stride, VertexAttributes.Usage.Normal, 3)
    val uv = channelOrNull(source, values, stride, VertexAttributes.Usage.TextureCoordinates, 2)
    val tangent = tangent(source, values, stride, normal)
    val size = (position.size.toLong() + (normal?.size ?: 0) + (uv?.size ?: 0) + (tangent?.size ?: 0) + indices.size) * 4
    return CachedMesh(RayMesh(key, position, indices, normal, uv, tangent), size)
}

/** The [RayMesh] [key] of a terrain: its interleaved position, normal and uv vertices split into channels. */
internal fun rayTerrainMeshOf(asset: RayTerrainSnapshot, key: String): CachedMesh {
    val values = asset.vertices()
    val count = values.size / 8
    val positions = FloatArray(count * 3) { values[it / 3 * 8 + it % 3] }
    val normals = FloatArray(count * 3) { values[it / 3 * 8 + 3 + it % 3] }
    val uvs = FloatArray(count * 2) { values[it / 2 * 8 + 6 + it % 2] }
    val indices = asset.indices()
    return CachedMesh(RayMesh(key, positions, indices, normals, uvs), (positions.size.toLong() + normals.size + uvs.size + indices.size) * 4)
}
