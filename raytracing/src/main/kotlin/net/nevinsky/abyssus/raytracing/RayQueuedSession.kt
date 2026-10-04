/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

class RayDeviceLostException(message: String) : IllegalStateException(message)

/** One per backend/device, accessed on its serial worker. Also the conformance kit's loss-injection hook. */
class RayDeviceHealth {
    private var loss: String? = null
    fun reportLost(reason: String) { loss = reason }
    fun checkUsable() { loss?.let { throw RayDeviceLostException(it) } }
}

/** Feasibility scheduling boundary. One submitted frame and one replaceable pending request per view. */
class RayQueuedSession(private val driver: RaySession, private val health: RayDeviceHealth) : RaySession {
    private val worker = Thread.currentThread()
    private var closed = false
    private var inFlight = false
    private var pending: Submission? = null
    private var latest: Submission? = null
    private class Submission(val key: RayFrameKey,val width:Int,val height:Int,val submit:()->Unit)

    private fun checkOwner() {
        check(Thread.currentThread() === worker && !closed) { "Ray session is closed or used from another worker" }
        health.checkUsable()
    }

    override fun submit(request: RayRequest) = offer(Submission(request.key,request.width,request.height) { driver.submit(request) })
    override fun submit(request: RaySceneRequest) = offer(Submission(request.key,request.width,request.height) { driver.submit(request) })
    private fun offer(request: Submission) {
        checkOwner()
        if (inFlight) pending = request else {
            request.submit()
            inFlight = true
        }
        latest = request
    }

    override val geometryBuilds: Long get() = driver.geometryBuilds

    override fun poll(): RayFrame? {
        checkOwner()
        if (!inFlight) return null
        val frame = try {
            driver.poll()
        } catch (lost: RayDeviceLostException) {
            health.reportLost(lost.message ?: "Ray device lost")
            throw lost
        } ?: return null
        inFlight = false
        val desired = latest!!
        val compatible = frame.key.sceneGeneration == desired.key.sceneGeneration &&
            frame.key.contextGeneration == desired.key.contextGeneration &&
            frame.width == desired.width && frame.height == desired.height
        val next = pending
        pending = null
        if (next != null) {
            next.submit()
            inFlight = true
        }
        return frame.takeIf { compatible }
    }

    override fun dispose() {
        check(Thread.currentThread() === worker) { "Ray session must stay on its owner worker" }
        if (closed) return
        closed = true
        pending = null
        latest = null
        driver.dispose()
    }
}
