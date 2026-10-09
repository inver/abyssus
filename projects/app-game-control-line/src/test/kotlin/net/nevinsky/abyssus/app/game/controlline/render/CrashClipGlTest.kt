/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.gdx.AnimationController
import net.nevinsky.abyssus.lib.gdx.ModelInstance
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.app.game.controlline.bundledProject
import net.nevinsky.abyssus.app.game.controlline.flight.CrashSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Each crash clip, played once on the built trainer as [FieldRenderer] plays it, ends with its parts moved. */
class CrashClipGlTest {
    @Before fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    @Test fun eachCrashClipPlaysOnceAndHoldsItsLastFrame() = TestGl.run {
        // the model's texture loads through Gdx.files, as in the game
        Gdx.files = Lwjgl3Files()
        val file = FileHandle(bundledProject().resolve("assets/model_trainer/model.glb").toFile())
        val loader = AssimpModelLoader()
        val model = loader.build(loader.loadData(file), file)
        try {
            val moved = mapOf(
                CrashSeverity.LITTLE to listOf("NoseWheel", "RightWheel"),
                CrashSeverity.MEDIUM to listOf("LeftWing", "Tail"),
                CrashSeverity.FULL to listOf("Propeller", "LeftWing", "RightWing", "Tail", "LeftWheel"),
            )
            for ((severity, parts) in moved) {
                val instance = ModelInstance(model)
                val rest = parts.associateWith { centre(instance, it) }
                val controller = AnimationController(instance)
                controller.setAnimation(severity.clip, 1)
                repeat(240) { controller.update(1f / 60f) }
                for (part in parts) {
                    val shift = centre(instance, part).dst(rest.getValue(part))
                    assertTrue("${severity.clip}: $part moved $shift m", shift > 0.01f)
                }
                val end = parts.associateWith { centre(instance, it) }
                controller.update(1f)
                for (part in parts) assertEquals("${severity.clip}: $part after the end", 0f, centre(instance, part).dst(end.getValue(part)), 1e-6f)
            }
        } finally {
            model.dispose()
        }
    }

    /** Where [node]'s mesh centre is in the model (its global transform applied to its bounds' centre). */
    private fun centre(instance: ModelInstance, node: String): Vector3 {
        val n = checkNotNull(instance.getNode(node)) { "no node $node" }
        val bounds = com.badlogic.gdx.math.collision.BoundingBox()
        n.parts.forEach { it.meshPart!!.mesh!!.calculateBoundingBox(bounds, it.meshPart!!.offset, it.meshPart!!.size) }
        return bounds.getCenter(Vector3()).mul(n.globalTransform)
    }
}
