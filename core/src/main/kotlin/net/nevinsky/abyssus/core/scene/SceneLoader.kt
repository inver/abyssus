package net.nevinsky.abyssus.core.scene

import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.JsonProcessor

class SceneLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader
) {
    /** The scene file [sceneName] of the project's `scenes` folder. */
    fun load(sceneName: String): Scene = parse(fileLoader.loadSceneFile(sceneName).readText())

    /** The scene [text] holds, for text that is not (or not yet) a file: an editor's unsaved buffer. */
    fun parse(text: String): Scene = jsonProcessor.parse(text, Scene::class.java)
}
