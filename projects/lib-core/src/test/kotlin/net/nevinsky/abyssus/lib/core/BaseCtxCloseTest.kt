/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core

import net.nevinsky.abyssus.lib.gdx.testing.RecordingLogger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BaseCtxCloseTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test fun closeLeavesNoAssetPrepareThreadAlive() {
        val ctx = BaseCtx(temp.root.path, null, null, RecordingLogger())
        // start both threads of the pool and remember them: other tests' contexts may run threads of the same name
        val threads = Collections.synchronizedSet(HashSet<Thread>())
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        repeat(2) {
            ctx.executor.execute {
                threads += Thread.currentThread()
                started.countDown()
                release.await()
            }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertEquals(setOf("asset-prepare"), threads.map { it.name }.toSet())

        ctx.close()

        assertTrue("close waits for the pool to terminate", ctx.executor.isTerminated)
        // a terminated pool's worker may still be returning from its run loop: give it a moment, not a task
        threads.forEach { it.join(1000) }
        assertTrue("no asset-prepare thread of this context is alive", threads.none { it.isAlive })
    }

    @Test fun closeTwiceIsHarmless() {
        val ctx = BaseCtx(temp.root.path, null, null, RecordingLogger())
        ctx.close()
        ctx.close()
        assertTrue(ctx.executor.isShutdown)
    }
}
