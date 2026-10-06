package net.nevinsky.abyssus.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation

@Service(Service.Level.APP)
class SceneReader(private val supplied: DocumentParsing? = null) : ConfigFileReader<Scene> {
    private fun loading(): DocumentParsing = supplied ?: service<AbyssusCore>().documents.parsing

    fun parse(text: String): Scene = loading().parse(text)

    override fun read(file: VirtualFile): AssetReadResult<Scene> = runCatchingKeepingCancellation {
        loading().parse(file.path) { textOf(file) }
    }.let { AssetReadResult.of(it) }
}
