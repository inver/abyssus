/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** The terrain being painted: the Paint Texture toggle in the Properties panel starts it. */
@Service(Service.Level.PROJECT)
class PaintTextureRequests(project: Project) : BrushRequests(project, TOPIC) {
    companion object {
        @JvmField
        val TOPIC = brushTopic("Paint Texture brush")
    }
}
