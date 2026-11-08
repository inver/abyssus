package net.nevinsky.abyssus.plugin.sceneview

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import org.junit.Assert.*
import org.junit.Test

/** OpenSpec `model-part-hinges` and `scene-model-animation`: the preview is rest rotation x axisAngle(X, a). */
class HingePreviewTest {
    private fun model(): Model {
        val flap = Node().apply {
            id = "flap"
            translation.set(1f, 0f, 0f)
            rotation.set(Quaternion(Vector3.Z, 30f))
        }
        val tip = Node().apply {
            id = "tip"
            translation.set(0f, 1f, 0f)
        }
        flap.addChild(tip)
        return Model().apply { nodes.add(flap) }
    }

    @Test
    fun theNodeTurnsFromItsRestRotationAboutItsOwnX() {
        val instance = ModelInstance(model())
        previewHinge(instance, "flap", 20f)
        val expected = Quaternion(Vector3.Z, 30f).mul(Quaternion(Vector3.X, 20f))
        val actual = instance.getNode("flap").rotation
        assertEquals(1f, kotlin.math.abs(expected.dot(actual)), 1e-5f)
        // the child node follows: its world position is the node's frame applied to (0, 1, 0)
        val tip = instance.getNode("tip").globalTransform.getTranslation(Vector3())
        val want = Vector3(0f, 1f, 0f).mul(expected).add(1f, 0f, 0f)
        assertEquals(0f, want.dst(tip), 1e-5f)
    }

    @Test
    fun aPlayingAnimationDoesNotShowThroughForThePreviewedNode() {
        val instance = ModelInstance(model())
        instance.getNode("flap").apply {
            translation.set(5f, 5f, 5f) // as an animation would leave it
            rotation.set(Quaternion(Vector3.Y, 77f))
            isAnimated = true
        }
        previewHinge(instance, "flap", 15f)
        val node = instance.getNode("flap")
        assertEquals(0f, Vector3(1f, 0f, 0f).dst(node.translation), 1e-6f)
        assertEquals(1f, kotlin.math.abs(Quaternion(Vector3.Z, 30f).mul(Quaternion(Vector3.X, 15f)).dot(node.rotation)), 1e-5f)
        assertFalse(node.isAnimated)
    }

    @Test
    fun anUnknownNodeChangesNothing() {
        val instance = ModelInstance(model())
        previewHinge(instance, "nope", 15f)
        assertEquals(0f, Vector3(1f, 0f, 0f).dst(instance.getNode("flap").translation), 1e-6f)
    }
}
