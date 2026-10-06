package net.nevinsky.abyssus.lib.core.scene

import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

class SceneLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()
) {
    /** The scene file [sceneName] of the project's `scenes` folder. */
    fun load(sceneName: String): Scene = parse(fileLoader.loadSceneFile(sceneName).readText())

    /** The scene [text] holds, for text that is not (or not yet) a file: an editor's unsaved buffer. */
    fun parse(text: String): Scene {
        val node = jsonProcessor.readObject(text)
        format.requireSupported(node, DocumentKind.SCENE)
        return jsonProcessor.bind(node, Scene::class.java)
    }
}
