/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import net.nevinsky.abyssus.lib.core.ui.model.showOverlayAction
import net.nevinsky.abyssus.app.game.controlline.flight.FlightSession
import net.nevinsky.abyssus.app.game.controlline.controller.ControllerSettings
import net.nevinsky.abyssus.app.game.controlline.controller.NoControllerSettings
import net.nevinsky.abyssus.app.game.controlline.controller.navigateKey
import net.nevinsky.abyssus.app.game.controlline.display.DisplayOptions
import net.nevinsky.abyssus.app.game.controlline.display.NoDisplayOptions
import net.nevinsky.abyssus.app.game.controlline.flow.GameFlow
import net.nevinsky.abyssus.app.game.controlline.flow.Screen
import net.nevinsky.abyssus.app.game.controlline.pattern.PatternJudge
import net.nevinsky.abyssus.app.game.controlline.scale.ScaleJudge
import net.nevinsky.abyssus.lib.core.assets.ui.UiBuiltResources
import net.nevinsky.abyssus.lib.core.assets.ui.UiBundledFont
import net.nevinsky.abyssus.lib.core.assets.ui.UiPreparedResource
import net.nevinsky.abyssus.lib.core.assets.ui.UiPreparedResources
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
import java.io.File
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

    /** Shows the flow's screen (when it changed) and takes the model's values for this frame. */
    fun update(session: FlightSession?, judge: PatternJudge?, seconds: Float, scaleJudge: ScaleJudge? = null) {
        val layers = model.layersOf(flow.screen)
        if (layers != shown) {
            shown = layers
            presenter.display(layers.screen, SCREENS_ASSET)
            layers.overlay?.let { overlay ->
                presenter.navigator.activate(showOverlayAction(overlay))
            }
        }
        presenter.publish(model.snapshot(session, judge, seconds, scaleJudge))
    }

    fun draw(seconds: Float) = presenter.render(seconds)

    /**
     * Sizes the UI to the window. libGDX's default HiDPI mode is logical: `glViewport` and `glScissor` multiply by the
     * back buffer's scale themselves, so the presenter gets the window's logical size and pointer events need no scaling.
     */
    fun resize() = presenter.resize(Gdx.graphics.width, Gdx.graphics.height)

    /** A key on a menu screen: the game's own keys first, then the presenter's (focus, the focused button, the name field). */
    fun keyDown(keycode: Int): Boolean {
        val screen = flow.screen
        val enter = keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER
        val field = screen is Screen.NameEntry && presenter.navigator.focus == NAME_FIELD
        // the typed name reaches the model when the field commits, which Enter in the field does first
        if (enter && field) presenter.keyDown(keycode)
        if (model.key(keycode, field, presenter.navigator.focus)) return true
        if (enter && field) return true
        // no input device in a test without a window: no shift
        val input: Input? = Gdx.input
        return presenter.keyDown(keycode, input != null && (input.isKeyPressed(Input.Keys.SHIFT_LEFT) || input.isKeyPressed(Input.Keys.SHIFT_RIGHT)))
    }

    /**
     * A key a controller sends to navigate (design decision 5 of `add-control-line-gamepad-support`): as [keyDown], then Tab
     * or Shift-Tab for an Up or Down a tab-navigated screen did not take, and Space for an Enter nothing took (a checkbox).
     */
    fun navigate(keycode: Int): Boolean = navigateKey(
        keycode, ::keyDown,
        { shift -> presenter.keyDown(Input.Keys.TAB, shift) },
        { presenter.keyDown(Input.Keys.SPACE) },
    )

    /** Pointer and text input for the screens; the game puts it behind its own key processor. */
    val inputProcessor: InputProcessor = object : InputAdapter() {
        override fun touchDown(x: Int, y: Int, pointer: Int, button: Int) = presenter.touchDown(x.toFloat(), y.toFloat())
        override fun touchUp(x: Int, y: Int, pointer: Int, button: Int) = presenter.touchUp(x.toFloat(), y.toFloat())
        override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean { presenter.touchDragged(x.toFloat(), y.toFloat()); return true }
        override fun mouseMoved(x: Int, y: Int): Boolean { presenter.pointerMoved(x.toFloat(), y.toFloat()); return false }
        override fun keyTyped(character: Char) = presenter.keyTyped(character)
    }

    fun dispose() = onDispose()
}

/** The back buffer's pixels per logical pixel; 1 on an ordinary display, 2 on a Retina one. */
private fun backBufferScale(): Float = Gdx.graphics.backBufferWidth.toFloat() / Gdx.graphics.width.coerceAtLeast(1)

/** From this scale on the sharper `@2x` fonts are used. */
private const val HIDPI_FROM = 1.5f

/**
 * These resources with every bitmap font that has an `@2x` twin (`fonts/display-28@2x.fnt`, written by `generateUiFonts`)
 * replaced by it. A style's `fontSize` is a size in logical pixels and the renderer scales a font to it, so the larger
 * glyphs come out the same size and are sampled one to one on the denser back buffer.
 */
private fun UiPreparedResources.withHiDpiFonts(): UiPreparedResources = UiPreparedResources(
    resources.mapValues { (_, resource) ->
        if (resource !is UiPreparedResource.Font) return@mapValues resource
        val twin = File(resource.fnt.parentFile, resource.fnt.nameWithoutExtension + "@2x.fnt")
        val page = File(twin.parentFile, twin.nameWithoutExtension + ".png")
        if (twin.isFile && page.isFile) UiPreparedResource.Font(resource.id, twin, listOf(page)) else resource
    },
)

/**
 * Loads the `screens` asset of [project] (the folder of the native project) and builds the host for [flow]; the asset
 * must be registered in the project file and admitted. Needs the GL context, and the core-profile shader prefixes set.
 */
fun createGameUi(
    project: Path, projectFile: String, flow: GameFlow, log: Logger, controls: ControllerSettings = NoControllerSettings,
    display: DisplayOptions = NoDisplayOptions, quit: () -> Unit,
): GameUiHost {
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
        .let { if (backBufferScale() >= HIDPI_FROM) it.withHiDpiFonts() else it }
    prepared.issues.forEach { log.warn("screens resource {}: {}", it.resourceId, it.problem) }
    val built = UiBuiltResources.Builder().build(prepared)
    val font = UiBundledFont()
    val model = GameUiModel(flow, controls, display, quit)
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
