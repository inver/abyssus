/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.HingePreview
import java.io.File
import net.nevinsky.abyssus.lib.core.editor.content.AssetPlacement
import net.nevinsky.abyssus.lib.core.editor.content.toMatrix
import net.nevinsky.abyssus.lib.core.editor.scene.ModelEntity

/**
 * The model entities of the scene. Each entity gets its own instance of the shared model, so entities using one asset
 * animate and move independently. GL thread only.
 */
class SceneModels(assets: AssetView<Model>) :
    PlacedAssets<Model, ModelEntity>(
        assets,
        { p, model, previous ->
            // a moved entity keeps its instance and animation
            val instance = previous?.instance ?: ModelInstance(model)
            p.transform.toMatrix(instance.transform!!)
            ModelEntity(p, model, instance, previous?.animation ?: firstAnimationOf(instance))
        },
    ) {
    /**
     * Brings the entities in line with [placements]; advances animations by [deltaSeconds]. A [hinge] preview then turns
     * its part, after the animation, so it wins over a playing animation for that node while the rest keeps animating.
     */
    fun update(placements: List<AssetPlacement>, projectDir: File?, deltaSeconds: Float, hinge: HingePreview? = null) {
        update(placements, projectDir)
        for (e in drawn) {
            e.animation?.update(deltaSeconds)
            if (hinge != null && e.placement.entityId == hinge.entityId) {
                previewHinge(e.instance, hinge.node, hinge.degrees)
            }
        }
    }
}

/** The first animation loops for as long as the entity is shown. */
private fun firstAnimationOf(instance: ModelInstance): AnimationController? =
    instance.animations.firstOrNull()?.let { first ->
        AnimationController(instance).also { it.setAnimation(first.id, -1) }
    }

/**
 * Turns node [node] of [instance] to its rest rotation times [degrees] about its own X axis and recomputes the
 * transforms, so the node's children follow. The rest pose is the model's, so an animation that moves the node does
 * not show through. Does nothing when the instance has no such node.
 */
internal fun previewHinge(instance: ModelInstance, node: String, degrees: Float) {
    val live = instance.getNode(node, true) ?: return
    val rest = instance.model.getNode(node, true) ?: return
    live.translation.set(rest.translation)
    live.scale.set(rest.scale)
    live.rotation.set(rest.rotation).mul(Quaternion(Vector3.X, degrees))
    live.isAnimated = false
    instance.calculateTransforms()
}
