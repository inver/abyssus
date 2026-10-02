package net.nevinsky.abyssus.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.JsonProcessor
import net.nevinsky.abyssus.scene.SceneDto

@Service(Service.Level.APP)
class SceneReader : ConfigFileReader<SceneDto> {

    fun parse(text: String): SceneDto = service<JsonProcessor>().parse(text, SceneDto::class.java)

    override fun read(file: VirtualFile): AssetReadResult<SceneDto> = runCatchingKeepingCancellation {
        parse(file.text()).copy(file = file)
    }.fold(
        { AssetReadResult.success(it) },
        { AssetReadResult.failure(it.message ?: it.javaClass.simpleName) },
    )
}