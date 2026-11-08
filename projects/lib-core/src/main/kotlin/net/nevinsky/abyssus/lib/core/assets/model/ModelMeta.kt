package net.nevinsky.abyssus.lib.core.assets.model

/**
 * A MODEL asset's settings. [parts] is keyed by node name and omitted from the file when null (see [ModelPartMeta]); the
 * editing helpers null an empty map.
 */
data class ModelMeta(
    val file: String? = null,
    val format: Format? = null,
    val binary: Boolean = false,
    val materials: List<String> = listOf(),
    val parts: Map<String, ModelPartMeta>? = null,
) {
    enum class Format {
        GLTF
    }
}

/** What the asset says about one node of the model: a [pivot] (new origin and axes) and/or a [hinge]. */
data class ModelPartMeta(val pivot: PartPivot? = null, val hinge: PartHinge? = null)

/** A pivot frame relative to the node's own frame in the model file; a missing member means identity. */
data class PartPivot(val position: PartVec3? = null, val rotation: PartQuat? = null)

/** Minimum and maximum deflection in degrees about the pivot's X axis; `min <= 0 <= max`, within +-180. */
data class PartHinge(val min: Float, val max: Float)

data class PartVec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

data class PartQuat(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f, val w: Float = 1f)
