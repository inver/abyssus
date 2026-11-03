/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.panel

/** The keys of the design's icon set that the panel model names; the plugin maps each to its SVG. */
fun componentIconKey(kind: String): String = when (kind.substringAfterLast('.').removeSuffix("Component")) {
    "Name" -> "name"
    "Position" -> "position"
    "Render" -> "render"
    "Parent" -> "parent"
    "RigidBody" -> "rigid"
    "Collider" -> "collider"
    "Constraint" -> "constraint"
    "Light" -> "light"
    "Foliage" -> "foliage"
    "PathArrangement" -> "path"
    "Plane" -> "plane"
    "Pilot" -> "pilot"
    else -> "generic"
}

/** The icon of an entity row by its `type`. */
fun entityIconKey(type: String?): String = when {
    type == null -> "object"
    type == "GROUP" -> "group"
    type.startsWith("LIGHT") -> "dlight"
    type == "TERRAIN" -> "terrain"
    else -> "object"
}

/** The icon of an asset by its `meta.json` type. */
fun assetIconKey(type: String?): String = when (type) {
    "MODEL" -> "model"
    "TERRAIN" -> "terrain"
    "FOLIAGE" -> "foliage"
    "TEXTURE" -> "texture"
    "SKYBOX" -> "skybox"
    "SKYBOX_HDR" -> "skyboxhdr"
    "SKYBOX_PROCEDURAL" -> "psky"
    "CLOUDS" -> "clouds"
    "UI" -> "uia"
    else -> "unknown"
}
