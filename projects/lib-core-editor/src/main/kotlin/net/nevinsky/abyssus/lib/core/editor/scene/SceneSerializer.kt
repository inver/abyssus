package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/** Writes a [SceneDto] as scene text. Open so a text-editor test can stand in for it until the entity serializer exists. */
open class SceneSerializer(
    private val jsonProcessor: JsonProcessor
) {
    open fun serialize(scene: SceneDto): String {
        return jsonProcessor.toString(scene)
    }
}
