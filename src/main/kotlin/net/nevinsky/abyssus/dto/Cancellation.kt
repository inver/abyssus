/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
