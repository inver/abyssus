package net.nevinsky.abyssus.uitesting

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * GES-04 for a light aimed at a direction handle, on the Lights project (`Abyssus Lights.scene`: directional light 1 at
 * (0, 10, 0) looking at handle 0 at the origin). Runs only when the IDE opened a copy of that project:
 * `ABYSSUS_UI_FIXTURE=projects/plugin-abyssus/src/test/testData/project/Lights scripts/run-ui-tests-docker.sh --tests '*LightGestureUiTest'`.
 */
class LightGestureUiTest : UiTestBase() {
    override val projectName = "Lights"

    private val scenePath = "scenes/Abyssus Lights.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val viewport get() = ViewportDriver(frame, ide)

    @After
    fun closeScene() {
        runCatching { ide.closeFile(scene.toString()) }
        runCatching { ide.setToolWindowWidth("Project", IdeFrame.PROJECT_WIDTH) }
    }

    private fun poll(timeout: Duration, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        do {
            if (runCatching(condition).getOrDefault(false)) return true
            Thread.sleep(150)
        } while (System.nanoTime() < deadline)
        return false
    }

    /** Clicks around [center] until the view selects entity [id]. */
    private fun pickNear(id: String, center: ViewPoint): Boolean {
        val offsets = (0..4).flatMap { ring -> listOf(-ring, 0, ring).flatMap { dx -> listOf(-ring, 0, ring).map { dy -> dx * 14 to dy * 14 } } }.distinct()
        for ((dx, dy) in offsets) {
            val camera = viewport.camera()
            val x = center.x + dx
            val y = center.y + dy
            if (x < 1 || y < 1 || x >= camera.width - 1 || y >= camera.height - 1) continue
            viewport.click(ViewPoint(x, y))
            if (poll(Duration.ofMillis(1200)) { viewport.viewSelection() == id }) return true
        }
        return false
    }

    @Test
    fun turningADirectionalLightMovesItsHandleAndUndoRestoresIt() {
        val original = Files.readString(scene)
        if (frame.propertiesWindow() != null) frame.closeProperties()
        frame.normalizeLayout(ide)
        ide.openSceneView(scene.toString())
        val toolbar = frame.sceneToolbar().also { it.awaitShown() }
        viewport.awaitContext(toolbar)
        // the scene has no model to frame, so zoom out with the wheel until the light is well inside the view
        val start = viewport.camera()
        viewport.scroll(3)
        val zoomed = poll(Duration.ofSeconds(5)) { viewport.camera().distance != start.distance }
        assertTrue("the wheel zoomed the camera", zoomed)
        val farther = if (viewport.camera().distance > start.distance) 1 else -1 // the wheel direction that zooms out
        repeat(12) {
            val c = viewport.camera()
            val at = c.project(0f, 10f, 0f)
            if (at != null && at.x in 60 until c.width - 60 && at.y in 60 until c.height - 60) return@repeat
            viewport.scroll(3 * farther)
            Thread.sleep(300)
        }
        val opened = ide.documentText(scene.toString())!!
        val light = viewport.position(original, 1)
        assertTrue("directional light 1 was picked", pickNear("1", viewport.screenOf(light)))
        toolbar.click("Rotate (E)")
        assertTrue("Rotate is the tool", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Rotate (E)") })

        val (press, direction) = viewport.ring(light, Triple(1f, 0f, 0f))
        viewport.drag(press, ViewPoint(press.x + Math.round(direction.first * 60).toInt(), press.y + Math.round(direction.second * 60).toInt()))
        LifecycleHarness.awaitState("the turn was written", true) { ide.documentText(scene.toString()) != opened }
        val turned = ide.documentText(scene.toString())!!
        assertEquals("the light's own PositionComponent is unchanged", viewport.entity(original, 1).get("components").get("PositionComponent"), viewport.entity(turned, 1).get("components").get("PositionComponent"))
        assertTrue("only handle 0's PositionComponent changed", LifecycleHarness.sameExcept(original, turned, "/ecs/entities/0/components/PositionComponent"))
        val handle = viewport.position(turned, 0)
        assertEquals("a turn about X keeps the handle's x", 0f, handle.first, 1e-3f)
        val length = Math.sqrt(((handle.second - light.second) * (handle.second - light.second) + handle.third * handle.third).toDouble())
        assertEquals("the handle stays 10 away from the light ($handle)", 10.0, length, 0.05)
        assertTrue("the handle moved ($handle)", handle.second != 0f || handle.third != 0f)

        viewport.canvas.click(java.awt.Point(6, 6))
        frame.undo()
        // the fixture is compact JSON and the IDE's pretty-printing of an opened scene may change its layout, so compare the JSON
        LifecycleHarness.awaitState("Undo restores the content", true) { LifecycleHarness.sameExcept(opened, ide.documentText(scene.toString())!!, "/ecs/nothing") }
    }
}
