package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeAnimation
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.lib.gdx.model.ModelMesh
import net.nevinsky.abyssus.lib.gdx.model.ModelMeshPart
import org.junit.Assert.*
import org.junit.Test

class ModelPivotsTest {
    /** Root > Wing > Flap (own triangle) > Tip (child, no geometry); Wing has a triangle too. */
    private fun model(flapScale: Vector3? = null, sharedFlapMesh: Boolean = false, animateFlap: Boolean = false): ModelData {
        val data = ModelData()
        fun mesh(id: String, dx: Float): ModelMesh =
            ModelMesh().apply {
                this.id = id
                attributes = arrayOf(VertexAttribute.Position(), VertexAttribute.Normal())
                // three vertices, normal +Y
                vertices = floatArrayOf(dx, 0f, 0f, 0f, 1f, 0f, dx + 1f, 0f, 0f, 0f, 1f, 0f, dx, 0f, 1f, 0f, 1f, 0f)
                parts = arrayOf(ModelMeshPart().apply { this.id = "${id}_p"; indices = intArrayOf(0, 1, 2) })
            }
        data.addMesh(mesh("wingMesh", 0f))
        data.addMesh(mesh("flapMesh", 5f))
        fun part(meshId: String) = ModelNodePart().apply { meshPartId = "${meshId}_p"; materialId = "m" }
        val tip = ModelNode().apply {
            id = "Tip"
            translation = Vector3(1f, 2f, 3f)
            rotation = Quaternion().setFromAxis(0f, 1f, 0f, 20f)
        }
        val flap = ModelNode().apply {
            id = "Flap"
            translation = Vector3(0.5f, 0.1f, 0f)
            rotation = Quaternion().setFromAxis(0f, 0f, 1f, 30f)
            scale = flapScale
            parts = arrayOf(part("flapMesh"))
            children = arrayOf(tip)
        }
        val wing = ModelNode().apply {
            id = "Wing"
            translation = Vector3(10f, 0f, 0f)
            rotation = Quaternion().setFromAxis(1f, 0f, 0f, 15f)
            parts = arrayOf(part(if (sharedFlapMesh) "flapMesh" else "wingMesh"))
            children = arrayOf(flap)
        }
        data.nodes.add(ModelNode().apply { id = "Root"; children = arrayOf(wing) })
        if (animateFlap) {
            data.animations.add(
                ModelAnimation().apply {
                    id = "a"
                    nodeAnimations.add(ModelNodeAnimation().apply { nodeId = "Flap" })
                }
            )
        }
        return data
    }

    private fun local(node: ModelNode) =
        Matrix4().set(node.translation ?: Vector3(), node.rotation ?: Quaternion(), node.scale ?: Vector3(1f, 1f, 1f))

    private fun worlds(data: ModelData): Map<String, Matrix4> {
        val out = LinkedHashMap<String, Matrix4>()
        fun walk(node: ModelNode, parent: Matrix4) {
            val m = Matrix4(parent).mul(local(node))
            out[node.id!!] = m
            node.children?.forEach { walk(it, m) }
        }
        data.nodes.forEach { walk(it, Matrix4()) }
        return out
    }

    private fun worldVertices(data: ModelData): Map<String, List<Vector3>> {
        val worlds = worlds(data)
        val out = HashMap<String, List<Vector3>>()
        fun walk(node: ModelNode) {
            for (part in node.parts.orEmpty()) {
                val mesh = data.meshes.first { m -> m.parts.any { it.id == part.meshPartId } }
                out[node.id!!] = (0 until 3).map { i ->
                    Vector3(mesh.vertices[i * 6], mesh.vertices[i * 6 + 1], mesh.vertices[i * 6 + 2]).mul(worlds.getValue(node.id!!))
                }
            }
            node.children?.forEach(::walk)
        }
        data.nodes.forEach(::walk)
        return out
    }

    private fun worldNormals(data: ModelData, id: String): List<Vector3> {
        val node = worlds(data).getValue(id)
        val mesh = data.meshes.first { it.id == if (id == "Flap") "flapMesh" else "wingMesh" }
        val rot = node.getRotation(Quaternion())
        return (0 until 3).map { Vector3(mesh.vertices[it * 6 + 3], mesh.vertices[it * 6 + 4], mesh.vertices[it * 6 + 5]).mul(rot) }
    }

    private fun pivot(position: Vector3, rotation: Quaternion = Quaternion()) =
        ModelPartMeta(pivot = PartPivot(PartVec3(position.x, position.y, position.z), PartQuat(rotation.x, rotation.y, rotation.z, rotation.w)))

    private fun assertNear(a: Vector3, b: Vector3, eps: Float = 1e-5f) =
        assertTrue("$a vs $b", a.dst(b) <= eps)

    @Test
    fun aRotatedPivotKeepsGeometryAndNodesWhereTheyWere() {
        val before = model()
        val verticesBefore = worldVertices(before)
        val worldsBefore = worlds(before)
        val normalsBefore = worldNormals(before, "Flap")

        val after = model()
        val result = applyPivots(after, mapOf("Flap" to pivot(Vector3(0f, 0f, -0.02f), Quaternion().setFromAxis(0f, 0f, 1f, 90f))))
        assertEquals(listOf("Flap"), result.applied)
        assertTrue(result.refused.isEmpty())

        val verticesAfter = worldVertices(after)
        for ((id, list) in verticesBefore) list.forEachIndexed { i, v -> assertNear(v, verticesAfter.getValue(id)[i]) }
        val worldsAfter = worlds(after)
        assertNear(worldsBefore.getValue("Tip").getTranslation(Vector3()), worldsAfter.getValue("Tip").getTranslation(Vector3()))
        assertNear(worldsBefore.getValue("Wing").getTranslation(Vector3()), worldsAfter.getValue("Wing").getTranslation(Vector3()))
        worldNormals(after, "Flap").forEachIndexed { i, n -> assertNear(normalsBefore[i], n) }
        // Tip keeps its world rotation too
        val q1 = worldsBefore.getValue("Tip").getRotation(Quaternion())
        val q2 = worldsAfter.getValue("Tip").getRotation(Quaternion())
        assertTrue(abs(q1.dot(q2)) > 1 - 1e-5f)
    }

    private fun abs(f: Float) = kotlin.math.abs(f)

    @Test
    fun theNodeOriginAndAxisMoveToThePivot() {
        val before = model()
        val after = model()
        applyPivots(after, mapOf("Flap" to pivot(Vector3(0f, 0f, -0.02f), Quaternion().setFromAxis(0f, 0f, 1f, 90f))))
        val oldFrame = worlds(before).getValue("Flap")
        val newFrame = worlds(after).getValue("Flap")
        val expectedOrigin = Vector3(0f, 0f, -0.02f).mul(oldFrame)
        assertNear(expectedOrigin, newFrame.getTranslation(Vector3()))
        val oldY = Vector3(0f, 1f, 0f).mul(oldFrame.getRotation(Quaternion()))
        val newX = Vector3(1f, 0f, 0f).mul(newFrame.getRotation(Quaternion()))
        assertNear(oldY.nor(), newX.nor())
    }

    @Test
    fun badPivotsAreRefusedAndLeaveTheNodeUntouched() {
        val cases = listOf(
            Triple("NoSuchNode", model(), PivotRefusalReason.UNKNOWN_NODE),
            Triple("Flap", model(flapScale = Vector3(1f, 2f, 1f)), PivotRefusalReason.NON_UNIFORM_SCALE),
            Triple("Flap", model(sharedFlapMesh = true), PivotRefusalReason.SHARED_PART),
            Triple("Flap", model(animateFlap = true), PivotRefusalReason.ANIMATED),
        )
        for ((node, data, reason) in cases) {
            val before = worldVertices(data).mapValues { it.value.map(::Vector3) }
            val result = applyPivots(data, mapOf(node to pivot(Vector3(1f, 1f, 1f))))
            assertEquals(listOf(PivotRefusal(node, reason)), result.refused)
            assertTrue(result.applied.isEmpty())
            for ((id, list) in before) list.forEachIndexed { i, v -> assertNear(v, worldVertices(data).getValue(id)[i]) }
        }
    }

    @Test
    fun anUnscaledPartlessEntryChangesNothing() {
        val data = model()
        val result = applyPivots(data, mapOf("Flap" to ModelPartMeta(hinge = PartHinge(-1f, 1f))))
        assertEquals(PivotResult(emptyList(), emptyList()), result)
    }

    @Test
    fun hingeRules() {
        assertNull(hingeProblem(PartHinge(-35f, 35f)))
        assertNotNull(hingeProblem(PartHinge(10f, 35f)))
        assertNotNull(hingeProblem(PartHinge(-35f, -1f)))
        assertNotNull(hingeProblem(PartHinge(-190f, 0f)))
        assertNotNull(hingeProblem(PartHinge(Float.NaN, 0f)))
    }
}
