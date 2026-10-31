/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.display

import com.badlogic.gdx.Gdx

/** The game window as far as the display setting cares: its mode now, and the two switches. */
interface DisplayWindow {
    /** Whether the window is fullscreen now. */
    val fullscreen: Boolean

    /** Switches to fullscreen at the current display mode; false when that was refused. */
    fun enterFullscreen(): Boolean

    /** Switches to a window of [width] x [height]; false when that was refused. */
    fun enterWindowed(width: Int, height: Int): Boolean
}

/** libGDX's window: the LWJGL3 backend's graphics object. Main thread only. */
class GdxWindow : DisplayWindow {
    override val fullscreen: Boolean get() = Gdx.graphics.isFullscreen

    override fun enterFullscreen(): Boolean = Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)

    override fun enterWindowed(width: Int, height: Int): Boolean = Gdx.graphics.setWindowedMode(width, height)
}

/**
 * The display option of the settings screen (design decision 8): [setFullscreen] keeps the choice at once (in `display.json`) and
 * marks the switch pending; [applyPending], called at the top of the next frame so a window-mode change never lands in the middle
 * of drawing the screen that asked for it, makes the switch. A switch the window refuses is dropped and the choice goes back to the
 * mode the window is really in, so the checkbox, the file and the window agree. Main thread only.
 */
class GdxDisplay(private val settings: DisplaySettings, private val window: DisplayWindow = GdxWindow()) : DisplayOptions {
    private var pending = false

    override val damaged: Boolean get() = settings.damaged

    /** The mode the window will be in after the next frame while a switch is pending, else the one it is in. */
    override val fullscreen: Boolean get() = if (pending) settings.fullscreen else window.fullscreen

    override fun setFullscreen(on: Boolean) {
        if (fullscreen == on) return
        settings.setFullscreen(on)
        pending = true
    }

    /** Makes the switch asked for since the last call, if any. */
    fun applyPending() {
        if (!pending) return
        pending = false
        val ok = if (settings.fullscreen) window.enterFullscreen() else window.enterWindowed(WINDOW_WIDTH, WINDOW_HEIGHT)
        if (!ok) settings.setFullscreen(window.fullscreen)
    }
}
