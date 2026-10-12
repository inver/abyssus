/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.vfs.VirtualFile

/** Opens the Game UI editor; the tree knows only this interface, so `projectView` does not depend on `gameui`. */
interface UiScreenOpener {
    /** Opens the editor tab of the UI asset whose `meta.json` is [metaFile], on [screenId] when given. */
    fun open(metaFile: VirtualFile, screenId: String?)
}
