package net.nevinsky.abyssus.lib.gdx.assets

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.lib.gdx.assets.model.ModelMeta
import net.nevinsky.abyssus.lib.gdx.assets.sky.clouds.CloudMeta
import net.nevinsky.abyssus.lib.gdx.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.lib.gdx.assets.sky.hdr.HdrSkyMeta
import net.nevinsky.abyssus.lib.gdx.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.gdx.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.lib.gdx.assets.texture.TextureMeta
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor

/** Binds an admitted metadata tree; callers validate native identity before invoking [bind]. No IO or GL. */
class AssetMetaBinder(
    private val json: JsonProcessor,
    settings: Map<MetaType, Class<*>> = mapOf(
        MetaType.MODEL to ModelMeta::class.java,
        MetaType.TERRAIN to TerrainMeta::class.java,
        MetaType.SKYBOX to SkyboxMeta::class.java,
        MetaType.SKYBOX_PROCEDURAL to ProceduralSkyMeta::class.java,
        MetaType.SKYBOX_HDR to HdrSkyMeta::class.java,
        MetaType.TEXTURE to TextureMeta::class.java,
        MetaType.PIXMAP_TEXTURE to TextureMeta::class.java,
        MetaType.CLOUDS to CloudMeta::class.java,
    ),
) {
    private val settings = settings.toMap()

    fun bind(name: String, tree: JsonNode): AssetMeta<Any> {
        val baseMeta = json.bind(tree, AssetMeta::class.java)
        val block = tree["additional"]?.takeIf { it.isObject } ?: json.readObject("{}")
        val additional: Any = settings[baseMeta.type]?.let {
            json.bind(block, it)
        } ?: json.bind(block, Map::class.java)
        return AssetMeta(
            name = name,
            additional = additional,
            formatVersion = baseMeta.formatVersion,
            version = baseMeta.version,
            lastModified = baseMeta.lastModified,
            type = baseMeta.type,
            uuid = baseMeta.uuid,
        )
    }
}
