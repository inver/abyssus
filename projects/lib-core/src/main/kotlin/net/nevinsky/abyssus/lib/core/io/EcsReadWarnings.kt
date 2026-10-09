/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.io

import org.slf4j.Logger

/** Problems met while loading a scene, each message kept and logged once. */
class EcsReadWarnings(private val log: Logger) {
    private val seen = LinkedHashSet<String>()

    val messages: List<String> get() = seen.toList()

    fun warn(message: String) {
        if (seen.add(message)) {
            log.warn(message)
        }
    }
}
