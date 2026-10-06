package net.nevinsky.abyssus.editor.document

import net.nevinsky.abyssus.editor.scene.sceneContentOf
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.editor.components.ComponentReader
import net.nevinsky.abyssus.runtime.ecs.component.PositionComponent
import net.nevinsky.abyssus.editor.scene.SceneContent
import net.nevinsky.abyssus.editor.parseScene
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

class SceneDocumentTest {
    private val decoder = ComponentReader(JsonProcessor().mapper, { _, _ -> null }, NOPLogger.NOP_LOGGER)
    private val text get() = File("src/test/testData/project/Untitled/scenes/Main Scene.scene").readText()

    @Test fun fixtureReadViewAgreesWithPinnedContentWithoutChangingText() {
        val root = SceneJson().parse(text)
        val before = SceneJson().inStyleOf(text, root)
        val document = SceneDocument(root, decoder)
        assertEquals((0..10).map(Int::toString), document.entities().map { it.id })
        val entity = document.entity("0")!!
        assertEquals("Model 0", entity.name)
        assertEquals(-3.035308f, entity.component(PositionComponent::class.java)!!.localPosition.x, 0f)
        val content = sceneContentOf(parseScene(text))
        assertEquals(content.models.first().assetName, document.renderAsset("0")!!.name)
        assertEquals(content.skybox, document.skybox())
        assertEquals(listOf("ecs", "0", "components"), document.locate("0"))
        assertNotNull(document.lookAtTarget("4"))
        assertNull(document.lookAtTarget("0"))
        assertEquals(before, SceneJson().inStyleOf(text, root))
    }

    @Test fun wrappedAddressAndExtensionPayloadAreRetained() {
        val text = """{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"7":{"components":{"PositionComponent":{},"WindComponent":{"gain":1.2300}}}},"archetypes":{}}}"""
        val root = SceneJson().parse(text)
        val document = SceneDocument(root, decoder)
        assertEquals(listOf("ecs", "entities", "7", "components"), document.locate("7"))
        assertEquals("7", document.entity("7")!!.name)
        assertEquals(0f, document.entity("7")!!.component(PositionComponent::class.java)!!.localPosition.x, 0f)
        assertNull(document.entity("missing"))
        assertEquals(text, SceneJson().inStyleOf(text, root))
    }

    @Test fun nativeAdmissionPrecedesEnumeration() {
        assertThrows(UnsupportedDocumentFormat::class.java) {
            SceneDocument(SceneJson().parse("""{"format":"abyssus","formatVersion":1,"ecs":{"componentIdentifiers":{}}}"""), decoder)
        }
    }
}
