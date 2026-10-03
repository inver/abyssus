/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.Disposable
import javax.swing.JComponent

/** A live view of a scene. The editor talks to this so the GL-backed [SceneViewPanel] can be replaced in tests. */
interface SceneView : Disposable {
    val view: JComponent

    /** Called on the AWT thread when rendering fails and the view has stopped. */
    var onFailure: ((Throwable) -> Unit)?

    /** Called on the AWT thread with the id (key under `ecs/entities`) of the entity the user clicked. */
    var onPick: ((String) -> Unit)?

    /**
     * Called on the AWT thread when a drag of a gizmo handle ends with the entity moved or rotated, with the entity's id
     * (key under `ecs/entities`) and the change. Returns whether it was written to the scene.
     */
    var onTransform: ((String, TransformEdit) -> Boolean)?

    /** Selects an entity after creation or a selection in the Abyssus tree. */
    fun selectEntity(entityId: String) {}

    fun setParams(params: SceneRenderParams)
}
