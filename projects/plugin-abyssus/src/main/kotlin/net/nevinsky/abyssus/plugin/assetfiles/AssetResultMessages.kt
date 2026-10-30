/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.assetfiles

import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage

/** One outcome mapping; callers retain their existing cancellation and diagnostic wording. */
fun assetResultMessage(
    result: AssetCommandResult,
    cancelledKey: String = "uiAssetCancelled",
    conflict: (String) -> String = { it },
    failure: (Throwable) -> String = { it.documentDisplayMessage() },
): String? = when (result) {
    AssetCommandResult.Done -> null
    is AssetCommandResult.Collision -> AbyssusBundle.message("newTerrainNameError.EXISTS")
    is AssetCommandResult.Conflict -> conflict(result.path)
    is AssetCommandResult.Blocked -> result.reason
    AssetCommandResult.Cancelled -> AbyssusBundle.message(cancelledKey)
    is AssetCommandResult.Failed -> failure(result.cause)
}
