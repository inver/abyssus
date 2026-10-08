package net.nevinsky.abyssus.lib.runtime.ecs

import com.badlogic.ashley.core.Entity
import com.fasterxml.jackson.databind.InjectableValues
import net.nevinsky.abyssus.lib.core.ecs.EcsLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.lib.core.ecs.component.IdComponent
import net.nevinsky.abyssus.lib.runtime.ecs.render.RenderComponent
import net.nevinsky.abyssus.lib.runtime.ecs.render.AssetResolver
import net.nevinsky.abyssus.lib.core.io.EcsReadWarnings
import net.nevinsky.abyssus.lib.core.scene.SceneEcsDocument
import net.nevinsky.abyssus.lib.core.scene.SceneEngine
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class NativeEcsAdmissionTest {
    private val json = JsonProcessor()
    private val loader = EcsLoader(json.mapper, log = NOPLogger.NOP_LOGGER)
    private val writer = EcsWriter(json.mapper)

    @Test fun legacyFieldsRejectTheWholeBlockBeforeAddingEntities() {
        for (text in listOf(
            """{"entities":{"0":{"components":{}}},"componentIdentifiers":null}""",
            """{"0":{"components":{}},"1":{"components":{"RenderComponent":{"renderable":{"class":null}}}}}""",
            """{"entities":{"0":{"components":{}},"1":{"components":{"net.nevinsky.abyssus.lib.runtime.ecs.render.RenderComponent":{"renderable":{"class":"old"}}}}}}""",
        )) {
            val engine = SceneEngine()
            val existing = Entity().add(IdComponent(99))
            engine.addEntity(existing)
            engine.ids.register(99, existing)
            val node = json.readObject(text)
            val before = node.toString()
            assertThrows(UnsupportedDocumentFormat::class.java) { loader.load(node, engine) }
            assertEquals(1, engine.entities.size())
            assertSame(existing, engine.entities.first())
            assertEquals(setOf(99), engine.ids.ids)
            assertEquals(before, node.toString())
        }
    }

    @Test fun writerRejectsReservedExtrasAndDirectLegacyRenderPayloads() {
        val document = SceneEcsDocument(mapOf("componentIdentifiers" to json.readObject("{}")), emptyList(), wrapped = true)
        assertThrows(UnsupportedDocumentFormat::class.java) { writer.write(SceneEngine(), document) }
        val raw = json.readObject("""{"class":null,"kind":"asset"}""")
        assertThrows(IllegalArgumentException::class.java) { writer.writeComponent(RenderComponent(raw = raw)) }
        assertTrue(raw.has("class"))
        val reader = json.mapper.reader(InjectableValues.Std()
            .addValue(AssetResolver::class.java.name, AssetResolver { _, _ -> null })
            .addValue(EcsReadWarnings::class.java.name, EcsReadWarnings(NOPLogger.NOP_LOGGER)))
        val failure = assertThrows(Exception::class.java) {
            reader.forType(RenderComponent::class.java).readValue<RenderComponent>("""{"renderable":{"class":null}}""")
        }
        val reason = generateSequence(failure as Throwable) { it.cause }.last()
        assertTrue(reason is UnsupportedDocumentFormat)
    }

    @Test fun unknownComponentsAndMarkerPayloadsKeepTheirOpaqueFields() {
        val node = json.readObject("""{"entities":{"0":{"components":{"example.RenderComponent":{"renderable":{"class":"opaque"}},"WindComponent":{"componentIdentifiers":{},"class":"opaque"},"RenderComponent":{"renderable":{"kind":"marker","payload":{"class":"opaque"}}}}}},"metadata":{"class":"opaque"}}""")
        val engine = SceneEngine()
        val document = loader.load(node, engine)
        assertEquals(node, writer.write(engine, document))
    }

    @Test fun writerRejectsLegacyRenderablesCarriedUnderEitherBuiltInName() {
        for (name in listOf("RenderComponent", "net.nevinsky.abyssus.lib.runtime.ecs.render.RenderComponent")) {
            val engine = SceneEngine()
            engine.addEntity(Entity().add(IdComponent(0)))
            val carried = json.readObject("""{"renderable":{"class":null}}""")
            val document = SceneEcsDocument(emptyMap(), emptyList(), mapOf(0L to mapOf(name to carried)))
            assertThrows(UnsupportedDocumentFormat::class.java) { writer.write(engine, document) }
            assertTrue(carried["renderable"].has("class"))
        }
    }
}
