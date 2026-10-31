package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

class SceneSerializer(
    private val jsonProcessor: JsonProcessor
) {
    fun serialize(scene: SceneDto): String {
        return jsonProcessor.toString(scene)
    }
}
