package net.nevinsky.abyssus.dto

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.AbyssusCore
import net.nevinsky.abyssus.runtime.SceneLoading
import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation

@Service(Service.Level.APP)
class SceneReader(private val loading: SceneLoading) : ConfigFileReader<Scene> {
    /** What the platform creates: the one place this service looks up the core. */
    constructor() : this(service<AbyssusCore>().scenes)

    fun parse(text: String): Scene = loading.parse(text)

    override fun read(file: VirtualFile): AssetReadResult<Scene> = runCatchingKeepingCancellation {
        loading.parse(file.path) { textOf(file) }
    }.let { AssetReadResult.of(it) }
}
