package net.nevinsky.abyssus

import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.core.assets.Asset
import net.nevinsky.abyssus.core.assets.AssetMeta
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.terrain.TerrainData
import net.nevinsky.abyssus.core.assets.terrain.TerrainLoader
import java.io.File
import java.util.UUID

/** Parses scene JSON without the platform: the reader is an application service, [JsonProcessor] is plain. */
fun parseScene(text: String): Scene = JsonProcessor().parse(text, Scene::class.java)

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
    return checkNotNull(TerrainLoader(files, AssetMetaLoader(JsonProcessor(), files)).prepare(name)) { "no terrain $name" }.data
}
