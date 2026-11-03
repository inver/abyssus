package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Physics on a copy of the Physics project (PHY-01, PHY-02): the project's Physics checkbox decides which typed kinds are
 * offered, a toggle is one undoable edit that keeps the stored component payloads, and a typed field of a physics
 * component is edited like a built-in one. Play itself needs the external play host and is covered separately.
 */
class PhysicsPlayLifecycleUiTest : UiTestBase() {
    override val projectName = "Physics"
    private val scene get() = project.resolve("scenes/Main Scene.scene")
    private val projectFile get() = project.resolve("Physics.abss")
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val mapper = ObjectMapper()

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun physicsEnabled(): Boolean? = mapper.readTree(Files.readString(projectFile))["physicsEnabled"]?.asBoolean()

    private fun components(id: Int) = mapper.readTree(Files.readString(scene))["ecs"][id.toString()]["components"]

    private fun openProject(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        frame.openProperties()
        tree.clickRow(tree.rows().first())
        LifecycleHarness.awaitState("project Properties show the Physics checkbox", true) { frame.properties().exists("physics-enabled") }
        return tree
    }

    private fun offeredKinds(tree: AbyssusTree, entityName: String): List<String> {
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        tree.rightClickRow(entityName)
        frame.clickMenuItem("Add Component...")
        val offered = frame.popupItems()
        frame.dismissPopup()
        return offered
    }

    private fun hasPhysicsKinds(offered: List<String>) = listOf("Rigid", "Collider", "Constraint").all { kind -> offered.any { it.startsWith(kind) } }

    @Test
    fun theProjectToggleDecidesWhichKindsAreOfferedAndKeepsStoredPayloads() {
        val sceneBefore = Files.readString(scene)
        val projectBefore = Files.readString(projectFile)
        val tree = openProject()
        assertTrue("the fixture project has physics on", frame.properties().checked("physics-enabled"))
        val entity = SceneFacts.read(scene).entityName(6) ?: tree.rows().first { it.startsWith("Model") }
        assertTrue("physics kinds are offered with physics on", hasPhysicsKinds(offeredKinds(tree, entity)))

        tree.clickRow(tree.rows().first())
        LifecycleHarness.awaitState("project Properties are back", true) { frame.properties().exists("physics-enabled") }
        frame.properties().clickCheck("physics-enabled")
        LifecycleHarness.awaitState("physics is saved off", false) { physicsEnabled() }
        assertFalse("the checkbox follows the file", frame.properties().checked("physics-enabled"))
        assertEquals("a project toggle never edits the scene", sceneBefore, Files.readString(scene))
        assertTrue("the stored RigidBody payload of Model 0 survives physics off", components(0).has("RigidBodyComponent"))
        assertFalse("no physics kind is offered with physics off", hasPhysicsKinds(offeredKinds(tree, entity)))

        tree.clickRow(tree.rows().first())
        LifecycleHarness.awaitState("project Properties are back again", true) { frame.properties().exists("physics-enabled") }
        frame.properties().clickCheck("physics-enabled")
        LifecycleHarness.awaitState("physics is saved on again", true) { physicsEnabled() }
        assertEquals("on, off, on leaves the project text as it was", projectBefore, Files.readString(projectFile))
        assertTrue("the kinds are offered again without reselecting anything", hasPhysicsKinds(offeredKinds(tree, entity)))
        assertEquals("the scene is still untouched", sceneBefore, Files.readString(scene))
    }

    @Test
    fun aPhysicsComponentFieldIsEditedRefusedAndUndoneLikeABuiltInOne() {
        val before = Files.readString(scene)
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        frame.openProperties()
        val name = SceneFacts.read(scene).entityName(0)!!
        tree.clickRow(name)
        LifecycleHarness.awaitState("Properties show Model 0", name) { frame.properties().headerTitle() }
        LifecycleHarness.awaitState("the Rigid body mass field is drawn", true) { frame.properties().exists("field-RigidBodyComponent-mass") }

        frame.properties().enter("field-RigidBodyComponent-mass", "5.5")
        LifecycleHarness.awaitState("mass 5.5 is saved", 5.5) { components(0)["RigidBodyComponent"]["mass"]?.asDouble() }
        val edited = Files.readString(scene)

        frame.properties().enter("field-RigidBodyComponent-mass", "heavy")
        LifecycleHarness.awaitState("text is refused with a reason", true) {
            runCatching { frame.properties().errorText("RigidBodyComponent", "mass") }.getOrDefault("").isNotBlank()
        }
        assertEquals("a refused value writes nothing", edited, Files.readString(scene))

        undoThroughSceneDocument(scene, before)
        assertEquals("Undo returns the exact text", before, Files.readString(scene))
    }

    private fun playButton(name: String) = frame.find<ComponentFixture>(byXpath("//div[@name='$name' and @class='JButton']"), Duration.ofSeconds(20))
    private fun enabled(name: String): Boolean = playButton(name).callJs("component.isEnabled()", runInEdt = true)
    private fun playStatus(): String = frame.findAll<ComponentFixture>(byXpath("//div[@class='JLabel' and (@visible_text='Playing' or @visible_text='Paused' or @visible_text='Starting…' or @visible_text='Play ended')]"))
        .firstOrNull()?.callJs<String>("component.getText()", runInEdt = true).orEmpty()

    @Test
    fun playPauseStepAndStopFollowThePhaseAndNeverWriteTheScene() {
        val before = Files.readString(scene)
        if (frame.propertiesWindow() != null) frame.closeProperties()
        frame.normalizeLayout(ide)
        ide.openSceneView(scene.toString())
        val toolbar = frame.sceneToolbar().also { it.awaitShown() }
        try {
            LifecycleHarness.awaitState("idle: only Play is enabled", listOf(true, false, false, false), Duration.ofSeconds(60)) {
                listOf(enabled("play"), enabled("pause"), enabled("step"), enabled("stop"))
            }
            playButton("play").click()
            LifecycleHarness.awaitState("the host is playing", "Playing", Duration.ofSeconds(120)) { playStatus() }
            assertEquals("playing: Pause and Stop", listOf(false, true, false, true), listOf(enabled("play"), enabled("pause"), enabled("step"), enabled("stop")))
            playButton("pause").click()
            LifecycleHarness.awaitState("paused", "Paused") { playStatus() }
            assertEquals("paused: Play, Step and Stop", listOf(true, false, true, true), listOf(enabled("play"), enabled("pause"), enabled("step"), enabled("stop")))
            playButton("step").click()
            LifecycleHarness.awaitState("a step keeps the pause", "Paused") { playStatus() }
            playButton("play").click()
            LifecycleHarness.awaitState("resumed", "Playing") { playStatus() }
            assertEquals("simulation never writes the scene", before, Files.readString(scene))
        } finally {
            runCatching { playButton("stop").click() }
        }
        LifecycleHarness.awaitState("stopped: idle again", listOf(true, false, false, false)) {
            listOf(enabled("play"), enabled("pause"), enabled("step"), enabled("stop"))
        }
        assertEquals("Stop restores the authored state; the scene bytes are unchanged", before, Files.readString(scene))
    }

    private fun hostProcesses(): Int = robot.callJs<Int>(
        "var n = 0; var it = java.lang.ProcessHandle.current().descendants().iterator();" +
            "while (it.hasNext()) { var c = it.next().info().commandLine().orElse(''); if (String(c).indexOf('play-host') >= 0) n++; } n",
        runInEdt = false,
    )

    @Test
    fun stopAndRestartRecoverTheControlsAndLeaveNoHostProcess() {
        val before = Files.readString(scene)
        if (frame.propertiesWindow() != null) frame.closeProperties()
        frame.normalizeLayout(ide)
        ide.openSceneView(scene.toString())
        frame.sceneToolbar().also { it.awaitShown() }
        LifecycleHarness.awaitState("idle", true, Duration.ofSeconds(60)) { enabled("play") }
        repeat(2) { round ->
            playButton("play").click()
            LifecycleHarness.awaitState("round $round plays", "Playing", Duration.ofSeconds(120)) { playStatus() }
            assertEquals("round $round owns one host process", 1, hostProcesses())
            playButton("stop").click()
            LifecycleHarness.awaitState("round $round stops: Play is enabled again", listOf(true, false, false, false)) {
                listOf(enabled("play"), enabled("pause"), enabled("step"), enabled("stop"))
            }
            LifecycleHarness.awaitState("round $round leaves no host process", 0, Duration.ofSeconds(30)) { hostProcesses() }
        }
        assertEquals("the scene bytes never changed", before, Files.readString(scene))
    }

    @Test
    fun authoringControlsAreOffWhilePlayingAndRecoverAfterStopWithoutSavingSimulatedPoses() {
        val before = Files.readString(scene)
        if (frame.propertiesWindow() != null) frame.closeProperties()
        frame.normalizeLayout(ide)
        ide.openSceneView(scene.toString())
        frame.sceneToolbar().also { it.awaitShown() }
        LifecycleHarness.awaitState("idle", true, Duration.ofSeconds(60)) { enabled("play") }
        LifecycleHarness.awaitState("Add Light is available while editing", true) { enabled("add-light") }
        playButton("play").click()
        LifecycleHarness.awaitState("playing", "Playing", Duration.ofSeconds(120)) { playStatus() }
        Thread.sleep(2000) // let bodies move so a saved simulated pose would differ
        assertFalse("Add Light is off while the simulation runs", enabled("add-light"))
        assertEquals("simulated poses are never written", before, Files.readString(scene))
        playButton("stop").click()
        LifecycleHarness.awaitState("Add Light recovers after Stop", true) { enabled("add-light") }
        playButton("add-light").click()
        frame.choosePopupItem("Directional")
        LifecycleHarness.awaitState("the edit is saved to the scene", true) { Files.readString(scene) != before }
        val after = Files.readString(scene)
        val diff = LifecycleHarness.lineDiff(before, after)
        assertTrue("only a new light entity was added, nothing removed or moved: $diff", diff.removed.none { it.contains("\"x\"") || it.contains("\"y\"") || it.contains("\"z\"") } && diff.added.any { it.contains("LIGHT_DIRECTIONAL") })
        LifecycleHarness.awaitState("no host process is left", 0, Duration.ofSeconds(30)) { hostProcesses() }
    }
}
