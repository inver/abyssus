package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

/**
 * The scene's saved ray tracing limits in Properties (RAY-04, RAY-05), which need neither a Scene View nor ray tracing
 * hardware: each of the five fields is committed with Enter into `rayTracing`, a bad value is explained and writes
 * nothing, and Undo restores the exact scene text. Whether the native image is correct is separate hardware evidence.
 */
class RayControlsUiTest : UiTestBase() {
    private val scene get() = project.resolve("scenes/Main Scene.scene")
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val mapper = ObjectMapper()

    @After
    fun closeProperties() {
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun saved(key: String): Int? = mapper.readTree(Files.readString(scene))["rayTracing"]?.get(key)?.takeIf { it.isInt }?.asInt()

    private fun openScene() {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        tree.expand("Scenes")
        val label = SceneFacts.read(scene).treeLabel
        frame.openProperties()
        tree.clickRow(label)
        LifecycleHarness.awaitState("scene Properties show the ray limits", true) { frame.properties().exists("ray-setting-targetSamplesPerPixel") }
    }

    @Test
    fun theFiveSavedLimitsAreEditedAndUndoneWithoutAViewOrHardware() {
        val before = Files.readString(scene)
        openScene()
        val limits = linkedMapOf("targetSamplesPerPixel" to 64, "maxRaysPerFrame" to 1000000, "maxReflectionBounces" to 3, "maxRefractionBounces" to 2, "maxInstances" to 512)
        var text = before
        val history = mutableListOf(before)
        for ((key, value) in limits) {
            frame.properties().enter("ray-setting-$key", value.toString())
            LifecycleHarness.awaitState("$key is saved", value) { saved(key) }
            assertFalse("$key leaves no error", frame.properties().labelText("ray-setting-$key-error").isNotBlank())
            assertTrue("each edit changes the text", Files.readString(scene) != text)
            text = Files.readString(scene)
            history += text
        }
        assertEquals("the view-less edits are the only change to the scene", limits.keys.toList(), limits.keys.filter { saved(it) == limits[it] })
        for (step in history.size - 2 downTo 0) undoThroughSceneDocument(scene, history[step])
        assertEquals("five Undos return the exact text", before, Files.readString(scene))
    }

    @Test
    fun invalidLimitsAreExplainedAndWriteNothing() {
        openScene()
        frame.properties().enter("ray-setting-maxReflectionBounces", "2")
        LifecycleHarness.awaitState("a valid value is saved", 2) { saved("maxReflectionBounces") }
        val valid = Files.readString(scene)
        for (bad in listOf("1.5", "many", "99999")) {
            frame.properties().enter("ray-setting-maxReflectionBounces", bad)
            LifecycleHarness.awaitState("'$bad' is explained", true) { frame.properties().labelText("ray-setting-maxReflectionBounces-error").isNotBlank() }
            assertEquals("'$bad' writes nothing", valid, Files.readString(scene))
        }
    }

    @Test
    fun requestingRayTracingWithNoViewOpensOneAndExplainsAnUnavailableBackendWithoutEditingTheScene() {
        val before = Files.readString(scene)
        openScene()
        assertFalse("tracing starts off", frame.properties().checked("ray-tracing-switch"))
        frame.properties().clickCheck("ray-tracing-switch")
        LifecycleHarness.awaitState("a reason or a status is shown for the request", true, Duration.ofSeconds(90)) {
            runCatching { frame.properties().labelText("ray-tracing-detail").isNotBlank() || frame.properties().labelText("ray-tracing-status").isNotBlank() }.getOrDefault(false)
        }
        val shown = frame.properties().labelText("ray-tracing-detail") + " | " + frame.properties().labelText("ray-tracing-status")
        assertTrue("an unavailable or failed mode names a reason: $shown", shown.replace("|", "").isNotBlank())
        assertEquals("requesting the mode never edits the scene", before, Files.readString(scene))
    }
}
