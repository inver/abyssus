/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import java.io.File

/** The `assets` folder of the project in [projectDir], for telling which changed paths belong to its assets. */
class AssetsFolder(projectDir: File) {
    private val prefix = File(projectDir.absoluteFile, ASSETS_DIR).path + File.separator

    /** Whether [path] is the folder itself or anything below it. */
    fun holds(path: String): Boolean = (File(path).path + File.separator).startsWith(prefix)

    /** Whether [path] is below the folder. */
    fun contains(path: String): Boolean = File(path).path.startsWith(prefix)
}
