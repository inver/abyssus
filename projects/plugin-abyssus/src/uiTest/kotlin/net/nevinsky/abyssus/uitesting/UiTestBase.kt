package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.RemoteRobot
import org.junit.After
import org.junit.Assume
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
    /** The project the IDE opened that this class is written for; the others skip (see ABYSSUS_UI_FIXTURE in scripts/run-ui-tests.sh). */
    protected open val projectName: String = "Untitled"

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
        Assume.assumeTrue("needs the $projectName project, the IDE opened ${project.source.fileName}", project.source.fileName.toString() == projectName)
        resetScenario()
    }

    /**
     * Brings the IDE and the disposable project back to the pristine state: dialogs and editors close first (which also
     * stops Play and background previews of those views), unsaved buffers are dropped next, and only then are the files
     * restored and refreshed. Restoring first would make the IDE see a file that changed under an edited buffer (a modal
     * "File Cache Conflict") and let that buffer be saved over the restored files or keep history for the next scenario.
     */
    protected fun resetScenario() {
        val frame = robot.find<IdeFrame>(java.time.Duration.ofSeconds(10))
        runCatching { frame.closeStrayDialogs() }
        reset.closeAllEditors()
        reset.discardUnsavedDocuments()
        project.restore()
        ide.refresh(project.dir.toString())
        reset.dropUndoHistory()
        ide.refreshProjectView()
        reset.reshowAbyssusView()
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
        lateinit var reset: IdeReset
        private lateinit var sourceBefore: Map<String, String>

        @JvmStatic
        @BeforeClass
        fun connect() {
            env = UiEnvironment()
            project = FixtureProject(env.fixtureSource, env.requireProjectDir())
            sourceBefore = project.sourceHashes()
            robot = env.connect()
            ide = IdeQueries(robot)
            reset = IdeReset(robot)
        }

        @JvmStatic
        @AfterClass
        fun sourceFixtureUntouched() {
            assertEquals("the committed Untitled fixture changed during the run", sourceBefore, project.sourceHashes())
            assertEquals("committed fixture profiles changed during the run", emptyList<java.nio.file.Path>(), project.untouchedProfiles())
        }
    }
}
