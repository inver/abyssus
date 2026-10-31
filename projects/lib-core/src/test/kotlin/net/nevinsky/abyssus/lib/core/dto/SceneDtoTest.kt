package net.nevinsky.abyssus.lib.core.dto

import com.badlogic.ashley.core.Component
import org.junit.Assert.*
import org.junit.Test
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent

class SceneDtoTest {
    private fun scene(vararg entries: Pair<Long, List<Component>>) =
        SceneDto(ecs = entries.associate { (id, parts) -> id to EntityDto(parts.toMutableList()) }.toMutableMap())

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
}
