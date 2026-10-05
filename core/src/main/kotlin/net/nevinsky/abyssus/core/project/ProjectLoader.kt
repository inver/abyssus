package net.nevinsky.abyssus.core.project

import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.JsonProcessor

class ProjectLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader
) {

    fun load(projectName: String): Project {
        val str = fileLoader.loadProjectFile(projectName).readText()
        return jsonProcessor.parse(str, Project::class.java).copy(dir = fileLoader.projectDir.toPath())
    }
}