package net.nevinsky.abyssus.core.format

import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.project.ProjectLoader
import net.nevinsky.abyssus.core.scene.SceneLoader
import net.nevinsky.abyssus.testing.warningsTo
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NativeDocumentAdmissionTest {
    @Test fun sceneReadersRejectMissingAndNonIntegralHeadersWithoutChangingFiles() {
        val dir = Files.createTempDirectory("native-scene").toFile()
        try {
            val file = File(dir, "scenes/Main.scene").also { it.parentFile.mkdirs() }
            val loader = SceneLoader(JsonProcessor(), FileLoader(dir))
            for (text in listOf("{}", """{"format":"abyssus","formatVersion":1.0}""", """{"format":"abyssus","formatVersion":2}""")) {
                file.writeText(text)
                assertThrows(IllegalArgumentException::class.java) { loader.parse(text) }
                assertThrows(IllegalArgumentException::class.java) { loader.load("Main.scene") }
                assertEquals(text, file.readText())
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun projectAdmissionHappensBeforeBindingAndLeavesInputAlone() {
        val dir = Files.createTempDirectory("native-project").toFile()
        try {
            val file = File(dir, "Example.abss")
            val text = """{"name":"Example","scenes":[]}"""
            file.writeText(text)
            assertThrows(IllegalArgumentException::class.java) { ProjectLoader(JsonProcessor(), FileLoader(dir)).load(file.name) }
            assertEquals(text, file.readText())
            val native = """{"format":"abyssus","formatVersion":1,"name":"Example","extra":1.00}"""
            file.writeText(native)
            assertEquals("Example", ProjectLoader(JsonProcessor(), FileLoader(dir)).load(file.name).name)
            assertEquals(native, file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectedMetadataIsReportedOnceAndRepairIsLoaded() {
        val dir = Files.createTempDirectory("native-meta").toFile()
        try {
            val file = File(dir, "assets/model/meta.json").also { it.parentFile.mkdirs() }
            val invalid = """{"type":"MODEL"}"""
            file.writeText(invalid)
            val messages = mutableListOf<String>()
            val loader = AssetMetaLoader(JsonProcessor(), FileLoader(dir), warningsTo(messages))
            assertNull(loader.loadBaseMeta("model"))
            assertNull(loader.loadBaseMeta("model"))
            assertEquals(1, messages.size)
            assertTrue(messages.single().contains("format"))
            assertEquals(invalid, file.readText())
            val repaired = """{"format":"abyssus","formatVersion":1,"type":"MODEL","extra":1.00}"""
            file.writeText(repaired)
            assertEquals("model", loader.loadBaseMeta("model")!!.name)
            assertEquals(repaired, file.readText())
        } finally { dir.deleteRecursively() }
    }
}
