package net.nevinsky.abyssus.lib.core.editor

import net.nevinsky.abyssus.lib.core.editor.document.DocumentParsing
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import net.nevinsky.abyssus.lib.core.dto.EntityDto
import net.nevinsky.abyssus.lib.core.format.EntityType
import net.nevinsky.abyssus.lib.core.ecs.component.HeaderComponent
import com.badlogic.ashley.core.Component
import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.AssetMeta
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.util.UUID

/** Parses scene JSON without the platform: the reader is an application service, [JsonProcessor] is plain. */
fun parseScene(text: String): SceneDto = DocumentParsing(JsonProcessor(NOPLogger.NOP_LOGGER), NOPLogger.NOP_LOGGER).parse(text)

/** An asset as the project reader lists it, for tests that need no folder on disk. [uuid] is any text, folded into a UUID. */
fun testAsset(
    name: String,
    uuid: String?,
    type: String,
    references: List<String> = emptyList(),
    unused: Boolean = false,
): Asset<Any> = Asset(
    File(name),
    AssetMeta(
        name = name, type = MetaType.valueOf(type), additional = Any(),
        uuid = uuid?.let { UUID.nameUUIDFromBytes(it.toByteArray()) },
    ),
    references,
    unused,
)

/** The heights of the terrain asset [name] of the project in [projectDir], read as a scene view loads them. */
fun terrainData(projectDir: File, name: String): TerrainData {
    val files = FileLoader(projectDir)
    return checkNotNull(TerrainLoader(files, AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)).prepare(name)) { "no terrain $name" }.staged.data
}

/** An entity of [type] with the header for [id] followed by [parts], for tests that need no JSON. */
fun entityOf(id: Long, type: EntityType, vararg parts: Component): EntityDto =
    EntityDto(mutableListOf<Component>(HeaderComponent(id = id, type = type)).apply { addAll(parts) })

/** A scene holding [entities], each keyed by the id of its header. */
fun sceneOf(vararg entities: EntityDto): SceneDto =
    SceneDto(ecs = entities.associateByTo(mutableMapOf()) { it.header().id })
