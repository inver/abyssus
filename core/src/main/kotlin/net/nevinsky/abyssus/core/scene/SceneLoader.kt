package net.nevinsky.abyssus.core.scene

import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.JsonProcessor

class SceneLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader
) {
    fun load(sceneName: String): Scene {
        val str = fileLoader.loadSceneFile(sceneName).readText()
        return jsonProcessor.parse(str, Scene::class.java)
    }
}