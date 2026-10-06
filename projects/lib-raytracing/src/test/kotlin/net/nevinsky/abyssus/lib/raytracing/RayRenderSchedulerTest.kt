/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.raytracing

import net.nevinsky.abyssus.lib.raytracing.RayDisplayKey
import net.nevinsky.abyssus.lib.raytracing.RayFrame
import net.nevinsky.abyssus.lib.raytracing.RayQualityLimits
import net.nevinsky.abyssus.lib.raytracing.RayQualityPolicy
import net.nevinsky.abyssus.lib.raytracing.RayRenderBatch
import net.nevinsky.abyssus.lib.raytracing.RayRenderInput
import net.nevinsky.abyssus.lib.raytracing.RaySceneRequest
import org.junit.Assert.*
import org.junit.Test

class RayRenderSchedulerTest {
    @Test fun sceneRequestsUseBoundedQualityAndKeepOriginalDisplayMetadata() {
        val batches = mutableListOf<RayRenderBatch>()
        var finished: RayFrame? = null
        val policy = RayQualityPolicy(RayQualityLimits(maxDimension = 4, minimumScale = .5, maxRaysPerFrame = 64))
        val scheduler = RayRenderScheduler({ batches += it }, { finished.also { finished = null } }, policy, { 0L })
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, .1f, 100f)
        val scene = RaySceneSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val request = RaySceneRequest(RayFrameKey(1, 1, 1, 1), 8, 8, camera, scene)
        val metadata = listOf("original camera", "original contents")
        val input = RayRenderInput(request, RayDisplayKey(8, 8, "orbit"), 1, 2, metadata)
        scheduler.offer(input); scheduler.pump()
        val first = batches.single().request as RaySceneRequest
        assertEquals(4, first.width)
        assertEquals(4, first.height)
        assertEquals(1, first.samples)
        assertEquals(0, first.sampleOffset)
        assertSame(scene, first.scene)
        finished = RayFrame(first.key, 4, 4, FloatArray(64), FloatArray(16) { 1f })
        scheduler.pump()
        assertSame(metadata, scheduler.latest()!!.batch.input.metadata)
        scheduler.offer(input); scheduler.pump()
        val second = batches.last().request as RaySceneRequest
        assertEquals(1, second.sampleOffset)
        assertEquals(first.accumulationEpoch, second.accumulationEpoch)
    }

    @Test fun continuousCameraAndPoseMotionPresentsOlderCompatibleFramesAndReplacesTheQueue() {
        val driver = Driver()
        driver.scheduler.offer(input(1)); driver.scheduler.pump()
        for (revision in 2L..1000L) driver.scheduler.offer(input(revision))
        assertEquals(1, driver.submitted.size)
        driver.finish(); driver.scheduler.pump()
        assertEquals(1L, driver.scheduler.latest()!!.frame.key.cameraRevision)
        assertEquals(1000L, driver.submitted.last().request.key.poseRevision)
        driver.scheduler.offer(input(1001)); driver.finish(); driver.scheduler.pump()
        assertEquals(1000L, driver.scheduler.latest()!!.frame.key.cameraRevision)
        assertEquals(3, driver.submitted.size)
    }

    @Test fun projectDeletionContextResizeAndCameraSwitchRejectOldFramesImmediately() {
        val changes = listOf(
            input(2, generation = 2), // project replacement or asset deletion advances structural generation
            input(2, context = 2), input(2, width = 6), input(2, cameraId = "camera-4"),
        )
        for (changed in changes) {
            val driver = Driver()
            driver.scheduler.offer(input(1)); driver.scheduler.pump(); driver.finish(); driver.scheduler.pump()
            assertNotNull(driver.scheduler.latest())
            driver.scheduler.offer(changed)
            assertNull(driver.scheduler.latest())
            driver.scheduler.pump(); driver.finish(); driver.scheduler.pump()
            assertNotNull(driver.scheduler.latest())
        }
    }

    @Test fun slowCompletionIsPolledWithoutAnotherSubmissionAndOldMotionHasAnAgeBound() {
        val driver = Driver()
        driver.scheduler.offer(input(1)); driver.scheduler.pump()
        driver.scheduler.offer(input(2))
        repeat(20) { driver.scheduler.pump() }
        assertEquals(1, driver.submitted.size)
        driver.now = 101_000_000L
        driver.finish(); driver.scheduler.pump()
        assertNull(driver.scheduler.latest())
        assertEquals(2L, driver.submitted.last().request.key.cameraRevision)
        driver.finish(); driver.scheduler.pump()
        driver.now += 1_000_000_000L
        assertNotNull("An unchanged still frame must not expire", driver.scheduler.latest())
    }

    @Test fun accumulationResetsForCameraPoseContentAndStructuralChanges() {
        val driver = Driver()
        driver.scheduler.offer(input(1)); driver.scheduler.pump()
        val initialEpoch = driver.submitted.last().accumulation.epoch
        driver.finish(); driver.scheduler.pump()
        driver.scheduler.offer(input(1)); driver.scheduler.pump()
        assertEquals(1, driver.submitted.last().accumulation.sampleOffset)
        driver.scheduler.offer(input(2))
        driver.finish(); driver.scheduler.pump()
        assertTrue(driver.submitted.last().accumulation.epoch > initialEpoch)
        assertEquals(0, driver.submitted.last().accumulation.sampleOffset)
        val motionEpoch = driver.submitted.last().accumulation.epoch
        driver.scheduler.offer(input(2, content = 3)) // lights, materials, environment or geometry revision
        driver.finish(); driver.scheduler.pump()
        assertTrue(driver.submitted.last().accumulation.epoch > motionEpoch)
        assertEquals(0, driver.submitted.last().accumulation.sampleOffset)
        assertEquals(1, driver.submitted.last().accumulation.sampleCount)
    }

    @Test fun cancellationDropsPendingAndLateCompletionEvenWhenReenabledWithTheSameScene() {
        val driver = Driver()
        driver.scheduler.offer(input(1)); driver.scheduler.pump(); driver.scheduler.offer(input(2))
        driver.scheduler.cancel()
        driver.scheduler.offer(input(1))
        driver.finish(); driver.scheduler.pump()
        assertNull(driver.scheduler.latest())
        assertEquals(2, driver.submitted.size)
        driver.scheduler.cancel(); driver.finish(); driver.scheduler.pump()
        assertNull(driver.scheduler.latest()); assertEquals(2, driver.submitted.size)
    }

    @Test fun offersDoNotCallTheNativeDriverAndPumpingStaysOnOneWorker() {
        val driver = Driver()
        val thread = Thread { repeat(1000) { driver.scheduler.offer(input(it.toLong())) } }
        thread.start(); thread.join()
        assertTrue(driver.submitted.isEmpty())
        driver.scheduler.pump()
        assertEquals(999L, driver.submitted.single().request.key.cameraRevision)
        var failure: Throwable? = null
        val otherWorker = Thread { try { driver.scheduler.pump() } catch (caught: Throwable) { failure = caught } }
        otherWorker.start(); otherWorker.join()
        assertTrue(failure is IllegalStateException)
    }

    @Test fun accumulationStopsAtItsBudgetAndMotionCanResumeRendering() {
        val driver = Driver(RayQualityPolicy(RayQualityLimits(minimumScale = 1.0, maxAccumulatedSamples = 3)))
        repeat(12) {
            driver.scheduler.offer(input(1)); driver.scheduler.pump()
            driver.finish(); driver.scheduler.pump()
        }
        assertEquals(3, driver.submitted.sumOf { it.accumulation.sampleCount })
        driver.scheduler.offer(input(2)); driver.scheduler.pump()
        assertEquals(0, driver.submitted.last().accumulation.sampleOffset)
        assertEquals(1, driver.submitted.last().accumulation.sampleCount)
    }

    @Test fun increasingInternalResolutionStartsNewAccumulationWithMatchedDisplayMetadata() {
        val driver = Driver()
        driver.scheduler.offer(input(1)); driver.scheduler.pump()
        val initial = driver.submitted.single()
        repeat(12) {
            driver.finish(); driver.scheduler.pump()
            driver.scheduler.offer(input(1)); driver.scheduler.pump()
        }
        val finer = driver.submitted.first { it.request.width > initial.request.width }
        assertEquals(0, finer.accumulation.sampleOffset)
        assertTrue(finer.accumulation.epoch > initial.accumulation.epoch)
        assertEquals(4, finer.input.display.width)
        assertEquals(1L, finer.input.request.key.cameraRevision)
    }

    @Test fun targetsOne257And4096AccumulateAcrossEightSampleBatchesWithClampedLastBatch() {
        for(target in listOf(1,257,4096)) {
            val driver=Driver(RayQualityPolicy(RayQualityLimits(minimumScale=1.0)))
            val saved=input(1).copy(settings=RayRenderSettings(target,67108864),settingsRevision=1)
            repeat(target+10) {
                driver.scheduler.offer(saved);driver.scheduler.pump();driver.finish();driver.scheduler.pump()
            }
            assertEquals(target,driver.submitted.sumOf { it.accumulation.sampleCount })
            assertTrue(driver.submitted.all { it.accumulation.sampleCount in 1..8 })
            assertEquals(target,driver.submitted.last().accumulation.let { it.sampleOffset+it.sampleCount })
        }
    }
    @Test fun settingsRevisionImmediatelyRejectsOldCompletedAndInflightFrames() {
        val driver=Driver()
        val old=input(1).copy(settings=RayRenderSettings(),settingsRevision=1)
        driver.scheduler.offer(old);driver.scheduler.pump()
        val changed=old.copy(settings=RayRenderSettings(512,4194304),settingsRevision=2)
        driver.scheduler.offer(changed);driver.finish();driver.scheduler.pump()
        assertNull(driver.scheduler.latest())
        assertEquals(0,driver.submitted.last().accumulation.sampleOffset)
        driver.finish();driver.scheduler.pump();assertNotNull(driver.scheduler.latest())
        driver.scheduler.offer(changed.copy(settingsRevision=3))
        assertNull(driver.scheduler.latest())
    }

    private class Driver(policy: RayQualityPolicy = RayQualityPolicy()) {
        var now = 0L
        val submitted = mutableListOf<RayRenderBatch>()
        private var completed: RayFrame? = null
        private var finished = 0
        val scheduler = RayRenderScheduler({ submitted.add(it) }, { completed.also { completed = null } }, policy, { now })
        fun finish() {
            if (submitted.size == finished) return
            finished = submitted.size
            val request = submitted.last().request
            completed = RayFrame(request.key, request.width, request.height, FloatArray(request.width * request.height * 4), FloatArray(request.width * request.height) { 1f })
        }
    }

    private fun input(revision: Long, generation: Long = 1, context: Long = 1, width: Int = 4, cameraId: String = "orbit", content: Long = 0): RayRenderInput {
        val mesh = RaySliceMesh(floatArrayOf(-1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), intArrayOf(0, 1, 2))
        val matrix = listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val camera = RaySliceCamera(listOf(0f, 0f, 2f), listOf(0f, 0f, -1f), listOf(0f, 1f, 0f), 60f, 0.1f, 100f)
        return RayRenderInput(RayRequest(RayFrameKey(generation, context, revision, revision), width, 4, camera,
            listOf(mesh), listOf(RaySliceInstance(0, matrix, listOf(1f, 1f, 1f)))), RayDisplayKey(width, 4, cameraId), content)
    }
}
