package net.nevinsky.abyssus.filetype

import com.intellij.openapi.fileTypes.FileType
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

object AbyssusProjectIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/abss_file_icon.svg", AbyssusProjectIcons::class.java)
}

abstract class AssetFileType(
    private val typeName: String,
    private val extension: String,
    private val icon: Icon,
) : FileType {
    override fun getName() = typeName
    override fun getDescription() = typeName
    override fun getDefaultExtension() = extension
    override fun getIcon() = icon
    override fun isBinary() = false
}

class SceneFileType private constructor() : AssetFileType("Abyssus Scene", "scene", SceneIcons.FILE) {
    companion object {
        @JvmField
        val INSTANCE = SceneFileType()
    }
}

class AbyssusProjectFileType private constructor() :
    AssetFileType("Abyssus Project", "abss", AbyssusProjectIcons.FILE) {
    companion object {
        @JvmField
        val INSTANCE = AbyssusProjectFileType()
    }
}
