package net.nevinsky.abyssus.core

import net.nevinsky.abyssus.core.io.FileLoader

import net.nevinsky.abyssus.core.assets.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileLoaderTest {
    private val untitled = FileLoader(testProject("Untitled"))

    @Test
    fun resolvesAnAssetFolderAndItsFile() {
        val folder = untitled.folder("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")!!
        assertEquals("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", folder.name)
        assertEquals("model.gltf", untitled.loadAssetFile("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", "model.gltf").name)
    }

    @Test
    fun namesThatLeaveTheAssetsFolderOrDoNotExistHaveNoFolder() {
        for (name in listOf("", ".", "..", "../Untitled", "a/b", "a\\b", "model_nope")) assertNull(name, untitled.folder(name))
    }

    @Test
    fun aMissingAssetOrFileIsReportedWithItsName() {
        val asset = "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"
        assertThrows(IllegalStateException::class.java) { untitled.loadAssetFile("model_nope", "model.gltf") }.also {
            assertEquals(true, it.message!!.contains("model_nope"))
        }
        assertThrows(IllegalStateException::class.java) { untitled.loadAssetFile(asset, "nope.gltf") }.also {
            assertEquals(true, it.message!!.contains("nope.gltf"))
        }
        assertThrows(IllegalArgumentException::class.java) { untitled.loadAssetFile(asset, null) }
        assertThrows(IllegalArgumentException::class.java) { untitled.loadAssetFile(asset, " ") }
    }

    @Test
    fun aFileMustBeInsideItsAssetFolder() {
        assertThrows(IllegalStateException::class.java) {
            untitled.loadAssetFile("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb", "..")
        }
    }

    @Test
    fun blankContentIsAnError() {
        val dir = Files.createTempDirectory("proj").toFile()
        try {
            File(dir, "assets/a").mkdirs()
            File(dir, "assets/a/blank.glsl").writeText("  \n")
            File(dir, "assets/a/code.glsl").writeText("void main() {}")
            val loader = FileLoader(dir)
            assertEquals("void main() {}", loader.loadAssetFileContent("a", "code.glsl"))
            assertThrows(IllegalStateException::class.java) { loader.loadAssetFileContent("a", "blank.glsl") }
        } finally {
            dir.deleteRecursively()
        }
    }
}
