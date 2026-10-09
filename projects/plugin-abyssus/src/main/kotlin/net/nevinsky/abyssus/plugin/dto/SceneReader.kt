package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.gdx.editor.document.DocumentParsing
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.plugin.AbyssusCore
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation

@Service(Service.Level.APP)
class SceneReader(private val supplied: DocumentParsing? = null) : ConfigFileReader<SceneDto> {
    private fun loading(): DocumentParsing = supplied ?: service<AbyssusCore>().documents.parsing

    fun parse(text: String): SceneDto = loading().parse(text)

    override fun read(file: VirtualFile): AssetReadResult<SceneDto> = runCatchingKeepingCancellation {
        loading().parse(file.path) { textOf(file) }
    }.let { AssetReadResult.of(it) }
}
