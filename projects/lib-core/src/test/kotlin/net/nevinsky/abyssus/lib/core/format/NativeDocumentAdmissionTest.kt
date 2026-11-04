package net.nevinsky.abyssus.lib.gdx.format

import java.io.File
import java.nio.file.Files
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.project.ProjectLoader
import net.nevinsky.abyssus.lib.core.scene.SceneLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.*
import org.junit.Test

class NativeDocumentAdmissionTest {
    @Test
    fun sceneReadersRejectMissingAndNonIntegralHeadersWithoutChangingFiles() {
        val dir = Files.createTempDirectory("native-scene").toFile()
        try {
            val file = File(dir, "scenes/Main.scene").also { it.parentFile.mkdirs() }
            val loader =
                SceneLoader(
                    JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER),
                    FileLoader(dir),
                    net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry(),
                )
            for (text in
                listOf(
                    "{}",
                    """{"format":"abyssus","formatVersion":1.0}""",
                    """{"format":"abyssus","formatVersion":2}""",
                )) {
                file.writeText(text)
                assertThrows(IllegalArgumentException::class.java) { loader.parse(text) }
                assertThrows(IllegalArgumentException::class.java) { loader.load("Main.scene") }
                assertEquals(text, file.readText())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun projectAdmissionHappensBeforeBindingAndLeavesInputAlone() {
        val dir = Files.createTempDirectory("native-project").toFile()
        try {
            val file = File(dir, "Example.abss")
            val text = """{"name":"Example","scenes":[]}"""
            file.writeText(text)
            assertThrows(IllegalArgumentException::class.java) {
                ProjectLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir)).load(file.name)
            }
            assertEquals(text, file.readText())
            val native = """{"format":"abyssus","formatVersion":1,"name":"Example","extra":1.00}"""
            file.writeText(native)
            assertEquals(
                "Example",
                ProjectLoader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER), FileLoader(dir))
                    .load(file.name)
                    .name,
            )
            assertEquals(native, file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
