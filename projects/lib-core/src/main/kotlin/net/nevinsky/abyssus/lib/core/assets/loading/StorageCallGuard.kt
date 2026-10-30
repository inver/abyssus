/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import java.util.concurrent.atomic.AtomicReference

/** Binds callers to the render thread and rejects loader/hook reentry. */
internal class StorageCallGuard {
    private val glThread = AtomicReference<Thread?>(null)
    private var callDepth = 0

    fun bindThread() {
        val current = Thread.currentThread()
        while (true) {
            val expected = glThread.get()
            if (expected == null) {
                if (glThread.compareAndSet(null, current)) {
                    return
                }
            } else {
                if (expected === current) {
                    return
                }
                throw IllegalStateException(
                    "AssetStorage is bound to '${expected.name}' but was called from '${current.name}'",
                )
            }
        }
    }

    inline fun <T> onGl(block: () -> T): T {
        bindThread()
        check(callDepth == 0) { "AssetStorage must not be re-entered from a loader or a hook" }
        callDepth++
        try {
            return block()
        } finally {
            callDepth--
        }
    }

}
