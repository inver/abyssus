package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Duration

class PropertiesUiTest : UiTestBase() {
    private val scenePath = "scenes/Main Scene.scene"
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))

    private fun scene() = SceneFacts.read(project.resolve(scenePath))

    /** The Abyssus tree with scene, entities and assets open, and the Properties window shown. */
    private fun tree(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.openProperties()
        tree.expand("Scenes")
        tree.expand(scene().treeLabel)
        tree.expand("ecs")
        tree.expand("Assets")
        frame.normalizeLayout(ide)
        return tree
    }

    private fun assetOfType(type: String): String = project.dir.resolve("assets").toFile().listFiles()!!
        .filter { it.isDirectory }.sortedBy { it.name }
        .first { Regex("\"type\"\\s*:\\s*\"$type\"").containsMatchIn(Files.readString(it.toPath().resolve("meta.json"))) }.name

    private fun waitForTexts(description: String, check: (List<String>) -> Boolean) =
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), description) { check(frame.properties().texts()) }

    private fun waitForPreviews() = waitFor(Duration.ofSeconds(30), Duration.ofMillis(500), "previews loaded") {
        val panel = frame.properties()
        panel.previewCount() > 0 && panel.unloadedPreviews().isEmpty()
    }

    @Test
    fun assetsRowShowsTheEmptyState() {
        tree().clickRow("Assets")
        waitForTexts("empty state") { texts -> texts.any { it.startsWith("Nothing to show") } }
        shot("props-empty", frame.properties().component)
    }

    @Test
    fun modelAndTerrainAssetsShowTheirMeta() {
        val tree = tree()
        for (type in listOf("MODEL", "TERRAIN")) {
            val asset = assetOfType(type)
            tree.clickRow(asset)
            waitForTexts("$asset meta") { texts -> asset in texts && type in texts }
            shot("props-asset-${type.lowercase()}", frame.properties().component)
        }
    }

    @Test
    fun skyboxAssetsShowPreviewsAndEditableFieldsOnlyWhereSupported() {
        val tree = tree()

        val cube = assetOfType("SKYBOX")
        tree.clickRow(cube)
        waitForTexts("cube skybox") { texts -> cube in texts && "SKYBOX" in texts }
        waitForPreviews()
        assertTrue("cube skybox has editable fields", frame.properties().fieldNames("asset-field-").isNotEmpty())
        shot("props-asset-sky-cube", frame.properties().component)

        val procedural = assetOfType("SKYBOX_PROCEDURAL")
        tree.clickRow(procedural)
        waitForTexts("procedural sky") { texts -> procedural in texts && "SKYBOX_PROCEDURAL" in texts }
        assertTrue("procedural sky has editable fields", frame.properties().fieldNames("asset-field-").isNotEmpty())
        shot("props-asset-sky-procedural", frame.properties().component)

        val hdr = assetOfType("SKYBOX_HDR")
        tree.clickRow(hdr)
        waitForTexts("hdr skybox") { texts -> hdr in texts && "SKYBOX_HDR" in texts }
        assertTrue("hdr skybox is read-only: ${frame.properties().texts()}", frame.properties().texts().any { it.endsWith("read-only") })
        assertTrue("hdr skybox has no editable fields", frame.properties().fieldNames("asset-field-").isEmpty())
        waitForPreviews()
        shot("props-asset-sky-hdr", frame.properties().component)
    }

    @Test
    fun entityShowsItsComponentsAndCameraItsFields() {
        val facts = scene()
        val tree = tree()
        val entityName = facts.entityName(0)!!
        tree.clickRow(entityName)
        waitForTexts("entity 0") { texts -> entityName in texts && "entity 0" in texts }
        assertEquals(entityName, frame.properties().fieldText("field-NameComponent-name"))
        shot("props-entity", frame.properties().component)

        tree.expand("Camera 4")
        tree.clickRow("Camera 4")
        waitForTexts("camera entity") { texts -> "Camera 4" in texts && "entity 4" in texts }
        val fields = frame.properties().fieldNames("field-CameraComponent-")
        assertTrue("camera fields in $fields", "field-CameraComponent-camera.fieldOfView" in fields)
        tree.clickRow("Camera") // the Camera component row under Camera 4
        waitForTexts("camera component") { texts -> "camera.fieldOfView" in texts }
        shot("props-camera", frame.properties().component)
    }

    @Test
    fun entityNameEditCommitsAndUndoRestoresTheBytes() {
        val original = Files.readString(project.resolve(scenePath))
        val facts = SceneFacts(original)
        val oldName = facts.entityName(0)!!
        assertEquals(1, Regex("\"name\": \"$oldName\"").findAll(original).count())
        val tree = tree()
        tree.clickRow(oldName)
        waitForTexts("entity 0") { texts -> "entity 0" in texts }

        frame.properties().enter("field-NameComponent-name", "Renamed 0")
        val expected = original.replace("\"name\": \"$oldName\"", "\"name\": \"Renamed 0\"")
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "disk shows only the entity name changed") {
            Files.readString(project.resolve(scenePath)) == expected
        }

        ide.openTextEditor(project.resolve(scenePath).toString())
        try {
            frame.undo()
            waitFor(Duration.ofSeconds(15), Duration.ofMillis(300), "Undo restores the document") {
                ide.documentText(project.resolve(scenePath).toString()) == original
            }
            frame.saveAll()
            waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "disk restored by Undo") {
                Files.readString(project.resolve(scenePath)) == original
            }
        } finally {
            ide.closeFile(project.resolve(scenePath).toString())
        }
    }

    @Test
    fun invalidCameraNumberShowsAFieldErrorWithoutEditingTheScene() {
        val original = Files.readString(project.resolve(scenePath))
        val tree = tree()
        tree.expand("Camera 4")
        tree.clickRow("Camera") // the Camera component row: only the Camera section, so the error is in view
        waitForTexts("camera component") { texts -> "camera.fieldOfView" in texts }

        frame.properties().enter("field-CameraComponent-camera.fieldOfView", "not a number")
        waitFor(Duration.ofSeconds(10), Duration.ofMillis(300), "field error shown") {
            frame.properties().errorText("CameraComponent", "camera.fieldOfView").isNotBlank()
        }
        assertEquals(original, Files.readString(project.resolve(scenePath)))
        assertFalse(ide.documentText(project.resolve(scenePath).toString())?.contains("not a number") ?: false)
        shot("props-camera-error", frame.properties().component) // shot() moves focus to the tree, the error stays
    }
}
