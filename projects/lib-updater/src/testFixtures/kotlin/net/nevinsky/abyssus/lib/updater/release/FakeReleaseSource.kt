/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.release

/** A [ReleaseSource] that answers with [release], or fails with [failure], and counts the calls. */
class FakeReleaseSource(var release: Release? = null, var failure: ReleaseSourceException? = null) : ReleaseSource {
    var calls = 0
        private set

    override fun latestStable(): Release? {
        calls++
        failure?.let { throw it }
        return release
    }
}
