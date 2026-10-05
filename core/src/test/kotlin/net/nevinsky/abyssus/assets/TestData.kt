package net.nevinsky.abyssus.assets

import net.nevinsky.abyssus.assets.json.JsonProcessor
import org.slf4j.Logger
import org.slf4j.helpers.NOPLogger
import java.io.File

/** A fixture project under the repository's `src/test/testData/project` (shared with the plugin's tests). */
fun testProject(name: String): File =
    File(checkNotNull(System.getProperty("abyssus.testData")) { "run through Gradle: abyssus.testData is not set" }, "project/$name")

/** The sky shaders of this module, as the plugin passes them. */
fun skyShaders(): ShaderSource = ShaderSource("/shader/sky", AssetLoading::class.java)

/** A loading graph with no IDE: [log] receives problems, `prepare` runs on [executor] (the calling thread by default). */
fun testLoading(
    log: Logger = NOPLogger.NOP_LOGGER,
    executor: java.util.concurrent.Executor = java.util.concurrent.Executor(Runnable::run),
): AssetLoading = AssetLoading(JsonProcessor(), log, executor, skyShaders())
