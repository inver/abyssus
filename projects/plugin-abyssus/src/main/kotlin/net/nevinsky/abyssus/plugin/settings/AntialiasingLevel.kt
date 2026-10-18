/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.settings

/** Scene view quality stored by enum name in a native project's `sceneViewAntialiasing` field. */
enum class AntialiasingLevel(val samples: Int) {
    OFF(0),
    X2(2),
    X4(4),
    X8(8);

    companion object {
        val DEFAULT = X4
        fun parse(text: String?): AntialiasingLevel = entries.firstOrNull { it.name == text } ?: DEFAULT
    }
}

