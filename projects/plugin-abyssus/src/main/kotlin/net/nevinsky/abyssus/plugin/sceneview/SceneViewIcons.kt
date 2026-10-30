/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * The stroke icons of the scene view's toolbar and tab (24 px grid, 1.8 stroke, drawn at 16 px). Each has a light
 * file `scene_view_<name>_icon.svg` and a `_dark` twin that the platform picks in dark themes.
 */
enum class SceneViewIcon(val resourceName: String) {
    SELECT("select"),
    MOVE("move"),
    ROTATE("rotate"),
    FRAME("frame"),
    SNAP("snap"),
    SPACE("space"),
    SHADING("shading"),
    SHADOWS("shadows"),
    FOG("fog"),
    GRID("grid"),
    WIREFRAME("wireframe"),
    CAMERA("camera"),
    PREVIEW("preview"),
    SOURCE("source");

    val path: String get() = "/icons/scene_view_${resourceName}_icon.svg"
    val darkPath: String get() = "/icons/scene_view_${resourceName}_icon_dark.svg"

    val icon: Icon get() = IconLoader.getIcon(path, SceneViewIcon::class.java)
}
