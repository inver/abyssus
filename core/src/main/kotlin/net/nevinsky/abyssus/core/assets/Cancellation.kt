/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.assets

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but lets cancellation through: a [CancellationException] (which includes the IDE's
 * `ProcessCanceledException`) must reach whoever cancelled the work instead of being reported as a broken file.
 */
inline fun <T> runCatchingKeepingCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
