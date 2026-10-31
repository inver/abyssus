/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.component.RenderComponent

/** The asset the render component of entity [id] of [scene] names (its type and folder), or null for an entity that renders none. */
fun renderedAsset(scene: SceneDto, id: Long): BaseMeta? =
    scene.component(id, RenderComponent::class.java)?.takeIf { it.assetName.isNotEmpty() }
        ?.let { render -> render.type?.let { BaseMeta(it, render.assetName) } }
