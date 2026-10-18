/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

/**
 * The preview size and backdrop of one UI editor tab: per viewer through [preferences] under [prefKey], never part of a
 * scene or UI document.
 */
internal class UiViewSettings(private val preferences: UiPreferences, private val prefKey: String) {
    var viewport: UiViewport = (preferences.get("$prefKey.viewport")?.split("x")?.mapNotNull { it.toIntOrNull() }
        ?.takeIf { it.size == 2 }?.let { UiViewport(it[0], it[1]) }?.takeIf { it.valid }) ?: UiViewport(1280, 720)
        private set

    var backdropScene: String? = preferences.get("$prefKey.backdrop")
        private set

    /** Sets what the preview lays out for; false (nothing changes) for a size that is not positive or is above [MAX_PREVIEW_SIDE]. */
    fun setViewport(width: Int, height: Int): Boolean {
        val next = UiViewport(width, height)
        if (!next.valid) return false
        viewport = next
        preferences.set("$prefKey.viewport", "${width}x$height")
        return true
    }

    /** Chooses the scene drawn behind the preview (a file name in `scenes`), or none. */
    fun setBackdrop(name: String?) {
        backdropScene = name
        preferences.set("$prefKey.backdrop", name)
    }
}
