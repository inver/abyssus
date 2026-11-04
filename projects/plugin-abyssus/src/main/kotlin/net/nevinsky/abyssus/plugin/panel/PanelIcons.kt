/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.panel

import net.nevinsky.abyssus.plugin.filetype.*
import javax.swing.Icon

/** Maps the icon keys of the panel model (`lib-core-editor`'s `PanelIcons`) to the design's icon set. */
fun panelIcon(key: String?): Icon = when (key) {
    "project" -> AbyssusProjectIcons.PROJECT
    "fScenes" -> ScenesIcons.LIST
    "fUi" -> ScenesIcons.UI
    "fAssets" -> ScenesIcons.ASSETS
    "folder" -> ScenesIcons.FOLDER
    "scene" -> SceneIcons.FILE
    "uiscreen" -> ScenesIcons.UI_SCREEN
    "ambient" -> PropertyIcons.LIGHT
    "fog" -> PropertyIcons.FOG
    "sky" -> PropertyIcons.SKYBOX
    "ecs" -> PropertyIcons.ECS
    "object" -> TreeIcons.load("entity_object")
    "group" -> TreeIcons.load("entity_group")
    "dlight" -> TreeIcons.load("entity_light_directional")
    "camera" -> ComponentIcons.CAMERA
    "terrain" -> AssetIcons.forType("TERRAIN")
    "model" -> AssetIcons.forType("MODEL")
    "foliage" -> AssetIcons.forType("FOLIAGE")
    "clouds" -> AssetIcons.forType("CLOUDS")
    "texture" -> AssetIcons.forType("TEXTURE")
    "psky" -> AssetIcons.forType("SKYBOX_PROCEDURAL")
    "skybox" -> AssetIcons.forType("SKYBOX")
    "skyboxhdr" -> AssetIcons.forType("SKYBOX_HDR")
    "unknown" -> AssetIcons.UNKNOWN
    "uia" -> AssetIcons.forType("UI")
    "name" -> ComponentIcons.NAME
    "position" -> ComponentIcons.POSITION
    "render" -> ComponentIcons.RENDER
    "parent" -> ComponentIcons.PARENT
    "rigid" -> ComponentIcons.RIGID_BODY
    "collider" -> ComponentIcons.COLLIDER
    "constraint" -> ComponentIcons.CONSTRAINT
    "light" -> ComponentIcons.LIGHT
    "path" -> ComponentIcons.PATH_ARRANGEMENT
    "plane" -> ComponentIcons.PLANE
    "pilot" -> ComponentIcons.PILOT
    else -> ComponentIcons.GENERIC
}

/** [icon] drawn dim, for a disabled section or entity. */
fun dimmed(icon: Icon): Icon = com.intellij.openapi.util.IconLoader.getDisabledIcon(icon)
