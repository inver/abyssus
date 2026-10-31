/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.dto.SceneDto

/** One change of a scene: it edits [SceneDto] in place and returns whether it changed anything. */
fun interface SceneEdit {
    fun apply(scene: SceneDto): Boolean
}

/**
 * The entity of [scene] that the view or the Properties panel calls [id] (they keep entity ids as text), or null when
 * [id] is no number or no such entity exists.
 */
fun entityById(scene: SceneDto, id: String): EntityDto? = id.toLongOrNull()?.let(scene::entity)
