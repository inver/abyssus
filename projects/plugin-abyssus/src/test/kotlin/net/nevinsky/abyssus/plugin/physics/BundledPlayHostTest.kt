/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.physics

import net.nevinsky.abyssus.lib.physics.play.PlayFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BundledPlayHostTest {
    @Test
    fun theBundledPlayHostPlaysThePhysicsScene() {
        val project = File(checkNotNull(System.getProperty("abyssus.testData")), "project/Physics")
        val plan = PlayLaunch.choose(project, File(checkNotNull(System.getProperty("abyssus.playHost")))) as PlayLaunch.Plan
        assertEquals(PHYSICS_ONLY_MODULE, plan.module)
        val client = PlayProcessLauncher().launch(plan, project)
        try {
            val ready = CountDownLatch(1)
            client.onReady = { ready.countDown() }
            client.onFailed = { throw AssertionError("$it\n${client.tail.last()}") }
            client.start()
            client.send(PlayFrame.Load(File(project, "scenes/Main Scene.scene").readText(), project.path, -1))
            assertTrue("not ready: ${client.tail.last()}", ready.await(20, TimeUnit.SECONDS))
            client.send(PlayFrame.Play)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while ((client.poses()?.get("0")?.position?.y ?: 4f) >= 3f && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("Model 0 did not fall: ${client.tail.last()}", client.poses()!!.getValue("0").position.y < 3f)
        } finally {
            client.stop()
            assertTrue(client.process.waitFor(10, TimeUnit.SECONDS))
        }
    }
}
