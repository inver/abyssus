/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime.scene

import net.nevinsky.abyssus.assets.json.JsonProcessor

class SceneParser(private val json: JsonProcessor) {
    fun parse(text: String): SceneDto = json.bind(json.readObject(text), SceneDto::class.java)
}
