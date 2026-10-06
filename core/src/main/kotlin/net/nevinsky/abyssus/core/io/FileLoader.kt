package net.nevinsky.abyssus.core.io

import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.SCENES_DIR
import java.io.File

class FileLoader(val projectDir: File) {

    private val assetsDir = File(projectDir, ASSETS_DIR)
    private val scenesDir = File(projectDir, SCENES_DIR)

    fun loadAssetFile(assetName: String, fileName: String?): File {
        if (fileName.isNullOrBlank()) {
            throw IllegalArgumentException("Empty file name")
        }
        val dir = folder(assetName) ?: throw IllegalStateException("Could not found asset with name '$assetName'")
        return file(dir, fileName)
            ?: throw IllegalStateException("Failed to load file '$fileName' in asset '$assetName'")
    }

    fun loadAssetFileContent(assetName: String, fileName: String?): String {
        val content = loadAssetFile(assetName, fileName).readText()
        if (content.isBlank()) {
            throw IllegalStateException("Asset '$assetName' has no file '$fileName'")
        }
        return content
    }

    /** The names of every asset folder of the project, sorted. */
    fun assetNames(): List<String> =
        assetsDir.listFiles { file -> file.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun folder(assetName: String): File? {
        // an asset name is a folder name: refuse anything that could leave the assets folder
        if (assetName.isEmpty() || assetName.contains('/') || assetName.contains('\\') || assetName == ".." || assetName == ".") {
            return null
        }
        return File(assetsDir, assetName).takeIf { it.isDirectory }
    }

    fun loadProjectFile(projectName: String): File {
        return file(projectDir, projectName)
            ?: throw IllegalStateException("Failed to load scene '$projectName'")
    }

    fun loadSceneFile(sceneName: String): File {
        return file(scenesDir, sceneName)
            ?: throw IllegalStateException("Failed to load scene '$sceneName'")
    }

    /** The file [name] inside [folder], or null when it is blank, missing or outside the folder. */
    private fun file(folder: File, name: String?): File? {
        if (name.isNullOrBlank()) {
            return null
        }
        val res = File(folder, name)
        if (!res.isFile) {
            return null
        }
        return res
    }
}