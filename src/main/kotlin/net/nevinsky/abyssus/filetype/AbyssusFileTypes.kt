package net.nevinsky.abyssus.filetype

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object SceneIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/scene_file_icon.svg", SceneIcons::class.java)
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
