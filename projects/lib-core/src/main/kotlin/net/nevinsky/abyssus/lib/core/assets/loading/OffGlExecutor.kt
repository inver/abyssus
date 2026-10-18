/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class OffGlExecutor(val executor: ExecutorService, val virtualThreads: Boolean)

private val prepareThreadIds = AtomicInteger()

/**
 * Virtual threads when `Executors.newVirtualThreadPerTaskExecutor` exists and does not throw
 * (JDK 21+ desktop). Otherwise a daemon fixed pool named `asset-prepare-*`.
 * The public [AssetStorage] API does not change between the two.
 */
internal fun createOffGlExecutor(
    parallelism: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
): OffGlExecutor {
    try {
        val method = Executors::class.java.getMethod("newVirtualThreadPerTaskExecutor")
        val executor = method.invoke(null) as ExecutorService
        return OffGlExecutor(executor, virtualThreads = true)
    } catch (e: NoSuchMethodException) {
        return OffGlExecutor(platformPreparePool(parallelism), virtualThreads = false)
    } catch (e: UnsupportedOperationException) {
        return OffGlExecutor(platformPreparePool(parallelism), virtualThreads = false)
    } catch (e: InvocationTargetException) {
        val cause = e.cause
        if (cause is UnsupportedOperationException || cause is NoSuchMethodError) {
            return OffGlExecutor(platformPreparePool(parallelism), virtualThreads = false)
        }
        throw e
    } catch (e: NoSuchMethodError) {
        return OffGlExecutor(platformPreparePool(parallelism), virtualThreads = false)
    }
}

private fun platformPreparePool(parallelism: Int): ExecutorService =
    Executors.newFixedThreadPool(parallelism) { runnable ->
        Thread(runnable, "asset-prepare-${prepareThreadIds.incrementAndGet()}").apply { isDaemon = true }
    }

