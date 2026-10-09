package net.nevinsky.abyssus.lib.gdx.scene

import net.nevinsky.abyssus.lib.gdx.dto.SceneDto
import net.nevinsky.abyssus.lib.gdx.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.gdx.format.DocumentKind
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor

class SceneLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()
) {
    /**
     * Load [SceneDto] from file exclude ecs structure
     */
    fun load(sceneName: String): SceneDto = parse(fileLoader.loadSceneFile(sceneName).readText())

    /** The scene [text] holds, for text that is not (or not yet) a file: an editor's unsaved buffer. */
    fun parse(text: String): SceneDto {
        val node = jsonProcessor.readObject(text)
        format.requireSupported(node, DocumentKind.SCENE)
        return jsonProcessor.bind(node, SceneDto::class.java)
    }
}
