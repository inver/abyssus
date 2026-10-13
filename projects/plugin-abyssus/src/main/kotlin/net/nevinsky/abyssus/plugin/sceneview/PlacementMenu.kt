/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.actionSystem.DefaultActionGroup

/**
 * An "Add ..." menu of the Scene view: whether it can be offered now ([available]) and the choices it lists, which are
 * given what they place at ([choices]). Keeping the two together means a host cannot supply one without the other.
 */
class PlacementMenu<T>(val available: () -> Boolean, val choices: (T) -> DefaultActionGroup) {
    /** The choices placing at [where], or null while the menu cannot be offered. */
    fun choicesAt(where: T): DefaultActionGroup? = if (available()) choices(where) else null
}
