/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.editor.content.Pose

import com.badlogic.gdx.graphics.Camera
import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * What an overlay sees of its Scene view in one drawing pass. [content] holds the poses the view shows (authored, or
 * simulated while playing, with any drag preview applied); [ecs] is the scene's `ecs` block as the editor holds it.
 * [onTop] is false for the depth-tested pass and true for the pass drawn over everything, for the selection.
 */
class OverlayView(
    val content: SceneContent,
    val ecs: JsonNode?,
    val projectDir: File?,
    val selectedId: String?,
    val camera: Camera,
    val viewportHeight: Int,
    val playing: Boolean,
    val onTop: Boolean,
)

/**
 * Lines and markers another plugin draws in one Scene view, each frame, on the render thread inside
 * `GdxRuntime.withContext`. It draws only through the [LineSink] and owns no GL objects. One instance per view,
 * disposed with it.
 */
interface SceneOverlay : Disposable {
    /** Called twice a frame: once depth-tested ([OverlayView.onTop] false), then over everything. */
    fun draw(view: OverlayView, lines: LineSink)

    /** Actions for this view's toolbar, such as a toggle that shows or hides the overlay. EDT only. */
    fun actions(): List<AnAction> = emptyList()

    override fun dispose() {}
}

/** The `net.nevinsky.abyssus.sceneOverlay` extension: makes a [SceneOverlay] for each Scene view that opens. */
interface SceneOverlayProvider {
    fun create(project: Project, file: VirtualFile): SceneOverlay

    companion object {
        val EP_NAME = ExtensionPointName.create<SceneOverlayProvider>("net.nevinsky.abyssus.sceneOverlay")
    }
}

/** What a simulation starts from: the scene as the editor holds it (unsaved text included). */
class SimulationRequest(
    val project: Project,
    val file: VirtualFile,
    val sceneText: String,
    val projectDir: File,
    /** The entity selected when Play was pressed, null for none. */
    val selection: String?,
)

/** A key or mouse event passed to a running simulation: a key by name (`W`), or a mouse [button] at view pixel [x], [y]. */
data class SimulationInput(val kind: Kind, val key: String = "", val button: Int = 0, val x: Int = 0, val y: Int = 0) {
    enum class Kind { KEY_DOWN, KEY_UP, BUTTON_DOWN, BUTTON_UP, MOUSE_MOVE }
}

/** How a simulation tells the view it started or ended on its own. Callable from any thread. */
interface SimulationListener {
    fun started()

    /** The simulation ended unexpectedly; the provider has already told the user why. */
    fun failed(message: String)
}

/** A running simulation of one Scene view. Commands are called on the EDT and must not block. */
interface SceneSimulation {
    fun pause()
    fun resume()

    /** Advances a paused simulation by one fixed step. */
    fun step()

    /** Ends the simulation and releases everything it holds; called once. */
    fun stop()

    fun input(event: SimulationInput)

    /** The latest simulated poses by entity id, null before the first arrive. Called on the render thread. */
    fun poses(): Map<String, Pose>?
}

/**
 * The `net.nevinsky.abyssus.sceneSimulation` extension: Play in the Scene view. With one installed, the view shows Play,
 * Pause, Step and Stop. [start] must return at once; the simulation reports through the listener when it runs.
 */
interface SceneSimulationProvider {
    fun start(request: SimulationRequest, listener: SimulationListener): SceneSimulation

    companion object {
        val EP_NAME = ExtensionPointName.create<SceneSimulationProvider>("net.nevinsky.abyssus.sceneSimulation")
    }
}
