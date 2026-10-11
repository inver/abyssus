/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.uidemo

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShaderProgram
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
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * The standalone UI demo: the `menus` asset of the demo project drawn by the same [UiPresenter] the editor preview uses,
 * against a [UiDemoHost]'s changing values. It shares nothing with Control Line's own screens. Start opens the HUD
 * overlay over the menu; Pause is a modal overlay; Quit closes the window.
 */
class UiDemoGame(private val project: Path) : ApplicationAdapter() {
    private val log = LoggerFactory.getLogger("ui-demo")
    private val host = UiDemoHost()
    private val bundledFont = UiBundledFont()
    private var resources: UiBuiltResources? = null
    private var presenter: UiPresenter? = null
    private var scale = 1f

    override fun create() {
        // the window is a 3.2 core context: libGDX's legacy GLSL (the sprite batch's own shader included) gets its version
        // line and the defines that make `attribute`, `varying`, `texture2D` and `gl_FragColor` valid, as in the game
        ShaderProgram.prependVertexCode = "#version 150\n#define attribute in\n#define varying out\n"
        ShaderProgram.prependFragmentCode = "#version 150\n#define varying in\n#define texture2D texture\n" +
            "#define textureCube texture\n#define gl_FragColor fragColor\nout vec4 fragColor;\n"
        val json = JsonProcessor(log)
        val files = FileLoader(project.toFile())
        val dto = ProjectLoader(json, files).load("UiDemo.abss")
        val registry = UiProjectRegistry(json, files)
        val collection = (registry.lookup(dto, "menus") as? UiAdmission.Admitted)?.collection
        if (collection == null) {
            log.error("The demo project's menus asset could not be admitted")
            Gdx.app.exit()
            return
        }
        val prepared = UiResourcePreparer(UiResourceResolver(project.resolve("assets/menus").toFile())).prepare(collection)
        prepared.issues.forEach { log.warn("resource {}: {}", it.resourceId, it.problem) }
        val built = UiBuiltResources.Builder().build(prepared).also { resources = it }
        lateinit var ui: UiPresenter
        val session = UiBindingSession(collection, host::write)
        ui = UiPresenter("menus", collection, { name -> (registry.lookup(dto, name) as? UiAdmission.Admitted)?.collection }, session,
            { name -> host.emit(name).also { if (it) react(ui, name) } }, built.forRenderer(bundledFont.acquire()), json)
        presenter = ui
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(x: Int, y: Int, pointer: Int, button: Int) = ui.touchDown(x * scale, y * scale)
            override fun touchUp(x: Int, y: Int, pointer: Int, button: Int) = ui.touchUp(x * scale, y * scale)
            override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean { ui.touchDragged(x * scale, y * scale); return true }
            override fun mouseMoved(x: Int, y: Int): Boolean { ui.pointerMoved(x * scale, y * scale); return false }
            override fun keyDown(keycode: Int) = ui.keyDown(keycode, Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))
            override fun keyTyped(character: Char) = ui.keyTyped(character)
        }
        resize(Gdx.graphics.width, Gdx.graphics.height)
    }

    /** What the game does after its own handler accepted an action. */
    private fun react(ui: UiPresenter, name: String) {
        fun action(type: UiActionType, screen: String?) = UiAction(type.json, type, screenId = screen, raw = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
        when (name) {
            "startGame" -> ui.navigator.activate(action(UiActionType.SHOW_OVERLAY, "hud"))
            "toMenu" -> ui.navigator.activate(action(UiActionType.OPEN_SCREEN, "main"))
            "quit" -> Gdx.app.exit()
        }
    }

    override fun resize(width: Int, height: Int) {
        // the viewport is in pixels, pointer events in window units: HiDPI screens differ by this factor
        scale = Gdx.graphics.backBufferWidth.toFloat() / maxOf(1, Gdx.graphics.width)
        presenter?.resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
    }

    override fun render() {
        val ui = presenter ?: return
        host.tick(Gdx.graphics.deltaTime.toDouble())
        ui.publish(host.snapshot())
        Gdx.gl.glClearColor(0.08f, 0.1f, 0.14f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        ui.render(Gdx.graphics.deltaTime)
    }

    override fun dispose() {
        presenter?.dispose()
        resources?.dispose()
        if (bundledFont.activeLeases > 0) bundledFont.release()
    }
}
