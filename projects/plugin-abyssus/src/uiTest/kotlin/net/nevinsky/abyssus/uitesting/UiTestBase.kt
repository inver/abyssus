package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.RemoteRobot
import org.junit.After
import org.junit.Before
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.rules.Timeout
import org.junit.runner.Description
import net.nevinsky.abyssus.uitesting.screenshot.ScreenshotSuite

/**
 * Base for scenarios: one connection per class, a [FixtureProject] over the prepared copy, and a check at the end that
 * the committed fixture was not touched.
 */
abstract class UiTestBase {
    /** Moves the pointer off every control, then compares (or queues, or just keeps) a capture of [target]. */
    protected fun shot(scenario: String, target: com.intellij.remoterobot.fixtures.ComponentFixture, focusTree: Boolean = true) {
        val frame = robot.find<IdeFrame>(java.time.Duration.ofSeconds(10))
        if (focusTree) frame.focusTree()
        frame.movePointerAway()
        shots.check(scenario, target)
    }

    /** Every test starts from the pristine project on disk and a freshly shown Abyssus tree. */
    @Before
    fun resetProjectAndView() {
        project.restore()
        ide.refresh(project.dir.toString())
        ide.refreshProjectView()
        robot.find<IdeFrame>(java.time.Duration.ofSeconds(10)).reopenAbyssusView()
    }

    @After
    fun closeStrayDialogs() {
        runCatching { robot.find<IdeFrame>(java.time.Duration.ofSeconds(5)).closeStrayDialogs() }
    }

    /**
     * Undo of the plugin's scene edits is tied to the scene document, so open it in the text editor, press the IDE's
     * Undo shortcut there, wait for [restored] to be the document text, save, and wait for the disk to match.
     */
    protected fun undoThroughSceneDocument(scene: java.nio.file.Path, restored: String) {
        val path = scene.toString()
        val frame = robot.find<IdeFrame>(java.time.Duration.ofSeconds(10))
        ide.openTextEditor(path)
        try {
            com.intellij.remoterobot.utils.waitFor(java.time.Duration.ofSeconds(20), java.time.Duration.ofMillis(200), "the scene text editor has focus") { ide.editorHasFocus() }
            frame.undo()
            com.intellij.remoterobot.utils.waitFor(java.time.Duration.ofSeconds(15), java.time.Duration.ofMillis(300), "Undo restores the document") {
                ide.documentText(path) == restored
            }
            frame.saveAll()
            com.intellij.remoterobot.utils.waitFor(java.time.Duration.ofSeconds(10), java.time.Duration.ofMillis(300), "disk restored by Undo") {
                java.nio.file.Files.readString(scene) == restored
            }
        } finally {
            ide.closeFile(path)
        }
    }

    /** A scenario stuck on a blocked IDE fails instead of hanging the run. */
    @get:Rule(order = 0) val timeout: Timeout = Timeout.seconds(240)

    /** Screenshot checks; queued baseline updates are written only if the whole test passes. */
    protected val shots: ScreenshotSuite by lazy { ScreenshotSuite(ide, env.reportsDir) }

    @get:Rule(order = 2) val screenshotOutcome: TestWatcher = object : TestWatcher() {
        override fun succeeded(description: Description) = shots.commit()
        override fun failed(e: Throwable, description: Description) = shots.discard()
    }

    @get:Rule(order = 1) val diagnostics = FailureDiagnostics({ env }, { robot })

    companion object {
        lateinit var env: UiEnvironment
        lateinit var robot: RemoteRobot
        lateinit var project: FixtureProject
        lateinit var ide: IdeQueries
        private lateinit var sourceBefore: Map<String, String>

        @JvmStatic
        @BeforeClass
        fun connect() {
            env = UiEnvironment()
            project = FixtureProject(env.fixtureSource, env.requireProjectDir())
            sourceBefore = project.sourceHashes()
            robot = env.connect()
            ide = IdeQueries(robot)
        }

        @JvmStatic
        @AfterClass
        fun sourceFixtureUntouched() {
            assertEquals("the committed Untitled fixture changed during the run", sourceBefore, project.sourceHashes())
        }
    }
}
