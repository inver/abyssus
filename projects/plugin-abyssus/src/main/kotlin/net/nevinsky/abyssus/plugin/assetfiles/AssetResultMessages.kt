/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.assetfiles

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.io.File

/**
 * One outcome mapping; callers retain their existing cancellation and diagnostic wording. A null [cancelledKey] means a
 * cancelled command says nothing.
 */
fun assetResultMessage(
    result: AssetCommandResult,
    cancelledKey: String? = "uiAssetCancelled",
    conflict: (String) -> String = { it },
    failure: (Throwable) -> String = { it.documentDisplayMessage() },
    collision: (String) -> String = { AbyssusBundle.message("newTerrainNameError.EXISTS") },
    failed: (AssetCommandResult.Failed) -> String = { failure(it.cause) },
): String? = when (result) {
    AssetCommandResult.Done -> null
    is AssetCommandResult.Collision -> collision(result.path)
    is AssetCommandResult.Conflict -> conflict(result.path)
    is AssetCommandResult.Blocked -> result.reason
    AssetCommandResult.Cancelled -> cancelledKey?.let { AbyssusBundle.message(it) }
    is AssetCommandResult.Failed -> failed(result)
}

/**
 * The text a terrain brush stroke reports for [result]: [conflictKey] for a changed or colliding file, [failedKey] (given
 * the cause) for a failure, and nothing for a finished or cancelled stroke.
 */
fun strokeResultMessage(result: AssetCommandResult, conflictKey: String, failedKey: String): String? = assetResultMessage(
    result,
    cancelledKey = null,
    conflict = { AbyssusBundle.message(conflictKey) },
    collision = { AbyssusBundle.message(conflictKey) },
    failure = { AbyssusBundle.message(failedKey, it.documentDisplayMessage()) },
)

/** The `meta.json` of the asset [folder] and the data file [data] it names, as the VFS sees them after a write. */
fun assetFilesAffected(folder: VirtualFile, data: File): List<VirtualFile> =
    listOfNotNull(folder.findChild(META_FILE), LocalFileSystem.getInstance().refreshAndFindFileByIoFile(data))
