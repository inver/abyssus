/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.ray

import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.raytracing.RayFrame
import net.nevinsky.abyssus.lib.render.hostframe.HostFramePresenter

/** Presents a [RayFrame] through `lib-render`'s [HostFramePresenter] (the same technique as the editor). GL thread only. */
internal class RayFrameQuad : Disposable {
    private val presenter = HostFramePresenter()

    fun draw(frame: RayFrame) = presenter.draw(frame, frame.width, frame.height, frame::copyColorTo, frame::copyDepthTo)

    override fun dispose() = presenter.dispose()
}
