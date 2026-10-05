package net.nevinsky.abyssus.core.assets

import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.assets.loading.ShaderSource
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.io.File

/** A fixture project under the repository's `src/test/testData/project` (shared with the plugin's tests). */
fun testProject(name: String): File =
    File(checkNotNull(System.getProperty("abyssus.testData")) { "run through Gradle: abyssus.testData is not set" }, "project/$name")

/** The sky shaders of this module, as the plugin passes them. */
fun skyShaders(): ShaderSource = ShaderSource("/shader/sky", ShaderSource::class.java)

/** The file access of the project folder [projectDir]. */
fun testFileLoader(projectDir: File): FileLoader = FileLoader(projectDir)

/** The `meta.json` loader of the project folder [projectDir]; [log] receives unreadable metas. */
fun testMetaLoader(
    projectDir: File,
    log: Logger = NOPLogger.NOP_LOGGER,
    fileLoader: FileLoader = testFileLoader(projectDir),
): AssetMetaLoader = AssetMetaLoader(JsonProcessor(), fileLoader, log)

private val exrCopy: File by lazy {
    val stream = checkNotNull(ShaderSource::class.java.getResourceAsStream("/hdr/grass_1k.exr")) { "fixture missing" }
    java.nio.file.Files.createTempFile("grass_1k", ".exr").toFile().also { file ->
        file.deleteOnExit()
        stream.use { file.writeBytes(it.readAllBytes()) }
    }
}

/** The 1024 x 512 OpenEXR sky bundled with the module, as a real file (the resource may sit inside a jar). */
fun exrFixture(): File = exrCopy
