/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.dto

/**
 * Like [runCatching], but lets IDE cancellation through. A read that is cancelled by a pending write action throws
 * `ProcessCanceledException` (e.g. `ReadAction.CannotReadException`); it must reach the platform, which retries the
 * work, instead of being reported as a problem with the file.
 */
inline fun <T> runCatchingKeepingCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
    throw e
} catch (e: kotlin.coroutines.cancellation.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
