/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.app.game.controlline.components.VitrineTableComponent
import net.nevinsky.abyssus.app.game.controlline.flow.PlaneChoice
import net.nevinsky.abyssus.app.game.controlline.vitrine.*
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ecs.component.assetName
import kotlin.math.cos
import kotlin.math.sin

/** The name of the hangar's table. */
private const val TABLE_NAME = "Table"

/** The height of the table-top above the table's origin when the hangar's table has no `VitrineTableComponent` (m). */
private const val DEFAULT_TOP_HEIGHT = 0.958f

/** A plane on the table is turned this far from the table's own heading (radians): side on, a little to the camera. */
private const val PLANE_TURN = -1.0f

/** Where a plane that is not a choice of the mode waits: far below the hangar. */
private const val OUT_OF_SIGHT = -1000f

/**
 * Plane select in the hangar scene: the chosen plane stands at the centre of the table, the others wait to either side
 * of it, and a plane chosen next slides to the centre (a [Carousel]). The planes are the [planes] of the field scene,
 * borrowed by [scene]; the renderer draws them at the poses of [poses].
 */
class HangarStage(
    val scene: FieldScene,
    private val planes: Map<Int, Entity>,
    private val modelPoints: (String) -> List<Vector3>?,
) {
    private val table: VitrineTable? =
        scene.named(TABLE_NAME)?.let { entity ->
            val at = scene.position(entity)
            val top = entity.getComponent(VitrineTableComponent::class.java)?.topHeight ?: DEFAULT_TOP_HEIGHT
            VitrineTable(at.localPosition.cpy(), at.localRotation.getYawRad(), top)
        }

    /** Whether the hangar has the table plane select needs. */
    val usable: Boolean
        get() = table != null && scene.camera != null

    private val centre = HashMap<Int, VitrinePose?>()
    private var carousel: Carousel? = null
    private var shown = emptyList<Int>()

    /** Plane select was left: the next one starts with the chosen plane in the centre. */
    fun leave() {
        carousel = null
    }

    /** Points [cameras] where the hangar's own camera looks from. */
    fun look(cameras: Cameras) {
        val (at, camera) = scene.camera ?: return
        cameras.fixed(at.localPosition, camera.direction, camera.fieldOfView)
    }

    /**
     * Where every borrowed plane is drawn this frame: the [choices] along the table with [index] at its centre, gliding
     * over [seconds]; the other planes out of sight.
     */
    fun poses(choices: List<PlaneChoice>, index: Int, seconds: Float): Map<Entity, Matrix4> {
        val table = table ?: return emptyMap()
        val ids = choices.map { it.entityId }
        var turning = carousel
        if (turning == null || ids != shown) {
            turning = Carousel(ids.size, index)
            carousel = turning
            shown = ids
        }
        turning.advance(index, seconds)
        val side = Vector3(cos(table.yaw), 0f, -sin(table.yaw))
        val poses = HashMap<Entity, Matrix4>()
        for ((id, entity) in planes) {
            val i = ids.indexOf(id)
            val pose = if (i >= 0) {
                centreOf(id, entity, table)
            } else {
                null
            }
            poses[entity] = if (pose == null) {
                Matrix4().setToTranslation(0f, OUT_OF_SIGHT, 0f)
            } else {
                val at = Vector3(pose.position).mulAdd(side, turning.offset(i) * CAROUSEL_SPACING)
                Matrix4(at, pose.rotation, Vector3(1f, 1f, 1f))
            }
        }
        return poses
    }

    /** The pose of a plane at the table's centre: its lowest point on the table-top, as `vitrinePoses` puts it. */
    private fun centreOf(id: Int, entity: Entity, table: VitrineTable): VitrinePose? =
        centre.getOrPut(id) {
            val at = scene.position(entity)
            val points = assetName(entity, MetaType.MODEL)?.let(modelPoints)
            val turned = VitrineTable(table.position, table.yaw + PLANE_TURN, table.topHeight)
            vitrinePoses(listOf(VitrinePlane(at.localPosition.cpy(), at.localRotation.cpy(), points)), listOf(turned))
                .single()
        }
}
