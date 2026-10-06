package net.nevinsky.abyssus.core.assets

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.assets.model.ModelMeta
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyMeta
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.core.assets.terrain.TerrainMeta
import net.nevinsky.abyssus.core.assets.texture.TextureMeta

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
    ),
) {
    private val settings = settings.toMap()

    fun bind(name: String, tree: JsonNode): AssetMeta<Any> {
        val type = tree["type"]?.asText()?.let { name -> MetaType.entries.firstOrNull { it.name == name } } ?: MetaType.UNKNOWN
        val block = tree["additional"]?.takeIf { it.isObject } ?: json.readObject("{}")
        val additional: Any = settings[type]?.let { json.bind(block, it) } ?: json.bind(block, Map::class.java)
        return AssetMeta(
            name = name,
            formatVersion = tree["formatVersion"]?.asInt(1) ?: 1,
            version = tree["version"]?.asInt(1) ?: 1,
            lastModified = tree["lastModified"]?.asLong(0) ?: 0,
            type = type,
            additional = additional,
            uuid = parseUuidOrNull(tree["uuid"]?.asText()),
        )
    }
}
