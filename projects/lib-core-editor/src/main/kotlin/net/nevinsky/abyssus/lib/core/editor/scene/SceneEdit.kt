/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto

/** One change of a scene: it edits [SceneDto] in place and returns whether it changed anything. */
fun interface SceneEdit {
    fun apply(scene: SceneDto): Boolean
}
