/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime.scene

import net.nevinsky.abyssus.assets.json.JsonProcessor

class SceneParser(private val json: JsonProcessor, private val format: net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat = net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat()) {
    fun parse(text: String): SceneDto {
        val root = json.readObject(text)
        format.requireSupported(root, net.nevinsky.abyssus.assets.format.DocumentKind.SCENE)
        return json.bind(root, SceneDto::class.java)
    }
}
