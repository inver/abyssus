/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.projectView

/** One step of the walk that selects a tree row again by its entry path. */
enum class ReselectStep { FOUND, DESCEND, SKIP }

/**
 * Where the row with entry path [target] is relative to a row whose entry path is [entryPath] (a DTO row) or whose
 * asset folder is [assetPath] (an asset row); a row with neither is the view root.
 */
fun reselectStep(target: String, entryPath: String?, assetPath: String?): ReselectStep = when {
    entryPath == target -> ReselectStep.FOUND
    entryPath != null && target.startsWith("$entryPath/") -> ReselectStep.DESCEND
    assetPath != null && target.startsWith("$assetPath/") -> ReselectStep.DESCEND
    entryPath == null && assetPath == null -> ReselectStep.DESCEND // view root
    else -> ReselectStep.SKIP
}
