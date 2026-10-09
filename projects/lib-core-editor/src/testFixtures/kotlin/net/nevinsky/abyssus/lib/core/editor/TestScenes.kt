package net.nevinsky.abyssus.lib.gdx.editor

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.dto.SceneDto
import net.nevinsky.abyssus.lib.gdx.assets.Asset
import net.nevinsky.abyssus.lib.gdx.assets.AssetMeta
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainLoader
import org.slf4j.helpers.NOPLogger
import java.io.File
import java.util.UUID

/** Parses scene JSON without the platform: the reader is an application service, [JsonProcessor] is plain. */
fun parseScene(text: String): SceneDto = JsonProcessor(NOPLogger.NOP_LOGGER).parse(text, SceneDto::class.java)

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

/** A fixture project under the repository's `src/test/testData/project`, shared with `core`'s tests. */
fun testProject(name: String): File =
    File(System.getProperty("abyssus.testData") ?: "src/test/testData", "project/$name")

/** The heights of the terrain asset [name] of the project in [projectDir], read as a scene view loads them. */
fun terrainData(projectDir: File, name: String): TerrainData {
    val files = FileLoader(projectDir)
    return checkNotNull(TerrainLoader(files, AssetMetaLoader(JsonProcessor(NOPLogger.NOP_LOGGER), files)).prepare(name)) { "no terrain $name" }.staged.data
}
