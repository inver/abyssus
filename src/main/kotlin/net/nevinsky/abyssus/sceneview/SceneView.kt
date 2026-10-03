/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
