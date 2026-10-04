package net.nevinsky.abyssus.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.scene.SceneDto
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation

@Service(Service.Level.APP)
class SceneReader(private val json: JsonProcessor) : ConfigFileReader<SceneDto> {
    /** What the platform creates: the one place this service looks up the core. */
    constructor() : this(service<AbyssusCore>().json)

    fun parse(text: String): SceneDto = json.parse(text, SceneDto::class.java)

    override fun read(file: VirtualFile): AssetReadResult<SceneDto> = runCatchingKeepingCancellation {
        parse(file.text()).copy(file = file)
    }.let { AssetReadResult.of(it) }
}