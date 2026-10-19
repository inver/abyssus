/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon
import net.nevinsky.abyssus.lib.core.assets.MetaType

object SceneIcons {
    @JvmField
    val FILE: Icon = TreeIcons.load("scene")
}

object SceneViewIcons {
    @JvmField
    val VIEW: Icon = IconLoader.getIcon("/icons/view_scene.svg", SceneViewIcons::class.java)
}

/** Loader for the tree glyphs under `/icons/tree/` (the design's Icon set; `TreeIconInventoryTest` lists them). */
object TreeIcons {
    fun load(name: String): Icon = IconLoader.getIcon("/icons/tree/$name.svg", TreeIcons::class.java)
}

object ScenesIcons {
    /** The `Scenes` folder of a project; individual scenes use [SceneIcons.FILE]. */
    @JvmField
    val LIST: Icon = TreeIcons.load("folder_scenes")

    @JvmField
    val UI: Icon = TreeIcons.load("folder_ui")

    @JvmField
    val ASSETS: Icon = TreeIcons.load("folder_assets")

    /** A type group such as Models or Terrains. */
    @JvmField
    val FOLDER: Icon = TreeIcons.load("folder")

    @JvmField
    val UI_SCREEN: Icon = TreeIcons.load("ui_screen")

    /** The `Screens` and `Overlays` folders under UI. */
    @JvmField
    val UI_SCREENS: Icon = TreeIcons.load("folder_screens")

    @JvmField
    val UI_OVERLAYS: Icon = TreeIcons.load("folder_overlays")

    /** The Water group of a scene. */
    @JvmField
    val WATER: Icon = TreeIcons.load("folder_water")

    @JvmField
    val WATER_LAKE: Icon = TreeIcons.load("water_lake")

    @JvmField
    val WATER_SEA: Icon = TreeIcons.load("water_sea")
}

/** Icons for well-known scene properties, looked up by property name. */
object PropertyIcons {
    @JvmField
    val LIGHT: Icon = TreeIcons.load("ambient_light")

    @JvmField
    val ECS: Icon = TreeIcons.load("ecs")

    @JvmField
    val FOG: Icon = TreeIcons.load("fog")

    @JvmField
    val SKYBOX: Icon = TreeIcons.load("skybox")

    fun forProperty(name: String): Icon? = when (name) {
        "ambientLight" -> LIGHT
        "ecs" -> ECS
        "fog" -> FOG
        "skyboxName" -> SKYBOX
        else -> null
    }
}

/** The 14 px enable / disable eye at the right of a row: blue when enabled, gray and crossed out when disabled. */
object EyeIcons {
    @JvmField
    val ON: Icon = TreeIcons.load("eye_on")

    @JvmField
    val OFF: Icon = TreeIcons.load("eye_off")
}

/** The glyphs of the Abyssus pane's Options-menu filter and the Properties Browse button. */
object TreeActionIcons {
    @JvmField
    val UNUSED_FILTER: Icon = TreeIcons.load("action_unused_filter")

    @JvmField
    val UNUSED_FILTER_SELECTED: Icon = TreeIcons.load("action_unused_filter_selected")

    /** The Browse button of an asset field. */
    @JvmField
    val BROWSE: Icon = TreeIcons.load("action_browse")
}

/** Icons of an entity's components, by component name without the `Component` suffix; anything else gets [GENERIC]. */
object ComponentIcons {
    private fun load(name: String): Icon =
        IconLoader.getIcon("/icons/component_${name}_icon.svg", ComponentIcons::class.java)

    @JvmField
    val GENERIC: Icon = load("generic")

    @JvmField
    val PARTICLES: Icon = load("particles")

    /** Kept for existing callers: the transform kinds use the Position glyph. */
    val TRANSFORM: Icon get() = POSITION

    val PHYSICS: Icon get() = RIGID_BODY

    @JvmField
    val NAME: Icon = TreeIcons.load("component_name")

    @JvmField
    val POSITION: Icon = TreeIcons.load("component_position")

    @JvmField
    val RENDER: Icon = TreeIcons.load("component_render")

    @JvmField
    val PARENT: Icon = TreeIcons.load("component_parent")

    @JvmField
    val RIGID_BODY: Icon = TreeIcons.load("component_rigid_body")

    @JvmField
    val COLLIDER: Icon = TreeIcons.load("component_collider")

    @JvmField
    val CONSTRAINT: Icon = TreeIcons.load("component_constraint")

    @JvmField
    val LIGHT: Icon = TreeIcons.load("component_light")

    @JvmField
    val FOLIAGE: Icon = TreeIcons.load("component_foliage")

    @JvmField
    val PATH_ARRANGEMENT: Icon = TreeIcons.load("component_path_arrangement")

    @JvmField
    val PLANE: Icon = TreeIcons.load("component_plane")

    @JvmField
    val PILOT: Icon = TreeIcons.load("component_pilot")

    fun forComponent(name: String): Icon = when (name.removeSuffix("Component")) {
        "Name" -> NAME
        "Position", "Rotation", "Scale", "Transform" -> POSITION
        "Render", "Model", "Mesh" -> RENDER
        "Parent" -> PARENT
        "Physics", "RigidBody" -> RIGID_BODY
        "Collider" -> COLLIDER
        "Constraint" -> CONSTRAINT
        "Light", "AmbientLight" -> LIGHT
        "Foliage" -> FOLIAGE
        "PathArrangement" -> PATH_ARRANGEMENT
        "Plane" -> PLANE
        "Pilot" -> PILOT
        "Terrain" -> AssetIcons.forType("TERRAIN")
        "Material" -> AssetIcons.forType("MATERIAL")
        "Particles", "ParticleSystem" -> PARTICLES
        else -> GENERIC
    }
}

/** Icons of a project's assets, by `meta.json` `type`; an unrecognized or missing type gets [UNKNOWN]. */
object AssetIcons {
    private fun load(name: String): Icon = IconLoader.getIcon("/icons/asset_${name}_icon.svg", AssetIcons::class.java)

    @JvmField
    val UNKNOWN: Icon = load("unknown")

    private val byType: Map<String, Icon> by lazy {
        mapOf(
            "MODEL" to TreeIcons.load("asset_model"),
            "TERRAIN" to TreeIcons.load("asset_terrain"),
            "FOLIAGE" to TreeIcons.load("asset_foliage"),
            "TEXTURE" to TreeIcons.load("asset_texture"),
            "CLOUDS" to TreeIcons.load("asset_clouds"),
            "SKYBOX_PROCEDURAL" to TreeIcons.load("asset_skybox_procedural"),
            "UI" to TreeIcons.load("asset_ui"),
            "SHADER" to TreeIcons.load("asset_shader"),
            "SKYBOX" to load("skybox"),
            "SKYBOX_HDR" to load("skybox_hdr"),
            "PIXMAP_TEXTURE" to load("pixmap_texture"),
            "MATERIAL" to load("material"),
        )
    }

    fun forType(type: String?): Icon = type?.let { byType[it] } ?: UNKNOWN

    fun forType(type: MetaType): Icon = forType(type.name)
}

object AbyssusProjectIcons {
    /** The project row at the top of the tree. */
    @JvmField
    val PROJECT: Icon = TreeIcons.load("project")

    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/abss_file_icon.svg", AbyssusProjectIcons::class.java)
}

/** Scenes are JSON documents, so the text editor highlights and folds them as JSON. */
class SceneFileType private constructor() : LanguageFileType(JsonLanguage.INSTANCE) {
    override fun getName() = "Abyssus Scene"
    override fun getDescription() = "Abyssus Scene"
    override fun getDisplayName() = "Abyssus Scene"
    override fun getDefaultExtension() = "scene"
    override fun getIcon(): Icon = SceneIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = SceneFileType()
    }
}

/** Project files are JSON documents too. */
class AbyssusProjectFileType private constructor() : LanguageFileType(JsonLanguage.INSTANCE) {
    override fun getName() = "Abyssus Project"
    override fun getDescription() = "Abyssus Project"
    override fun getDisplayName() = "Abyssus Project"
    override fun getDefaultExtension() = "abss"
    override fun getIcon(): Icon = AbyssusProjectIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = AbyssusProjectFileType()
    }
}
