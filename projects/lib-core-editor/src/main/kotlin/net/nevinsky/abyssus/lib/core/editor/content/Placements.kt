package net.nevinsky.abyssus.lib.gdx.editor.content

import net.nevinsky.abyssus.lib.core.dto.LIGHT_CONE_ANGLE
import net.nevinsky.abyssus.lib.core.dto.LIGHT_EDGE_SOFTNESS
import net.nevinsky.abyssus.lib.core.dto.LIGHT_RANGE
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FAR
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FOV
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_NEAR

data class Vec3(val x: Float, val y: Float, val z: Float)

/** An entity's simulated position and rotation; its authored scale is kept. */
data class Pose(val position: Vec3, val rotation: Quat)

data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float)

/** [w] is 1 for the identity rotation, which native scenes leave out of the file together with the other default fields. */
data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)
    }
}

/** Native `PositionComponent`: position 0, identity rotation and unit scale unless the file says otherwise. */
data class PlacementTransform(val position: Vec3, val rotation: Quat, val scale: Vec3) {
    companion object {
        val IDENTITY = PlacementTransform(Vec3(0f, 0f, 0f), Quat.IDENTITY, Vec3(1f, 1f, 1f))
    }
}

/** An entity showing the asset folder [assetName]. [entityId] is its key under `ecs/entities`; picking reports it back. */
data class AssetPlacement(val entityId: String, val assetName: String, val transform: PlacementTransform)

enum class LightKind { DIRECTIONAL, POINT, SPOT }

/** [direction] is the unit vector the light shines along (directional and spot); [position] matters for point and spot. */
data class LightPlacement(
    val entityId: String,
    val kind: LightKind,
    val color: Rgba,
    val intensity: Float,
    val position: Vec3,
    val direction: Vec3,
    val range: Float = LIGHT_RANGE,
    val rotation: Quat = Quat.IDENTITY,
    val coneAngle: Float = LIGHT_CONE_ANGLE,
    val edgeSoftness: Float = LIGHT_EDGE_SOFTNESS,
    /** The `PositionComponent.lookAtId` of the light, or null when it does not name a target. */
    val lookAtId: String? = null,
)

/**
 * A camera entity. [direction] is the view direction from `viewPointPosition` (as in the file, not yet normalized);
 * it is overridden by the position of [lookAtId] while that resolves. [name] is the entity's `NameComponent` name,
 * or its id when unnamed.
 */
data class CameraPlacement(
    val entityId: String,
    val name: String,
    val position: Vec3,
    val direction: Vec3,
    val lookAtId: String?,
    val near: Float = CAMERA_NEAR,
    val far: Float = CAMERA_FAR,
    val fieldOfView: Float = CAMERA_FOV,
    val rotation: Quat = Quat.IDENTITY,
)

