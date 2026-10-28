package net.nevinsky.abyssus.lib.core.scene

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.io.NativeJsonTree

class SceneLoader(
    private val fileLoader: FileLoader,
    private val parser: SceneParser,
) {
    constructor(
        jsonProcessor: JsonProcessor,
        fileLoader: FileLoader,
        registry: ComponentRegistry,
        format: AbyssusDocumentFormat = AbyssusDocumentFormat(),
        tree: NativeJsonTree = NativeJsonTree(),
    ) : this(fileLoader, SceneParser(jsonProcessor, registry, format, tree))

    /**
     * Load [SceneDto] from file
     */
    fun load(sceneName: String): SceneDto = parse(fileLoader.loadSceneFile(sceneName).readText())

    /** The scene [text] holds, for text that is not (or not yet) a file: an editor's unsaved buffer. */
    fun parse(text: String): SceneDto = parser.parse(text)
}
