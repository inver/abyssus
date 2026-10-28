package net.nevinsky.abyssus.lib.core.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.ecs.EntityDtoModule
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.NativeJsonTree

class SceneLoader(
    jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader,
    registry: ComponentRegistry,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
    private val tree: NativeJsonTree = NativeJsonTree(),
) {
    private val binder = jsonProcessor.binderWith(EntityDtoModule(registry))

    /**
     * Load [SceneDto] from file
     */
    fun load(sceneName: String): SceneDto = parse(fileLoader.loadSceneFile(sceneName).readText())

    /** The scene [text] holds, for text that is not (or not yet) a file: an editor's unsaved buffer. */
    fun parse(text: String): SceneDto {
        val node = tree.parseObject(text)
        format.requireSupported(node, DocumentKind.SCENE)
        return binder.bind(node, SceneDto::class.java)
    }
}
