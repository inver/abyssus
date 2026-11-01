package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Component
import org.junit.Assert.*
import org.junit.Test
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent

class SceneDtoTest {
    private fun scene(vararg entries: Pair<Long, List<Component>>) =
        SceneDto(ecs = entries.associate { (id, parts) -> id to EntityDto(parts.toMutableList()) }.toMutableMap())

    @Test
    fun aPrintedSceneCarriesItsFormatVersionAndReadsBack() {
        val registry = net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry()
        val json = net.nevinsky.abyssus.lib.core.io.JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER, registry)
        val text = json.pretty(SceneDto(name = "A"))
        assertTrue(text, text.contains("\"formatVersion\": 1"))
        assertEquals("A", net.nevinsky.abyssus.lib.core.scene.SceneParser(json, registry).parse(text).name)
    }

    @Test
    fun nextIdOnEmptySceneIsOne() = assertEquals(1L, SceneDto().nextId())

    @Test
    fun nextIdIsLargestIdPlusOne() = assertEquals(8L, scene(3L to emptyList(), 7L to emptyList()).nextId())

    @Test
    fun nameOfMissingEntityIsNull() = assertNull(SceneDto().nameOf(5))

    @Test
    fun nameOfFallsBackToIdWithoutNameComponent() = assertEquals("4", scene(4L to emptyList()).nameOf(4))

    @Test
    fun nameOfFallsBackToIdWhenNameBlank() =
        assertEquals("4", scene(4L to listOf(NameComponent("  "))).nameOf(4))

    @Test
    fun nameOfUsesNameComponent() = assertEquals("Sun", scene(4L to listOf(NameComponent("Sun"))).nameOf(4))

    @Test
    fun componentFoundByClassAndMissingEntityIsNull() {
        val s = scene(1L to listOf(NameComponent("a")))
        assertNotNull(s.component(1, NameComponent::class.java))
        assertNull(s.component(2, NameComponent::class.java))
    }

    @Test
    fun addTakesTheNextIdAndPutsTheHeaderFirst() {
        val s = scene(7L to emptyList())
        val id = s.add(EntityType.OBJECT, NameComponent("x"))
        assertEquals(8L, id)
        val e = s.entity(id)!!
        assertEquals(id, e.header().id)
        assertEquals(EntityType.OBJECT, e.header().type)
        assertSame(e.components[0], e.header())
        assertEquals("x", s.nameOf(id))
    }

    @Test
    fun addOnAnEmptySceneStartsAtOne() = assertEquals(1L, SceneDto().add(EntityType.GROUP))

    @Test
    fun addTellsTheBuilderTheNewId() {
        val s = scene(4L to emptyList())
        val id = s.add(EntityType.OBJECT) { listOf(NameComponent("entity $it")) }
        assertEquals("entity 5", s.nameOf(id))
    }
}
