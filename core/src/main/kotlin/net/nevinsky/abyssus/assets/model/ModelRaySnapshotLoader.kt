package net.nevinsky.abyssus.assets.model

import com.badlogic.gdx.graphics.Pixmap
import net.nevinsky.abyssus.assets.files.AssetMeta
import net.nevinsky.abyssus.assets.loading.RaySnapshotLoader
import net.nevinsky.abyssus.assets.sky.RaySkySnapshot
import net.nevinsky.abyssus.core.loader.AssimpModelLoader

class ModelRaySnapshotLoader(
    private val assimp: AssimpModelLoader,
) : RaySnapshotLoader {
    override fun load(meta: AssetMeta<Any>): RaySkySnapshot? {

        val data = assimp.loadData(file)
        val images = assimp.decodeTextures(data, file)
        return try {
            capture(data, images)
        } finally {
            images.values.forEach(Pixmap::dispose)
        }
    }
}