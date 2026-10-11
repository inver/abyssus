/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.lib.core.assets.ui.UiBuiltResources
import net.nevinsky.abyssus.lib.core.assets.ui.UiBundledFont
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourcePreparer
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourceResolver
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.project.ProjectLoader
import net.nevinsky.abyssus.lib.core.project.UiProjectRegistry
import net.nevinsky.abyssus.lib.core.ui.model.UiAction
import net.nevinsky.abyssus.lib.core.ui.model.UiActionType
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.render.UiPresenter
import net.nevinsky.abyssus.lib.core.ui.state.UiBindingSession
import org.slf4j.Logger
import java.nio.file.Path

/** The name field of the name-entry screen: Enter there saves the name. */
private const val NAME_FIELD = "ne-field"

/**
 * The game's screens and HUD: the `screens` UI asset of the project drawn by a [UiPresenter], following the [flow]. Each
 * frame [update] shows the layers of the flow's screen and publishes the [GameUiModel]'s values; buttons send named
 * actions to the model, and the game's own keys are handled by the model before the presenter sees them. GL thread only.
 */
class GameUiHost(
    private val flow: GameFlow,
    val model: GameUiModel,
    val presenter: UiPresenter,
    private val onDispose: () -> Unit,
) {
    private var shown: GameLayers? = null
    private var scale = 1f

    /** Shows the flow's screen (when it changed) and takes the model's values for this frame. */
    fun update(session: FlightSession?, judge: PatternJudge?, seconds: Float) {
        val layers = model.layersOf(flow.screen)
        if (layers != shown) {
            shown = layers
            presenter.display(layers.screen, SCREENS_ASSET)
            layers.overlay?.let { overlay ->
                presenter.navigator.activate(UiAction(UiActionType.SHOW_OVERLAY.json, UiActionType.SHOW_OVERLAY, screenId = overlay, raw = JsonNodeFactory.instance.objectNode()))
            }
        }
        presenter.publish(model.snapshot(session, judge, seconds))
    }

    fun draw(seconds: Float) = presenter.render(seconds)

    /** The window's size in logical units; the UI works in back-buffer pixels, which differ on HiDPI screens. */
    fun resize() {
        scale = Gdx.graphics.backBufferWidth.toFloat() / maxOf(1, Gdx.graphics.width)
        presenter.resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
    }

    /** A key on a menu screen: the game's own keys first, then the presenter's (focus, the focused button, the name field). */
    fun keyDown(keycode: Int): Boolean {
        val screen = flow.screen
        val enter = keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER
        val field = screen is Screen.NameEntry && presenter.navigator.focus == NAME_FIELD
        // the typed name reaches the model when the field commits, which Enter in the field does first
        if (enter && field) presenter.keyDown(keycode)
        if (model.key(keycode, field)) return true
        if (enter && field) return true
        return presenter.keyDown(keycode, Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))
    }

    /** Pointer and text input for the screens; the game puts it behind its own key processor. */
    val inputProcessor: InputProcessor = object : InputAdapter() {
        override fun touchDown(x: Int, y: Int, pointer: Int, button: Int) = presenter.touchDown(x * scale, y * scale)
        override fun touchUp(x: Int, y: Int, pointer: Int, button: Int) = presenter.touchUp(x * scale, y * scale)
        override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean { presenter.touchDragged(x * scale, y * scale); return true }
        override fun mouseMoved(x: Int, y: Int): Boolean { presenter.pointerMoved(x * scale, y * scale); return false }
        override fun keyTyped(character: Char) = presenter.keyTyped(character)
    }

    fun dispose() = onDispose()
}

/**
 * Loads the `screens` asset of [project] (the folder of the native project) and builds the host for [flow]; the asset
 * must be registered in the project file and admitted. Needs the GL context, and the core-profile shader prefixes set.
 */
fun createGameUi(project: Path, projectFile: String, flow: GameFlow, log: Logger, quit: () -> Unit): GameUiHost {
    val json = JsonProcessor(log)
    val files = FileLoader(project.toFile())
    val dto = ProjectLoader(json, files).load(projectFile)
    val registry = UiProjectRegistry(json, files)
    val admission = registry.lookup(dto, SCREENS_ASSET)
    val admitted = admission as? UiAdmission.Admitted
        ?: throw IllegalStateException("The '$SCREENS_ASSET' UI asset of $project is not registered or was refused: $admission")
    val collection = admitted.collection
    admitted.warnings.forEach { log.warn("screens asset: {}", it) }
    val prepared = UiResourcePreparer(UiResourceResolver(project.resolve("assets/$SCREENS_ASSET").toFile())).prepare(collection)
    prepared.issues.forEach { log.warn("screens resource {}: {}", it.resourceId, it.problem) }
    val built = UiBuiltResources.Builder().build(prepared)
    val font = UiBundledFont()
    val model = GameUiModel(flow, quit)
    val presenter = UiPresenter(
        SCREENS_ASSET, collection, { name -> (registry.lookup(dto, name) as? UiAdmission.Admitted)?.collection },
        UiBindingSession(collection, model::write), model::emit, built.forRenderer(font.acquire()), json,
    )
    return GameUiHost(flow, model, presenter) {
        presenter.dispose()
        built.dispose()
        if (font.activeLeases > 0) font.release()
    }
}
