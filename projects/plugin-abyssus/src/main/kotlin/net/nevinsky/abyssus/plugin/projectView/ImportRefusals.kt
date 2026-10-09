/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.editor.document.DocumentKind
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage

/**
 * Why [abss] cannot receive an import (its `.abss` is not a supported native document), or null. Shared by Import
 * FlightGear Aircraft and Import Model, which both refuse before any dialog opens; the file is only read.
 */
internal fun projectRefusal(abss: VirtualFile): String? {
    val core = service<AbyssusCore>()
    return runCatchingKeepingCancellation {
        core.format.requireSupported(core.json.readObject(textOf(abss)), DocumentKind.PROJECT)
    }.exceptionOrNull()?.let { with(it) { documentDisplayMessage() } }
}
