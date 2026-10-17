/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.settings.AntialiasingLevel

/** Pixel formats to try, accepting a driver's silently reduced count without rewriting the preference. */
class SampleFallback {
    fun counts(level: AntialiasingLevel): List<Int> =
        AntialiasingLevel.entries.map { it.samples }.filter { it <= level.samples }.reversed()
}
