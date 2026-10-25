package net.nevinsky.abyssus.uitesting

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * Scene View gestures (GES-01..07) with real pointer input on the canvas, at coordinates computed from the view's
 * camera and the saved scene. It runs under the canvas's software GL (see `SceneViewSmokeUiTest`) and is skipped,
 * never passed, where no context exists.
 */
class SceneInteractionUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val scene get() = project.resolve(scenePath)
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val viewport get() = ViewportDriver(frame, ide)

    @After
    fun closeScene() {
        runCatching { ide.closeFile(scene.toString()) }
        runCatching { ide.setToolWindowWidth("Project", IdeFrame.PROJECT_WIDTH) }
    }

    /** Opens the scene in the Scene View with the Abyssus tree beside it and waits for a GL context. */
    private fun openView(): AbyssusTree {
        if (frame.propertiesWindow() != null) frame.closeProperties()
        frame.normalizeLayout(ide)
        val tree = frame.selectAbyssusView()
        tree.expand("Scenes")
        tree.expand(SceneFacts.read(scene).treeLabel)
        tree.expand("ecs")
        ide.openSceneView(scene.toString())
        val toolbar = frame.sceneToolbar().also { it.awaitShown() }
        viewport.awaitContext(toolbar)
        return tree
    }

    /** Clicks around [center] until the tree selects a row starting with [name]; returns where it hit. */
    private fun pickNear(tree: AbyssusTree, name: String, center: ViewPoint): ViewPoint? {
        val offsets = (0..4).flatMap { ring -> listOf(-ring, 0, ring).flatMap { dx -> listOf(-ring, 0, ring).map { dy -> dx * 14 to dy * 14 } } }.distinct()
        for ((dx, dy) in offsets) {
            val point = ViewPoint(center.x + dx, center.y + dy)
            viewport.click(point)
            if (poll(Duration.ofMillis(1200)) { tree.selectedRows().any { it == name || it.startsWith("$name ") } }) return point
        }
        return null
    }

    /** True once [condition] holds, polled for at most [timeout]; false otherwise (the caller asserts with its own message). */
    private fun poll(timeout: Duration, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        do {
            if (runCatching(condition).getOrDefault(false)) return true
            Thread.sleep(150)
        } while (System.nanoTime() < deadline)
        return false
    }

    @Test
    fun clickingAnObjectSelectsItAndClickingEmptySpaceClears() {
        val original = Files.readString(scene)
        val tree = openView()
        val model = viewport.screenOf(viewport.position(original, 0))
        val hit = pickNear(tree, "Model 0", model)
        assertTrue("a click near Model 0 at $model selected it in the tree (selected: ${tree.selectedRows()})", hit != null)
        assertEquals("the view selected the picked entity too", "0", viewport.viewSelection())
        // the top-left corner of the default framing is sky; the view clears its selection, the tree keeps its own
        viewport.click(ViewPoint(6, 6))
        assertTrue("a click on empty space cleared the view's selection (it holds ${viewport.viewSelection()})", poll(Duration.ofSeconds(3)) { viewport.viewSelection() == null })
        assertTrue("the Abyssus tree selection is unchanged (selected: ${tree.selectedRows()})", tree.selectedRows().any { it.startsWith("Model 0") })
        assertEquals("picking and clearing write nothing", original, Files.readString(scene))
    }

    /** Picks Model 0 and returns its saved text, the toolbar and the tree. */
    private fun pickModel(): Triple<String, SceneViewToolbar, AbyssusTree> {
        val original = Files.readString(scene)
        val tree = openView()
        val hit = pickNear(tree, "Model 0", viewport.screenOf(viewport.position(original, 0)))
        assertTrue("Model 0 was picked (selected: ${tree.selectedRows()})", hit != null)
        return Triple(original, frame.sceneToolbar(), tree)
    }

    private fun documentX(text: String) = viewport.position(text, 0).first

    @Test
    fun toolKeysAndButtonsSwitchTheToolAndNavigationLeavesTheSceneAlone() {
        val (original, toolbar, _) = pickModel()
        toolbar.click("Rotate (E)")
        assertTrue("Rotate is pressed", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Rotate (E)") && !toolbar.isPressed("Move (W)") })
        toolbar.click("Select (Q)")
        assertTrue("Select is pressed", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Select (Q)") })
        viewport.press(java.awt.event.KeyEvent.VK_W)
        assertTrue("W switches to Move", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Move (W)") })
        viewport.press(java.awt.event.KeyEvent.VK_E)
        assertTrue("E switches to Rotate", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Rotate (E)") })
        viewport.press(java.awt.event.KeyEvent.VK_Q)
        assertTrue("Q switches to Select", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Select (Q)") })

        // a drag that starts away from every handle orbits the camera
        val before = viewport.camera()
        viewport.drag(ViewPoint(80, 80), ViewPoint(160, 110))
        assertTrue("the drag orbited the camera", poll(Duration.ofSeconds(3)) { viewport.camera().yaw != before.yaw })
        assertEquals("navigation writes nothing", original, ide.documentText(scene.toString()))
        frame.saveAll()
        assertEquals("navigation writes nothing to the file", original, Files.readString(scene))
    }

    @Test
    fun dragAlongTheWorldXHandleMovesOnlyXAndUndoFromTheSceneViewRestoresIt() {
        val (original, toolbar, _) = pickModel()
        assertTrue("Move is the tool", toolbar.isPressed("Move (W)"))
        val (press, direction) = viewport.xHandle(viewport.position(original, 0))
        viewport.drag(press, ViewPoint(press.x + Math.round(direction.first * 40).toInt(), press.y + Math.round(direction.second * 40).toInt()))
        LifecycleHarness.awaitState("the drag moved the model (x changed)", true) { documentX(ide.documentText(scene.toString())!!) != documentX(original) }
        val moved = ide.documentText(scene.toString())!!
        val diff = LifecycleHarness.lineDiff(original, moved)
        assertEquals("one line changed: $diff", 1, diff.added.size)
        assertTrue("only x changed: $diff", diff.added.single().trim().startsWith("\"x\""))
        assertTrue("x grew toward the handle tip", documentX(moved) > documentX(original))

        viewport.canvas.click(java.awt.Point(6, 6)) // focus the canvas for the shortcut (empty space, no write)
        frame.undo()
        LifecycleHarness.awaitState("Undo from the Scene View restores the text", original) { ide.documentText(scene.toString()) }
        frame.redo()
        LifecycleHarness.awaitState("Redo reapplies the move", moved) { ide.documentText(scene.toString()) }
        frame.saveAll()
        LifecycleHarness.awaitState("the move is saved", moved) { Files.readString(scene) }
    }

    @Test
    fun snapQuantizesTheMoveAndEscapeCancelsADragWithoutAnEdit() {
        val (original, toolbar, _) = pickModel()
        toolbar.click("Snap 0.5")
        assertTrue("Snap is on", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Snap 0.5") })
        val (press, direction) = viewport.xHandle(viewport.position(original, 0))
        viewport.drag(press, ViewPoint(press.x + Math.round(direction.first * 47).toInt(), press.y + Math.round(direction.second * 47).toInt()))
        LifecycleHarness.awaitState("the snapped drag committed", true) { documentX(ide.documentText(scene.toString())!!) != documentX(original) }
        val snapped = ide.documentText(scene.toString())!!
        val x = documentX(snapped)
        assertEquals("x is a multiple of the 0.5 step ($x)", 0.0, Math.IEEEremainder(x.toDouble(), 0.5), 1e-4)

        // a second, independent drag cancelled with Escape: no edit, the pose stays
        val (again, dir2) = viewport.xHandle(viewport.position(snapped, 0))
        viewport.drag(again, ViewPoint(again.x + Math.round(dir2.first * 60).toInt(), again.y + Math.round(dir2.second * 60).toInt()), release = false)
        viewport.press(java.awt.event.KeyEvent.VK_ESCAPE)
        viewport.releaseMouse()
        Thread.sleep(700)
        assertEquals("the cancelled drag changed nothing", snapped, ide.documentText(scene.toString()))
    }

    private fun dragBy(press: ViewPoint, direction: Pair<Double, Double>, pixels: Int) =
        viewport.drag(press, ViewPoint(press.x + Math.round(direction.first * pixels).toInt(), press.y + Math.round(direction.second * pixels).toInt()))

    @Test
    fun rotatingAroundTheYRingChangesOnlyTheRotationAndUndoRestoresIt() {
        val (original, toolbar, _) = pickModel()
        toolbar.click("Rotate (E)")
        assertTrue("Rotate is the tool", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Rotate (E)") })
        val (press, direction) = viewport.yRing(viewport.position(original, 0))
        dragBy(press, direction, 45)
        LifecycleHarness.awaitState("the rotation was written", true) { ide.documentText(scene.toString())!!.contains("localRotation") && ide.documentText(scene.toString()) != original }
        val rotated = ide.documentText(scene.toString())!!
        assertTrue("only the rotation of entity 0 changed", LifecycleHarness.sameExcept(original, rotated, "/ecs/0/components/PositionComponent/localRotation"))
        viewport.canvas.click(java.awt.Point(6, 6))
        frame.undo()
        LifecycleHarness.awaitState("Undo from the Scene View restores the text", original) { ide.documentText(scene.toString()) }
    }

    @Test
    fun theLocalGizmoFollowsTheObjectsOwnAxes() {
        val original = Files.readString(scene)
        val tree = openView()
        tree.clickRow("Model 2")
        assertTrue("the view follows the tree selection", poll(Duration.ofSeconds(5)) { viewport.viewSelection() == "2" })
        val toolbar = frame.sceneToolbar()
        toolbar.choose("World", "Local")
        assertTrue("the Local space is shown", poll(Duration.ofSeconds(3)) { runCatching { toolbar.component.findAll<com.intellij.remoterobot.fixtures.ComponentFixture>(com.intellij.remoterobot.search.locators.byXpath("//div[@class='ComboBoxButton' and @visible_text='Local']")).isNotEmpty() }.getOrDefault(false) })
        val q = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parseObject(original).get("ecs").get("2").get("components").get("PositionComponent").get("localRotation")
        val local = net.nevinsky.abyssus.lib.core.editor.pick.gizmoAxes(
            net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace.LOCAL,
            com.badlogic.gdx.math.Quaternion(q.get("x").floatValue(), q.get("y").floatValue(), q.get("z").floatValue(), q.get("w").floatValue()),
        ).getValue(net.nevinsky.abyssus.lib.core.editor.pick.GizmoAxis.X)
        val origin = viewport.position(original, 2)
        val (press, direction) = viewport.xHandle(origin, Triple(local.x, local.y, local.z))
        dragBy(press, direction, 40)
        LifecycleHarness.awaitState("the local drag moved the model", true) { ide.documentText(scene.toString()) != original }
        val moved = viewport.position(ide.documentText(scene.toString())!!, 2)
        val delta = doubleArrayOf((moved.first - origin.first).toDouble(), (moved.second - origin.second).toDouble(), (moved.third - origin.third).toDouble())
        val length = Math.sqrt(delta.sumOf { it * it })
        val alignment = (delta[0] * local.x + delta[1] * local.y + delta[2] * local.z) / length
        assertTrue("the move follows the local X axis (alignment $alignment)", alignment > 0.99)
        assertTrue("only the position of entity 2 changed", LifecycleHarness.sameExcept(original, ide.documentText(scene.toString())!!, "/ecs/2/components/PositionComponent/localPosition"))
    }

    @Test
    fun frameSelectionOnlyMovesTheViewAndDropOnlyChangesHeight() {
        val original = Files.readString(scene)
        val tree = openView()
        val toolbar = frame.sceneToolbar()
        viewport.click(ViewPoint(6, 6)) // nothing selected in the view
        // without a selection it frames everything drawn, and still only the view moves
        val overview = viewport.camera()
        toolbar.click("Frame selection (F)")
        assertTrue("framing everything moved the camera", poll(Duration.ofSeconds(5)) { viewport.camera().let { it.target != overview.target || it.distance != overview.distance } })
        assertEquals("framing everything writes nothing", original, ide.documentText(scene.toString()))
        tree.clickRow("Model 2")
        assertTrue("the view follows the tree selection", poll(Duration.ofSeconds(5)) { viewport.viewSelection() == "2" })
        val before = viewport.camera()
        toolbar.click("Frame selection (F)")
        assertTrue("framing moved the camera", poll(Duration.ofSeconds(8)) { viewport.camera().let { it.target != before.target || it.distance != before.distance } })
        assertEquals("framing writes nothing", original, ide.documentText(scene.toString()))

        frame.find<com.intellij.remoterobot.fixtures.ComponentFixture>(com.intellij.remoterobot.search.locators.byXpath("//div[@class='JButton' and @text='Drop']"), Duration.ofSeconds(10)).click()
        LifecycleHarness.awaitState("Drop wrote the new height", true) { ide.documentText(scene.toString()) != original }
        val dropped = ide.documentText(scene.toString())!!
        assertTrue("only the position of entity 2 changed", LifecycleHarness.sameExcept(original, dropped, "/ecs/2/components/PositionComponent/localPosition"))
        val was = viewport.position(original, 2)
        val now = viewport.position(dropped, 2)
        assertTrue("Drop changed only y, to the resting height: $was -> $now", now.first == was.first && now.third == was.third && now.second != was.second)
        viewport.canvas.click(java.awt.Point(6, 6))
        frame.undo()
        LifecycleHarness.awaitState("Undo restores the text", original) { ide.documentText(scene.toString()) }
    }

    /** Opens the camera chooser (found by its tooltip) and picks [item]. */
    private fun chooseCamera(item: String) {
        // the combo and its inner button both carry the tooltip: click the outer one
        frame.findAll<com.intellij.remoterobot.fixtures.ComponentFixture>(
            com.intellij.remoterobot.search.locators.byXpath("//div[@tooltiptext='Look through a camera']"),
        ).first().click()
        // a popup that fits inside the window is lightweight, so look for its list anywhere in the frame
        frame.find<com.intellij.remoterobot.fixtures.JListFixture>(
            com.intellij.remoterobot.search.locators.byXpath("//div[@class='MyList' or @class='JBList' or @class='JList']"), Duration.ofSeconds(10),
        ).clickItem(item, fullMatch = true)
    }

    @Test
    fun theCameraChooserAndOrientationWidgetFollowTheCameraMode() {
        val original = Files.readString(scene)
        openView()
        val toolbar = frame.sceneToolbar()
        assertTrue("Frame selection is available with the free camera", toolbar.isEnabled("Frame selection (F)"))

        // the Y mark of the orientation widget turns the free camera to look along Y
        val free = viewport.camera()
        val rect = java.awt.Rectangle(free.width - 12 - 64, 12, 64, 64)
        val mark = net.nevinsky.abyssus.lib.core.editor.pick.OrientationProjection(free.yaw, free.pitch).marks()
            .first { it.axis == net.nevinsky.abyssus.lib.core.editor.pick.GizmoAxis.Y }
        viewport.click(ViewPoint(Math.round(rect.x + 32 + mark.x * 22f), Math.round(rect.y + 32 - mark.y * 22f)))
        assertTrue("the Y mark turned the camera (pitch ${free.pitch})", poll(Duration.ofSeconds(5)) { Math.abs(viewport.camera().pitch) > 1.2f })
        assertEquals("viewing writes nothing", original, ide.documentText(scene.toString()))

        // looking through Camera 4: the view takes the camera, framing is unavailable and the widget leaves the orbit alone
        chooseCamera("Camera 4")
        assertTrue("Frame selection is unavailable through a scene camera", poll(Duration.ofSeconds(5)) { !toolbar.isEnabled("Frame selection (F)") })
        val through = viewport.camera()
        viewport.click(ViewPoint(Math.round(rect.x + 32 + mark.x * 22f), Math.round(rect.y + 32 - mark.y * 22f)))
        Thread.sleep(700)
        assertEquals("the widget does nothing through a scene camera", through.pitch, viewport.camera().pitch, 1e-6f)
        assertEquals("no authored camera pose changed merely from viewing", original, ide.documentText(scene.toString()))

        chooseCamera("Free camera")
        assertTrue("Frame selection is available again", poll(Duration.ofSeconds(5)) { toolbar.isEnabled("Frame selection (F)") })
        frame.saveAll()
        assertEquals("switching cameras writes nothing", original, Files.readString(scene))
    }

    @Test
    fun movingCameraFourWritesBothPositionsAndItsRotateHandlesAreAbsent() {
        val original = Files.readString(scene)
        val tree = openView()
        tree.clickRow("Camera 4")
        assertTrue("the view follows the tree selection", poll(Duration.ofSeconds(5)) { viewport.viewSelection() == "4" })
        val toolbar = frame.sceneToolbar()
        toolbar.click("Move (W)")
        assertTrue("Move is the tool", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Move (W)") })
        val origin = viewport.position(original, 4)
        val (press, direction) = viewport.xHandle(origin, Triple(0f, 1f, 0f))
        dragBy(press, direction, 40)
        LifecycleHarness.awaitState("the drag moved Camera 4", true) { ide.documentText(scene.toString()) != original }
        val moved = ide.documentText(scene.toString())!!
        val camera = net.nevinsky.abyssus.lib.core.editor.document.SceneJson().parseObject(moved).get("ecs").get("4").get("components")
        val local = viewport.position(moved, 4)
        val stored = camera.get("CameraComponent").get("camera").get("position")
        assertTrue("localPosition.y changed (${origin.second} -> ${local.second})", local.second != origin.second)
        assertEquals("camera.position.y follows localPosition.y", local.second, stored.get("y").floatValue(), 1e-3f)
        assertEquals("x is untouched", origin.first, local.first, 1e-4f)
        assertEquals("z is untouched", origin.third, local.third, 1e-4f)

        // a look-at camera has no rotate rings: a drag on where the Y ring would be does not rotate it
        toolbar.click("Rotate (E)")
        assertTrue("Rotate is the tool", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Rotate (E)") })
        val (ring, along) = viewport.yRing(local)
        dragBy(ring, along, 45)
        Thread.sleep(700)
        assertEquals("rotating a look-at camera writes nothing", moved, ide.documentText(scene.toString()))
        viewport.canvas.click(java.awt.Point(6, 6))
        toolbar.click("Move (W)")
        frame.undo()
        LifecycleHarness.awaitState("Undo restores the text", original) { ide.documentText(scene.toString()) }
    }

    /** `PositionComponent.localRotation` of entity [id] as x, y, z, w (identity when omitted). */
    private fun rotation(text: String, id: Int): DoubleArray {
        val q = viewport.entity(text, id).get("components").get("PositionComponent").get("localRotation")
        fun c(n: String, d: Double) = q?.get(n)?.asDouble() ?: d
        return doubleArrayOf(c("x", 0.0), c("y", 0.0), c("z", 0.0), c("w", 1.0))
    }

    @Test
    fun angularSnapQuantizesARotationToFifteenDegrees() {
        val (original, toolbar, _) = pickModel()
        toolbar.click("Snap 0.5")
        assertTrue("Snap is on", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Snap 0.5") })
        toolbar.click("Rotate (E)")
        assertTrue("Rotate is the tool", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Rotate (E)") })
        val (press, direction) = viewport.yRing(viewport.position(original, 0))
        dragBy(press, direction, 53)
        LifecycleHarness.awaitState("the snapped rotation was written", true) { ide.documentText(scene.toString()) != original }
        val rotated = ide.documentText(scene.toString())!!
        assertTrue("only the rotation of entity 0 changed", LifecycleHarness.sameExcept(original, rotated, "/ecs/0/components/PositionComponent/localRotation"))
        val q = rotation(rotated, 0)
        assertEquals("a turn about Y only", 0.0, Math.abs(q[0]) + Math.abs(q[2]), 1e-3)
        val degrees = Math.toDegrees(2 * Math.atan2(q[1], q[3]))
        assertTrue("the angle is not zero ($degrees)", Math.abs(degrees) > 1.0)
        assertEquals("the angle is a multiple of 15 degrees ($degrees)", 0.0, Math.IEEEremainder(degrees, 15.0), 0.05)
    }

    @Test
    fun aLocalRotationTurnsAboutTheObjectsOwnAxis() {
        val original = Files.readString(scene)
        val tree = openView()
        tree.clickRow("Model 2")
        assertTrue("the view follows the tree selection", poll(Duration.ofSeconds(5)) { viewport.viewSelection() == "2" })
        val toolbar = frame.sceneToolbar()
        toolbar.choose("World", "Local")
        assertTrue("the Local space is shown", poll(Duration.ofSeconds(3)) { runCatching { toolbar.component.findAll<com.intellij.remoterobot.fixtures.ComponentFixture>(com.intellij.remoterobot.search.locators.byXpath("//div[@class='ComboBoxButton' and @visible_text='Local']")).isNotEmpty() }.getOrDefault(false) })
        toolbar.click("Rotate (E)")
        assertTrue("Rotate is the tool", poll(Duration.ofSeconds(3)) { toolbar.isPressed("Rotate (E)") })
        val before = rotation(original, 2)
        val localY = net.nevinsky.abyssus.lib.core.editor.pick.gizmoAxes(
            net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace.LOCAL,
            com.badlogic.gdx.math.Quaternion(before[0].toFloat(), before[1].toFloat(), before[2].toFloat(), before[3].toFloat()),
        ).getValue(net.nevinsky.abyssus.lib.core.editor.pick.GizmoAxis.Y)
        val (press, direction) = viewport.ring(viewport.position(original, 2), Triple(localY.x, localY.y, localY.z))
        dragBy(press, direction, 45)
        LifecycleHarness.awaitState("the local rotation was written", true) { ide.documentText(scene.toString()) != original }
        val rotated = ide.documentText(scene.toString())!!
        assertTrue("only the rotation of entity 2 changed", LifecycleHarness.sameExcept(original, rotated, "/ecs/2/components/PositionComponent/localRotation"))
        // relative = conjugate(before) * after must be a turn about the object's own Y axis
        val a = rotation(rotated, 2)
        val (bx, by, bz, bw) = before.let { listOf(-it[0], -it[1], -it[2], it[3]) }
        val rel = doubleArrayOf(
            bw * a[0] + bx * a[3] + by * a[2] - bz * a[1],
            bw * a[1] - bx * a[2] + by * a[3] + bz * a[0],
            bw * a[2] + bx * a[1] - by * a[0] + bz * a[3],
            bw * a[3] - bx * a[0] - by * a[1] - bz * a[2],
        )
        assertTrue("the object turned (${rel.toList()})", Math.abs(rel[1]) > 0.02)
        assertEquals("the turn is about the local Y axis only (${rel.toList()})", 0.0, Math.abs(rel[0]) + Math.abs(rel[2]), 2e-3)
    }
}
