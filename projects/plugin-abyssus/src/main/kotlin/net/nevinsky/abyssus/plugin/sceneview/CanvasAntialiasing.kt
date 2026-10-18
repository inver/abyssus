/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

/** GL-free canvas lifecycle decisions, owned by one view on EDT. Replacement creates no GL context itself. */
internal class CanvasAntialiasing(
    initial: AntialiasingLevel,
    private val replace: (Int) -> Unit,
    private val warn: (Int, Throwable) -> Unit,
    private val fail: (Throwable) -> Unit,
) {
    private var level = initial
    private var remaining = SampleFallback().counts(initial)
    var samples: Int = remaining.first()
        private set
    private var stale = false
    private var warned = false

    fun setLevel(value: AntialiasingLevel, showing: Boolean) {
        if (value == level) return
        level = value
        remaining = SampleFallback().counts(value)
        stale = true
        if (showing) shown()
    }

    fun abandoned() { stale = true }

    fun shown() {
        if (!stale) return
        samples = remaining.first()
        replace(samples)
        // Disposing the old hidden canvas may call abandoned(); the new canvas is already fresh.
        stale = false
    }

    fun renderFailed(error: Throwable, initialized: Boolean) {
        if (initialized || samples == 0) {
            fail(error)
            return
        }
        if (!warned) {
            warned = true
            warn(samples, error)
        }
        remaining = remaining.drop(1)
        stale = true
        shown()
    }
}
