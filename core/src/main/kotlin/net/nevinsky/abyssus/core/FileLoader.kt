package net.nevinsky.abyssus.core

import net.nevinsky.abyssus.core.AbyssusProjectLayout.ASSETS_DIR
import java.io.File

class FileLoader(val projectDir: File) {

    private val assetsDir = File(projectDir, ASSETS_DIR)

    fun loadFile(assetName: String, fileName: String?): File {
        if (fileName.isNullOrBlank()) {
            throw IllegalArgumentException("Empty file name")
        }
        val dir = folder(assetName) ?: throw IllegalStateException("Could not found asset with name '$assetName'")
        return file(dir, fileName)
            ?: throw IllegalStateException("Failed to load file '$fileName' in asset '$assetName'")
    }

    fun loadFileContent(assetName: String, fileName: String?): String {
        val content = loadFile(assetName, fileName).readText()
        if (content.isBlank()) {
            throw IllegalStateException("Asset '$assetName' has no file '$fileName'")
        }
        return content
    }

    fun folder(assetName: String): File? {
        // an asset name is a folder name: refuse anything that could leave the assets folder
        if (assetName.isEmpty() || assetName.contains('/') || assetName.contains('\\') || assetName == ".." || assetName == ".") {
            return null
        }
        return File(assetsDir, assetName).takeIf { it.isDirectory }
    }

    /** The file [name] inside [folder], or null when it is blank, missing or outside the folder. */
    fun file(folder: File, name: String?): File? {
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