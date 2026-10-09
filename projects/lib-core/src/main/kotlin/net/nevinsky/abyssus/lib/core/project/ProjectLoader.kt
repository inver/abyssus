package net.nevinsky.abyssus.lib.gdx.project

import net.nevinsky.abyssus.lib.gdx.dto.ProjectDto
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.gdx.format.DocumentKind
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor

class ProjectLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader,
    private val format: AbyssusDocumentFormat = AbyssusDocumentFormat()
) {

    fun load(projectName: String): ProjectDto {
        val str = fileLoader.loadProjectFile(projectName).readText()
        val node = jsonProcessor.readObject(str)
        format.requireSupported(node, DocumentKind.PROJECT)
        return jsonProcessor.bind(node, ProjectDto::class.java).copy(dir = fileLoader.projectDir.toPath())
    }
}