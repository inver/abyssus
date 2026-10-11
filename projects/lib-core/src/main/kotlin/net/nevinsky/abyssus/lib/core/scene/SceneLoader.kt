package net.nevinsky.abyssus.lib.core.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.NativeJsonTree

class SceneLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
    private val tree: NativeJsonTree = NativeJsonTree(),
) {
    /**
     * Load [SceneDto] from file exclude ecs structure
     */
    fun load(sceneName: String): SceneDto = parse(fileLoader.loadSceneFile(sceneName).readText())

    /** The scene [text] holds, for text that is not (or not yet) a file: an editor's unsaved buffer. */
    fun parse(text: String): SceneDto {
        val node = tree.parseObject(text)
        format.requireSupported(node, DocumentKind.SCENE)
        // the ecs block is kept as read, so number spelling and unknown members reach the component binding unchanged
        return jsonProcessor.bind(node, SceneDto::class.java).copy(ecs = node.get("ecs")?.takeUnless { it.isNull })
    }
}
