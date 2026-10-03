/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.ecs.scene

import com.intellij.openapi.diagnostic.Logger

/** Problems met while loading a scene, each message kept and logged once. */
class SceneEcsWarnings {
    private val seen = LinkedHashSet<String>()

    val messages: List<String> get() = seen.toList()

    fun warn(message: String) {
        if (seen.add(message)) LOG.warn(message)
    }

    private companion object {
        val LOG = Logger.getInstance(SceneEcsWarnings::class.java)
    }
}
