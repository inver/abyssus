/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import net.nevinsky.abyssus.lib.core.ui.layout.UiRect

/**
 * The sprite batch and the white pixel every [UiPresenter] draws with. A host that rebuilds presenters (an editor on
 * each edit) keeps one kit for its GL context and disposes it once, on the GL thread; a presenter made without a kit
 * owns its own.
 */
class UiGlKit {
    val batch = SpriteBatch()
    val pixel: Texture = Pixmap(1, 1, Pixmap.Format.RGBA8888).let { p ->
        p.setColor(Color.WHITE); p.fill()
        Texture(p).also { p.dispose() }
    }

    fun dispose() {
        batch.dispose()
        pixel.dispose()
    }
}

/** Editing affordances drawn over the UI, in reference space (top-left origin): [ghost] is a drag's drop target. */
class UiEditOverlay(
    var selectedId: String? = null,
    var hoverId: String? = null,
    var ghost: UiRect? = null,
    var outlines: Boolean = true,
)
