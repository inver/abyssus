package net.nevinsky.abyssus.lib.core.editor.content

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.assets.ModelKey
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FAR
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FOV
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_NEAR
import net.nevinsky.abyssus.lib.core.ecs.component.PathArrangementComponent

/** A [Color] whose components are not clamped to 0..1: light colors are scaled by intensity and can exceed 1. */
fun unclampedColor(r: Float, g: Float, b: Float, a: Float): Color =
    net.nevinsky.abyssus.lib.core.light.unclampedColor(r, g, b, a)

data class Pose(val position: Vector3, val rotation: Quaternion)

/** Native `PositionComponent`: position 0, identity rotation and unit scale unless the file says otherwise. */
data class PlacementTransform(val position: Vector3, val rotation: Quaternion, val scale: Vector3) {
    companion object {
        val IDENTITY = PlacementTransform(Vector3(0f, 0f, 0f), Quaternion(), Vector3(1f, 1f, 1f))
    }
}

/**
 * An entity showing the asset folder [assetName]. [entityId] is its key under `ecs/entities`; picking reports it back.
 */
data class AssetPlacement(
    val entityId: Long,
    val assetName: String,
    val transform: PlacementTransform,
    /** The one mesh of a model to draw, or null for the whole model. */
    val meshId: String? = null,
)

/**
 * The name this placement's asset has in the asset storage: the asset folder, or for one mesh of a model its encoded
 * `ModelKey`. Entities showing different meshes of one model need different built models.
 */
val AssetPlacement.storageName: String
    get() = ModelKey(assetName, meshId).encode()

/** A path arrangement entity: its [arrangement] component as the file holds it (read, never changed, by the view). */
class ArrangementPlacement(val entityId: Long, val arrangement: PathArrangementComponent)

/** The kind of a light entity; the type lives in `lib-core`, which games share with the editor. */
typealias LightKind = net.nevinsky.abyssus.lib.core.light.LightKind

/** A light entity as renderers read it; the type lives in `lib-core`, which games share with the editor. */
typealias LightPlacement = net.nevinsky.abyssus.lib.core.light.LightPlacement

/**
 * A camera entity. [direction] is the view direction from `viewPointPosition` (as in the file, not yet normalized); it
 * is overridden by the position of [lookAtId] while that resolves. [name] is the entity's `NameComponent` name, or its
 * id when unnamed.
 */
data class CameraPlacement(
    val entityId: Long,
    val name: String,
    val position: Vector3,
    val direction: Vector3,
    val lookAtId: Long?,
    val near: Float = CAMERA_NEAR,
    val far: Float = CAMERA_FAR,
    val fieldOfView: Float = CAMERA_FOV,
    val rotation: Quaternion = Quaternion(),
)
