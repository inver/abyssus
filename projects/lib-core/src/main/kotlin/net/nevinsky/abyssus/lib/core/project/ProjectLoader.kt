package net.nevinsky.abyssus.lib.core.project

import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.io.JsonProcessor

class ProjectLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()
) {

    fun load(projectName: String): Project {
        val str = fileLoader.loadProjectFile(projectName).readText()
        val node = jsonProcessor.readObject(str)
        format.requireSupported(node, DocumentKind.PROJECT)
        return jsonProcessor.bind(node, Project::class.java).copy(dir = fileLoader.projectDir.toPath())
    }
}