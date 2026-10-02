/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.nevinsky.abyssus.filetype

import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

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

/** Icons of a project's assets, by `meta.json` `type`; an unrecognized or missing type gets [UNKNOWN]. */
object AssetIcons {
    private fun load(name: String): Icon = IconLoader.getIcon("/icons/asset_${name}_icon.svg", AssetIcons::class.java)

    @JvmField
    val UNKNOWN: Icon = load("unknown")

    private val byType: Map<String, Icon> by lazy {
        listOf("MODEL", "TERRAIN", "SKYBOX", "SKYBOX_HDR", "TEXTURE", "PIXMAP_TEXTURE", "MATERIAL", "SHADER")
            .associateWith { load(it.lowercase()) }
    }

    fun forType(type: String?): Icon = type?.let { byType[it] } ?: UNKNOWN
}

object AbyssusProjectIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/abss_file_icon.svg", AbyssusProjectIcons::class.java)
}

/** Scenes are JSON documents, so the text editor highlights and folds them as JSON. */
class SceneFileType private constructor() : LanguageFileType(JsonLanguage.INSTANCE) {
    override fun getName() = "Abyssus Scene"
    override fun getDescription() = "Abyssus Scene"
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
    override fun getDefaultExtension() = "abss"
    override fun getIcon(): Icon = AbyssusProjectIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = AbyssusProjectFileType()
    }
}
