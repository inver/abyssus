/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.panel

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.lib.core.editor.panel.assetIconKey
import net.nevinsky.abyssus.lib.core.editor.panel.componentIconKey
import net.nevinsky.abyssus.lib.core.editor.panel.entityIconKey
import net.nevinsky.abyssus.plugin.filetype.AssetIcons
import net.nevinsky.abyssus.plugin.filetype.ComponentIcons

/** The icon keys `lib-core-editor` names and the icons the plugin maps them to are written apart; this keeps them together. */
class PanelIconKeysTest : BasePlatformTestCase() {
    fun testEveryKeyTheModelNamesHasAnIcon() {
        val kinds = listOf(
            "Name", "Position", "Render", "Parent", "RigidBody", "Collider", "Constraint", "Light", "Foliage",
            "PathArrangement", "Plane", "Pilot",
        ).map { "${it}Component" }
        val keys = kinds.map(::componentIconKey) +
            listOf(null, "GROUP", "LIGHT_DIRECTIONAL", "TERRAIN", "OBJECT").map(::entityIconKey) +
            listOf("MODEL", "TERRAIN", "FOLIAGE", "TEXTURE", "SKYBOX", "SKYBOX_HDR", "SKYBOX_PROCEDURAL", "CLOUDS", "UI")
                .map(::assetIconKey)
        for (key in keys) {
            assertNotSame("key '$key' falls back to the generic icon", ComponentIcons.GENERIC, panelIcon(key))
            assertNotSame("key '$key' falls back to the unknown asset icon", AssetIcons.UNKNOWN, panelIcon(key))
        }
    }
}
