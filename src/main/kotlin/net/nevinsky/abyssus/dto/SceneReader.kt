package net.nevinsky.abyssus.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.runtime.SceneLoading
import net.nevinsky.abyssus.runtime.scene.SceneDto
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation

@Service(Service.Level.APP)
class SceneReader(private val loading: SceneLoading) : ConfigFileReader<SceneDto> {
    /** What the platform creates: the one place this service looks up the core. */
    constructor() : this(service<AbyssusCore>().scenes)

    fun parse(text: String): SceneDto = loading.parse(text)

    override fun read(file: VirtualFile): AssetReadResult<SceneDto> = runCatchingKeepingCancellation {
        loading.parse(file.path) { file.text() }
    }.let { AssetReadResult.of(it) }
}
