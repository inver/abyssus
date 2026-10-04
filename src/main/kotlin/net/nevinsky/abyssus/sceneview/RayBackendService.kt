/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.dto.runCatchingKeepingCancellation
import net.nevinsky.abyssus.raytracing.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/**
 * Application owner of providers, devices and one serial native worker. Each view gets an independent session and
 * scheduler; a shared-device loss clears all their CPU publications before native cleanup. No shutdown waits on EDT.
 */
internal class RayBackendService(
    private val selector: RayBackendSelector,
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "abyssus-ray-worker").apply { isDaemon = true }
    },
    private val publish: (() -> Unit) -> Unit = { SwingUtilities.invokeLater(it) },
    private val clock: () -> Long = System::nanoTime,
    private val reportFailure: (Throwable) -> Unit = {},
) : AutoCloseable {
    private class Binding(
        val revision: Long, val backend: RayBackend, val session: RaySession, val scheduler: RayRenderScheduler,
        var prepared: Boolean = false,
    )
    private val closed = AtomicBoolean()
    private val views = ConcurrentHashMap<String, RayViewRuntime<*>>()
    // Only the native owner worker reads these collections.
    private val bindings = linkedMapOf<RayViewRuntime<*>, Binding>()
    private val backends = linkedSetOf<RayBackend>()
    private var pumping = false

    fun <T> newView(
        viewId: String,
        limits: RayLimits = RayLimits(),
        qualityLimits: RayQualityLimits = RayQualityLimits(),
    ): RayViewRuntime<T> {
        check(!closed.get()) { "Ray backend service is closed" }
        val view = RayViewRuntime<T>(this, viewId, limits, qualityLimits)
        require(views.putIfAbsent(viewId, view) == null) { "A ray view already owns this identity" }
        return view
    }

    internal fun activate(view: RayViewRuntime<*>, revision: Long, retry: Boolean) = execute {
        if (!view.wanted(revision)) return@execute
        disposeBinding(view)
        var preparingBackend: RayBackend? = null
        runCatchingKeepingCancellation {
            when (val selected = selector.select(retry)) {
                RayBackendSelection.Off -> publish {
                    view.mode.unavailable(revision, RayBackendSelection.Unavailable(listOf(RayBackendAttempt("off", RayUnavailableReason.DISABLED))))
                }
                is RayBackendSelection.Unavailable -> publish { view.mode.unavailable(revision, selected) }
                is RayBackendSelection.Selected -> {
                    val backend = selected.backend
                    preparingBackend = backend
                    backends += backend
                    if (!view.wanted(revision) || closed.get()) return@runCatchingKeepingCancellation
                    publish { view.mode.preparing(revision, backend.info) }
                    val pixelBudget = minOf(view.limits.maxPixels.toLong(), backend.capabilities.memoryBudgetBytes / 80)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val limits = view.limits.copy(maxDimension = minOf(view.limits.maxDimension, backend.capabilities.maxFrameDimension),
                        maxPixels = pixelBudget, maxInstances = backend.capabilities.maxInstances)
                    val session = backend.openSession(view.viewId, limits)
                    val quality = view.qualityLimits.copy(
                        maxDimension = minOf(view.qualityLimits.maxDimension, limits.maxDimension),
                        maxPixels = minOf(view.qualityLimits.maxPixels, limits.maxPixels.toLong()),
                        frameMemoryBytes = minOf(view.qualityLimits.frameMemoryBytes, backend.capabilities.memoryBudgetBytes),
                    )
                    val scheduler = RayRenderScheduler({ batch ->
                        when (val request = batch.request) {
                            is RayRequest -> session.submit(request)
                            is RaySceneRequest -> session.submit(request)
                            else -> error("Unsupported ray request type")
                        }
                    }, session::poll, RayQualityPolicy(quality), clock)
                    if (!view.install(scheduler, revision)) {
                        session.dispose()
                        return@runCatchingKeepingCancellation
                    }
                    bindings[view] = Binding(revision, backend, session, scheduler)
                    if (!pumping) {
                        pumping = true
                        worker.scheduleWithFixedDelay(::pump, 0, 1, TimeUnit.MILLISECONDS)
                    }
                }
            }
        }.onFailure { failure ->
            if (failure is RayDeviceLostException && preparingBackend != null && bindings[view] == null) {
                failBackend(checkNotNull(preparingBackend), failure)
            }
            failView(view, revision, failure)
        }
    }

    internal fun deactivate(view: RayViewRuntime<*>) = execute { disposeBinding(view) }
    internal fun remove(view: RayViewRuntime<*>) {
        views.remove(view.viewId, view)
        deactivate(view)
    }

    private fun execute(action: () -> Unit) {
        if (!closed.get()) worker.execute(action)
    }

    private fun pump() {
        if (closed.get()) return
        for ((view, binding) in bindings.toList()) {
            if (bindings[view] !== binding || !view.wanted(binding.revision)) continue
            runCatchingKeepingCancellation {
                binding.scheduler.pump()
                if (!binding.prepared && binding.scheduler.latest() != null) {
                    binding.prepared = true
                    publish { view.mode.prepared(binding.revision) }
                }
            }.onFailure { failure ->
                if (failure is RayDeviceLostException) failBackend(binding.backend, failure)
                else failView(view, binding.revision, failure)
            }
        }
    }

    private fun failView(view: RayViewRuntime<*>, revision: Long, failure: Throwable) {
        val binding = bindings[view]
        if (failure is RayDeviceLostException && binding != null) {
            failBackend(binding.backend, failure)
            return
        }
        if (view.wanted(revision)) {
            view.clearPublication()
            publish { view.mode.failed(revision, failure.message ?: failure.javaClass.simpleName) }
        }
        reportFailure(failure)
        disposeBinding(view)
    }

    private fun failBackend(backend: RayBackend, failure: Throwable) {
        selector.markDeviceLost(backend, failure.message)
        val affected = bindings.filterValues { it.backend === backend }.toList()
        // Invalidate every publication before starting cleanup, which may need to drain already submitted work.
        for ((view, binding) in affected) {
            view.clearPublication()
            publish { view.mode.failed(binding.revision, failure.message ?: failure.javaClass.simpleName) }
        }
        reportFailure(failure)
        affected.forEach { (view, _) -> disposeBinding(view) }
        backends.remove(backend)
        runCatchingKeepingCancellation { backend.dispose() }.onFailure(reportFailure)
    }

    private fun disposeBinding(view: RayViewRuntime<*>) {
        val binding = bindings.remove(view) ?: return
        binding.scheduler.cancel()
        runCatchingKeepingCancellation { binding.session.dispose() }.onFailure(reportFailure)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        views.values.forEach { it.close() }
        views.clear()
        worker.execute {
            bindings.keys.toList().forEach(::disposeBinding)
            backends.toList().forEach { backend -> runCatchingKeepingCancellation { backend.dispose() }.onFailure(reportFailure) }
            backends.clear()
            worker.shutdown()
        }
    }
}
