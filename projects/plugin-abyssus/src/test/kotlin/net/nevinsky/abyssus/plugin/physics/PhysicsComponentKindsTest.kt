package net.nevinsky.abyssus.plugin.physics

import net.nevinsky.abyssus.lib.gdx.editor.ResourceEditorMessages
import net.nevinsky.abyssus.lib.gdx.editor.components.ComponentEditor
import net.nevinsky.abyssus.lib.gdx.editor.components.EditResult
import net.nevinsky.abyssus.lib.gdx.editor.document.SceneJson
import org.junit.Assert.*
import org.junit.Test

class PhysicsComponentKindsTest {
    private val messages = ResourceEditorMessages()
    private val editor = ComponentEditor(messages, PhysicsComponentKinds(messages).kinds)
    private fun scene(component: String = "") = SceneJson().parse("""{"ecs":{"entities":{"0":{"components":{$component}},"1":{"components":{}}}}}""")

    @Test fun rigidBodyDefaultsAreOmittedAndChangesPreserveUnknownData() {
        val root = scene()
        assertEquals(EditResult.Changed, editor.add(root, "0", "RigidBodyComponent"))
        assertEquals("{}", root.at("/ecs/entities/0/components/RigidBodyComponent").toString())
        assertEquals("1", editor.read(root, "0", "RigidBodyComponent")!!.first { it.field == "mass" }.value)
        val preserved = scene(""""RigidBodyComponent":{"mass":1.000,"extension":{"n":0.123450}}""")
        assertEquals(EditResult.Changed, editor.update(preserved, "0", "RigidBodyComponent", "mass", "2"))
        assertEquals("""{"mass":2,"extension":{"n":0.123450}}""", preserved.at("/ecs/entities/0/components/RigidBodyComponent").toString())
    }

    @Test fun fullClassNameIsEditableAndDoesNotOfferDuplicate() {
        val name = "net.nevinsky.abyssus.lib.physics.ColliderComponent"
        val root = scene("\"$name\":{}")
        assertFalse(editor.missingKinds(root, "0").any { it.name == "ColliderComponent" })
        assertEquals(EditResult.Changed, editor.update(root, "0", name, "halfExtents.x", "1"))
        assertEquals(1, root.at("/ecs/entities/0/components").size())
        assertEquals(1, root.at("/ecs/entities/0/components/$name/halfExtents/x").intValue())
    }

    @Test fun invalidDimensionsMassDistanceAndReferencesAreRejected() {
        for ((kind, field, value) in listOf(Triple("RigidBodyComponent", "mass", "0"),
            Triple("ColliderComponent", "halfExtents.x", "0"), Triple("ColliderComponent", "radius", "NaN"),
            Triple("ColliderComponent", "halfHeight", "-1"), Triple("ConstraintComponent", "minDistance", "-1"),
            Triple("ConstraintComponent", "other", "999"))) {
            val root = scene("\"$kind\":{}")
            val before = root.toString()
            assertTrue("$kind $field", editor.update(root, "0", kind, field, value) is EditResult.Rejected)
            assertEquals(before, root.toString())
        }
        val root = scene(""""ConstraintComponent":{}""")
        assertEquals(EditResult.Changed, editor.update(root, "0", "ConstraintComponent", "other", "1"))
        assertEquals(1, root.at("/ecs/entities/0/components/ConstraintComponent/other").intValue())
    }
}
