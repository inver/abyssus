/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.components

import com.badlogic.ashley.core.Component
import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.editor.document.SceneComponentDecoder

/** Default-aware component binding shared with the runtime JSON processor. */
class ComponentReader(private val json: JsonProcessor) : SceneComponentDecoder {
    override fun <C : Component> read(type: Class<C>, node: JsonNode): C = json.bindComponent(node, type)
}
