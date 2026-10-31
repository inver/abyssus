package net.nevinsky.abyssus.lib.core.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EntityDtoModule
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

/**
 * Turns scene text into a [SceneDto]: the native format is checked first, then the tree (which keeps number text) is
 * bound with components resolved through the registry. It needs no file access, so the game's [SceneLoader] and the
 * editor's unsaved-buffer reads share one admission and binding rule.
 */
class SceneParser(
    private val jsonProcessor: JsonProcessor,
    registry: ComponentRegistry,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
) {
    private val binder = jsonProcessor.binderWith(EntityDtoModule(registry))

    fun parse(text: String): SceneDto {
        val node = jsonProcessor.readObject(text)
        format.requireSupported(node, DocumentKind.SCENE)
        return binder.bind(node, SceneDto::class.java)
    }
}
