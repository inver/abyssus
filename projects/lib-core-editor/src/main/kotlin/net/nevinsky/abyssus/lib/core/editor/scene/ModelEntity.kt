/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.scene

import net.nevinsky.abyssus.lib.gdx.editor.content.AssetPlacement
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.gdx.AnimationController
import net.nevinsky.abyssus.lib.gdx.ModelInstance
import net.nevinsky.abyssus.lib.gdx.model.Model

/** One drawn entity: its own instance, its animation (null for a static model) and what it was built from. */
class ModelEntity(
    override val placement: AssetPlacement,
    val model: Model,
    val instance: ModelInstance,
    val animation: AnimationController?,
) : PlacedEntity<Model> {
    override val asset: Model get() = model

    /** The model's bounds in its own space, computed on first use (the posed meshes are walked once, not per click). */
    val localBounds: BoundingBox by lazy { instance.calculateBoundingBox(BoundingBox())!! }
}
