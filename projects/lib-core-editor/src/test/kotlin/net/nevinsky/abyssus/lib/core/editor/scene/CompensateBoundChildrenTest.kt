/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import java.nio.file.Files
import net.nevinsky.abyssus.lib.core.assets.model.PartPivot
import net.nevinsky.abyssus.lib.core.assets.model.PartQuat
import net.nevinsky.abyssus.lib.core.assets.model.PartVec3
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.ecs.component.NameComponent
import net.nevinsky.abyssus.lib.core.ecs.component.ParentComponent
import net.nevinsky.abyssus.lib.core.ecs.component.PositionComponent
import net.nevinsky.abyssus.lib.core.editor.modelmesh.ModelMeshLister
import net.nevinsky.abyssus.lib.core.editor.modelmesh.pivotMatrix
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.format.EntityType
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CompensateBoundChildrenTest {
    private val dirs = mutableListOf<File>()

    @After fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private val field = File("../app-game-control-line/project/ControlLine/scenes/Field.scene")

    /** The pose of a child under a node: node rest x local. */
    private fun bound(position: PositionComponent, rest: Matrix4): Matrix4 =
        Matrix4(rest).mul(Matrix4().set(position.localPosition, position.localRotation, position.localScale))

    @Test
    fun aDummyBoundToTheFlapKeepsItsWorldPoseAndOthersAreUntouched() {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("compensate").toFile().also(dirs::add)
        assertTrue(
            File("../app-game-control-line/project/ControlLine/assets/model_trainer")
                .copyRecursively(File(root, "assets/model_trainer"))
        )
        val files = testFileLoader(root)
        val info = checkNotNull(ModelMeshLister(files, testMetaLoader(root, fileLoader = files)).parts("model_trainer"))

        val scene = parseScene(field.readText())
        val trainer = 5
        val onFlap = scene.add(EntityType.DUMMY) {
            listOf(NameComponent("On flap"), PositionComponent(), ParentComponent(trainer, "LeftFlap"))
        }
        val flapPose = scene.entity(onFlap)!!.component(PositionComponent::class.java)!!
        flapPose.localPosition.set(0.1f, 0.02f, -0.03f)
        flapPose.localRotation.set(Quaternion().setFromAxis(0f, 1f, 0f, 25f))
        val onWing = scene.add(EntityType.DUMMY) {
            listOf(NameComponent("On wing"), PositionComponent(), ParentComponent(trainer, "LeftWing"))
        }
        val wingPose = scene.entity(onWing)!!.component(PositionComponent::class.java)!!
        wingPose.localPosition.set(1f, 2f, 3f)
        val wingBefore = Vector3(wingPose.localPosition)

        val oldRest = Matrix4(info.rest.getValue("LeftFlap"))
        val pivot = PartPivot(PartVec3(0.05f, -0.01f, 0.02f), PartQuat(0f, 0f, 0.3826834f, 0.9238795f))
        val newRest = Matrix4(info.fileFrames.getValue("LeftFlap")).mul(pivotMatrix(pivot))

        val worldBefore = bound(flapPose, oldRest)
        assertTrue(CompensateBoundChildren("model_trainer", "LeftFlap", oldRest, newRest).apply(scene))
        val worldAfter = bound(flapPose, newRest)

        assertTrue(worldBefore.getTranslation(Vector3()).dst(worldAfter.getTranslation(Vector3())) < 1e-5f)
        val dot = worldBefore.getRotation(Quaternion(), true).dot(worldAfter.getRotation(Quaternion(), true))
        assertTrue(kotlin.math.abs(dot) > 1 - 1e-5f)
        assertEquals(wingBefore, wingPose.localPosition)
    }

    @Test
    fun aNoOpPivotChangeWritesNothing() {
        val scene = parseScene(field.readText())
        val rest = Matrix4().setTranslation(1f, 2f, 3f)
        assertFalse(CompensateBoundChildren("model_trainer", "LeftFlap", rest, Matrix4(rest)).apply(scene))
    }
}
