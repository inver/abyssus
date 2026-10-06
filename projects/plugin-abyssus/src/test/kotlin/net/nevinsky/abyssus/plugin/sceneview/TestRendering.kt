package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.plugin.AssetLoading
import net.nevinsky.abyssus.lib.core.testing.RecordingLogger
import org.slf4j.Logger
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import java.util.concurrent.Executor

/** Problems written to stderr, the way the IDE logger prints them in tests. */
val printingLog: Logger = RecordingLogger(echo = true)

/**
 * A renderer wired by hand, with no IntelliJ application: the same graph `AbyssusCore` builds in the IDE, with `prepare`
 * on [executor] (the calling thread by default) and problems to [log].
 */
fun testRenderer(executor: Executor = Executor(Runnable::run), log: Logger = printingLog): SceneRenderer = SceneRenderer(
    ViewAssets(AssetLoading(JsonProcessor(), log, executor, ShaderSource("/shader/sky", AssetLoading::class.java))),
    ShaderSource("/shader/scene", SceneRenderer::class.java),
)
