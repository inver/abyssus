/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ArrayMap
import net.nevinsky.abyssus.core.assets.model.RayModelSkinning
import net.nevinsky.abyssus.core.assets.model.RayModelSnapshot
import net.nevinsky.abyssus.core.assets.model.ModelRaySnapshotLoader
import net.nevinsky.abyssus.core.assets.model.RayModelSource
import net.nevinsky.abyssus.core.AnimationController
import net.nevinsky.abyssus.core.ModelInstance
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.mesh.MeshPart
import net.nevinsky.abyssus.core.model.Model
import net.nevinsky.abyssus.core.model.ModelData
import net.nevinsky.abyssus.core.model.ModelMesh
import net.nevinsky.abyssus.core.model.ModelMeshPart
import net.nevinsky.abyssus.core.node.Animation
import net.nevinsky.abyssus.core.node.Node
import net.nevinsky.abyssus.core.node.NodeAnimation
import net.nevinsky.abyssus.core.node.NodePart
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The animation is a joint sliding from x = 0 to x = 2 in one second; the skinned part follows that joint. */
class RayAnimationSnapshotTest {
    init {
        com.badlogic.gdx.utils.GdxNativesLoader.load() // matrix products and the camera's frustum are native
    }

    @Test fun firstAnimationIsCapturedAtItsCurrentTime() {
        val entity = entity("a")
        entity.animation!!.update(.5f)
        val pose = RayModelPoses().capture(listOf(entity)).getValue("a")
        assertEquals(1f, pose.node("joint")!![12], 1e-5f)
        assertEquals(1f, pose.bones("skin", "part")!!.single()[12], 1e-5f)
    }

    @Test fun twoInstancesOfOneModelKeepIndependentPoses() {
        val model = model()
        val first = entity("first", model); val second = entity("second", model)
        first.animation!!.update(.25f); second.animation!!.update(.75f)
        val poses = RayModelPoses().capture(listOf(first, second))
        assertEquals(.5f, poses.getValue("first").bones("skin", "part")!!.single()[12], 1e-5f)
        assertEquals(1.5f, poses.getValue("second").bones("skin", "part")!!.single()[12], 1e-5f)
        // a captured pose is a copy: later animation or mutation of it never reaches the live instance
        poses.getValue("first").bones("skin", "part")!!.single()[12] = 99f
        assertEquals(.5f, poses.getValue("first").bones("skin", "part")!!.single()[12], 1e-5f)
        first.animation!!.update(.25f)
        assertEquals(.5f, poses.getValue("first").bones("skin", "part")!!.single()[12], 1e-5f)
    }

    @Test fun revisionAdvancesOnlyWhenThePoseChanges() {
        val entity = entity("a")
        val poses = RayModelPoses()
        entity.animation!!.update(.2f)
        val first = poses.capture(listOf(entity)).getValue("a").revision
        assertEquals(first, poses.capture(listOf(entity)).getValue("a").revision)
        entity.animation.update(.2f)
        assertTrue(poses.capture(listOf(entity)).getValue("a").revision > first)
    }

    @Test fun removedEntitiesLoseTheirPoseAndStaticOnesHaveNone() {
        val animated = entity("a"); val static = staticEntity("s")
        val poses = RayModelPoses()
        assertEquals(setOf("a"), poses.capture(listOf(animated, static)).keys)
        assertEquals(emptySet<String>(), poses.capture(listOf(static)).keys)
        assertEquals(setOf("a"), poses.capture(listOf(animated)).keys)
    }

    @Test fun jointDeformationMovesOnlyTheAnimatedInstancesSnapshotGeometry() {
        val snapshot = snapshot()
        val model = model()
        val moving = entity("moving", model); val still = entity("still", model)
        moving.animation!!.update(.75f)
        val poses = RayModelPoses().capture(listOf(moving, still))
        val content = SceneContent(models = listOf(
            AssetPlacement("moving", "model", PlacementTransform.IDENTITY), AssetPlacement("still", "model", PlacementTransform.IDENTITY)))
        val frame = (RaySceneSnapshots().capture(
            SceneRenderParams.DEFAULT.copy(content = content, projectDir = File("project")), camera(), LightSet.NONE,
            RaySceneAssetState.Ready(mapOf("model" to snapshot), emptyMap()), poses = poses,
            deform = { mesh, palette -> RayModelSkinning().deform(mesh, palette) },
        ) as RaySceneConversion.Ready).frame.scene
        val x = frame.instances.associate { it.id.substringBefore('/') to frame.meshes[it.mesh].positions()[0] }
        assertEquals(1.5f, x.getValue("moving"), 1e-5f)
        assertEquals(0f, x.getValue("still"), 1e-5f)
        assertEquals(2, frame.meshes.size)
    }

    private fun camera() = PerspectiveCamera(60f, 800f, 600f).apply { position.set(0f, 0f, 5f); lookAt(0f, 0f, 0f); update() }

    private fun staticEntity(id: String) = ModelEntity(placement(id), Model(), ModelInstance(Model()), null)
    private fun entity(id: String, model: Model = model()): ModelEntity {
        val instance = ModelInstance(model)
        val controller = AnimationController(instance).also { it.setAnimation("slide", -1) }
        return ModelEntity(placement(id), model, instance, controller)
    }
    private fun placement(id: String) = AssetPlacement(id, "model", PlacementTransform.IDENTITY)

    private fun model(): Model {
        val model = Model()
        val joint = Node().apply { this.id = "joint" }
        val skin = Node().apply { this.id = "skin" }
        val part = NodePart(MeshPart().apply { id = "part" }, Material())
        part.invBoneBindTransforms = ArrayMap<Node, Matrix4>(true, 1, Node::class.java, Matrix4::class.java).apply { put(joint, Matrix4()) }
        part.bones = Array<Matrix4>().apply { add(Matrix4()) }.toArray(Matrix4::class.java)
        skin.parts.add(part)
        model.nodes.add(joint); model.nodes.add(skin)
        val slide = NodeAnimation().apply {
            node = joint
            translation = Array<NodeKeyframe<Vector3>>().apply {
                add(NodeKeyframe(0f, Vector3(0f, 0f, 0f))); add(NodeKeyframe(1f, Vector3(2f, 0f, 0f)))
            }
        }
        model.animations.add(Animation("slide").apply { duration = 1f; nodeAnimations.add(slide) })
        model.calculateTransforms()
        return model
    }

    /** The same skinned asset as [model], read the way the scene view reads it: one joint bound to one vertex. */
    private fun snapshot(): RayModelSnapshot {
        val data = ModelData()
        data.meshes.add(ModelMesh().apply {
            id = "mesh"
            attributes = arrayOf(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.BoneWeight(0))
            // position, normal, (joint, weight): three vertices, all fully bound to joint 0
            vertices = floatArrayOf(
                0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f,
                0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
            )
            parts = arrayOf(ModelMeshPart().apply { id = "part"; primitiveType = GL20.GL_TRIANGLES; indices = intArrayOf(0, 1, 2) })
        })
        data.materials.add(ModelMaterial().apply { id = "mat"; diffuse = Color(Color.WHITE) })
        data.nodes.add(ModelNode().apply { id = "joint" })
        data.nodes.add(ModelNode().apply {
            id = "skin"
            parts = arrayOf(ModelNodePart().apply {
                meshPartId = "part"; materialId = "mat"
                bones = ArrayMap<String, Matrix4>().apply { put("joint", Matrix4()) }
            })
        })
        return ModelRaySnapshotLoader(net.nevinsky.abyssus.core.FileLoader(java.io.File(".")), AssimpModelLoader()).capture(RayModelSource(data, emptyMap()))
    }
}
