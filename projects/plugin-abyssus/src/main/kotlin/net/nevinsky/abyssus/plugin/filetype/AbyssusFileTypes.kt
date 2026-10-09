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
    val FILE: Icon = IconLoader.getIcon("/icons/scene_file_icon.svg", SceneIcons::class.java)
}

object SceneViewIcons {
    @JvmField
    val VIEW: Icon = IconLoader.getIcon("/icons/view_scene.svg", SceneViewIcons::class.java)
}

object ScenesIcons {
    /** The `scenes` list of a project; individual scenes use [SceneIcons.FILE]. */
    @JvmField
    val LIST: Icon = IconLoader.getIcon("/icons/scenes_icon.svg", ScenesIcons::class.java)
}

/** Icons for well-known scene properties, looked up by property name. */
object PropertyIcons {
    private fun load(name: String): Icon = IconLoader.getIcon("/icons/${name}_icon.svg", PropertyIcons::class.java)

    @JvmField
    val LIGHT: Icon = load("light")

    @JvmField
    val ECS: Icon = load("ecs")

    @JvmField
    val FOG: Icon = load("fog")

    @JvmField
    val SKYBOX: Icon = load("skybox")

    fun forProperty(name: String): Icon? = when (name) {
        "ambientLight" -> LIGHT
        "ecs" -> ECS
        "fog" -> FOG
        "skyboxName" -> SKYBOX
        else -> null
    }
}

/** The enable / disable eye at the right of a row: teal when enabled, gray and crossed out when disabled. */
object EyeIcons {
    @JvmField
    val ON: Icon = IconLoader.getIcon("/icons/eye_on_icon.svg", EyeIcons::class.java)

    @JvmField
    val OFF: Icon = IconLoader.getIcon("/icons/eye_off_icon.svg", EyeIcons::class.java)
}

/** Icons of an entity's components, by component name without the `Component` suffix; anything else gets [GENERIC]. */
object ComponentIcons {
    private fun load(name: String): Icon =
        IconLoader.getIcon("/icons/component_${name}_icon.svg", ComponentIcons::class.java)

    @JvmField
    val GENERIC: Icon = load("generic")

    @JvmField
    val TRANSFORM: Icon = load("transform")

    @JvmField
    val PHYSICS: Icon = load("physics")

    @JvmField
    val PARTICLES: Icon = load("particles")

    fun forComponent(name: String): Icon = when (name.removeSuffix("Component")) {
        "Position", "Rotation", "Scale", "Transform" -> TRANSFORM
        "Render", "Model", "Mesh" -> AssetIcons.forType("MODEL")
        "Terrain" -> AssetIcons.forType("TERRAIN")
        "Material" -> AssetIcons.forType("MATERIAL")
        "Physics", "RigidBody", "Collider" -> PHYSICS
        "Particles", "ParticleSystem" -> PARTICLES
        "Light", "AmbientLight" -> PropertyIcons.LIGHT
        else -> GENERIC
    }
}

/** Icons of a project's assets, by `meta.json` `type`; an unrecognized or missing type gets [UNKNOWN]. */
object AssetIcons {
    private fun load(name: String): Icon = IconLoader.getIcon("/icons/asset_${name}_icon.svg", AssetIcons::class.java)

    @JvmField
    val UNKNOWN: Icon = load("unknown")

    private val byType: Map<String, Icon> by lazy {
        listOf("MODEL", "TERRAIN", "SKYBOX", "SKYBOX_HDR", "TEXTURE", "PIXMAP_TEXTURE", "MATERIAL", "SHADER", "CLOUDS")
            .associateWith { load(it.lowercase()) } + ("SKYBOX_PROCEDURAL" to load("skybox"))
    }

    fun forType(type: String?): Icon = type?.let { byType[it] } ?: UNKNOWN

    fun forType(type: MetaType): Icon = forType(type.name)
}

object AbyssusProjectIcons {
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
