/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.runtime.ecs.scene

import net.nevinsky.abyssus.assets.AssetLog

/** Problems met while loading a scene, each message kept and logged once. */
class SceneEcsWarnings(private val log: AssetLog) {
    private val seen = LinkedHashSet<String>()

    val messages: List<String> get() = seen.toList()

    fun warn(message: String) {
        if (seen.add(message)) log.warn(message, null)
    }

}
