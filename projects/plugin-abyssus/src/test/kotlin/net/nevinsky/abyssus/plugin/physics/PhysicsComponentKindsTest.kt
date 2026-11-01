package net.nevinsky.abyssus.plugin.physics

import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.ecs.ComponentRegistry
import net.nevinsky.abyssus.lib.core.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.core.editor.components.ComponentEditor
import net.nevinsky.abyssus.lib.core.editor.components.EditResult
import net.nevinsky.abyssus.lib.core.testing.sceneWithEcs
import net.nevinsky.abyssus.lib.physics.ColliderComponent
import net.nevinsky.abyssus.lib.physics.ConstraintComponent
import net.nevinsky.abyssus.lib.physics.PHYSICS_COMPONENTS
import net.nevinsky.abyssus.lib.physics.RigidBodyComponent
import org.junit.Assert.*
import org.junit.Test

class PhysicsComponentKindsTest {
    private val messages = ResourceEditorMessages()
    private val editor = ComponentEditor(messages, PhysicsComponentKinds(messages).kinds)
    private val registry = ComponentRegistry().also { r -> r.registerAll(PHYSICS_COMPONENTS); PHYSICS_COMPONENTS.values.forEach { r.register(it.name, it) } }
    private fun scene(component: String = ""): SceneDto =
        sceneWithEcs("""{"0":{"components":{$component}},"1":{"components":{}}}""", registry)

    private fun SceneDto.body() = entity(0)!!.component(RigidBodyComponent::class.java)!!
    private fun SceneDto.collider() = entity(0)!!.component(ColliderComponent::class.java)!!

    @Test fun rigidBodyIsAddedWithDefaultsAndChangesTheField() {
        val root = scene()
        assertEquals(EditResult.Changed, editor.add(root, 0L, "RigidBodyComponent"))
        assertEquals(1f, root.body().mass, 0f)
        assertEquals("1", editor.read(root, 0L, "RigidBodyComponent")!!.first { it.field == "mass" }.value)
        assertEquals(EditResult.Changed, editor.update(root, 0L, "RigidBodyComponent", "mass", "2"))
        assertEquals(2f, root.body().mass, 0f)
    }

    @Test fun unitsComeFromTheKinds() {
        val root = scene(""""RigidBodyComponent":{"mass":0.5},"ColliderComponent":{"shape":"SPHERE","radius":0.15}""")
        val body = editor.read(root, 0L, "RigidBodyComponent")!!.associateBy { it.field }
        assertEquals("kg", body.getValue("mass").unit)
        assertEquals("", body.getValue("friction").unit)
        val collider = editor.read(root, 0L, "ColliderComponent")!!.associateBy { it.field }
        assertEquals("m", collider.getValue("radius").unit)
    }

    @Test fun nestedFieldsAreEditedInPlace() {
        val root = scene(""""ColliderComponent":{"offset":{"x":1},"halfExtents":{"x":1,"y":0.5}}""")
        assertEquals(EditResult.Changed, editor.update(root, 0L, "ColliderComponent", "offset.x", "0"))
        assertEquals(0f, root.collider().offset.x, 0f)
        assertEquals(EditResult.Changed, editor.update(root, 0L, "ColliderComponent", "halfExtents.x", "0.5"))
        assertEquals(0.5f, root.collider().halfExtents.x, 0f)
        assertEquals(0.5f, root.collider().halfExtents.y, 0f)
    }

    @Test fun fullClassNameIsEditableAndDoesNotOfferDuplicate() {
        val name = "net.nevinsky.abyssus.lib.physics.ColliderComponent"
        val root = scene("\"$name\":{}")
        assertFalse(editor.missingKinds(root, 0L).any { it.name == "ColliderComponent" })
        assertEquals(EditResult.Changed, editor.update(root, 0L, name, "halfExtents.x", "1"))
        assertEquals(1, root.entity(0)!!.components.count { it is ColliderComponent })
        assertEquals(1f, root.collider().halfExtents.x, 0f)
    }

    @Test fun invalidDimensionsMassDistanceAndReferencesAreRejected() {
        for ((kind, field, value) in listOf(Triple("RigidBodyComponent", "mass", "0"),
            Triple("ColliderComponent", "halfExtents.x", "0"), Triple("ColliderComponent", "radius", "NaN"),
            Triple("ColliderComponent", "halfHeight", "-1"), Triple("ConstraintComponent", "minDistance", "-1"),
            Triple("ConstraintComponent", "other", "999"))) {
            val root = scene("\"$kind\":{}")
            val before = net.nevinsky.abyssus.plugin.printSceneWith(registry, root)
            assertTrue("$kind $field", editor.update(root, 0L, kind, field, value) is EditResult.Rejected)
            assertEquals(before, net.nevinsky.abyssus.plugin.printSceneWith(registry, root))
        }
        val root = scene(""""ConstraintComponent":{}""")
        assertEquals(EditResult.Changed, editor.update(root, 0L, "ConstraintComponent", "other", "1"))
        assertEquals(1, root.entity(0)!!.component(ConstraintComponent::class.java)!!.other)
    }
}
