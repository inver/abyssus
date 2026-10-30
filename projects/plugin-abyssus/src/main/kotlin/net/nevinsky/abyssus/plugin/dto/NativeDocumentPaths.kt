/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.META_FILE

/** Path classification only: actual document reads still validate the native header. */
fun isNativeDocumentPath(path: String): Boolean {
    val name = path.substringAfterLast('/')
    return name == META_FILE || name.substringAfterLast('.', "") in ProjectLayout.ASSET_EXTENSIONS
}
