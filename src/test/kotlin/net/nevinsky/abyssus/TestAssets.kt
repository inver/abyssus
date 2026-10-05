package net.nevinsky.abyssus

import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.runtime.scene.SceneDto
import net.nevinsky.abyssus.assets.Asset
import net.nevinsky.abyssus.assets.AssetMeta
import net.nevinsky.abyssus.assets.MetaType
import java.io.File
import java.util.UUID

/** Parses scene JSON without the platform: the reader is an application service, [JsonProcessor] is plain. */
fun parseScene(text: String): SceneDto = JsonProcessor().parse(text, SceneDto::class.java)

/** An asset as the project reader lists it, for tests that need no folder on disk. [uuid] is any text, folded into a UUID. */
fun testAsset(
    name: String,
    uuid: String?,
    type: String,
    references: List<String> = emptyList(),
    unused: Boolean = false,
): Asset<Any> = Asset(
    name,
    AssetMeta(1, 0L, MetaType.valueOf(type), Any(), uuid?.let { UUID.nameUUIDFromBytes(it.toByteArray()) }),
    File(name),
    references,
    unused,
)

/** A fixture project under the repository's `src/test/testData/project`, shared with `core`'s tests. */
fun testProject(name: String): File =
    File(System.getProperty("abyssus.testData") ?: "src/test/testData", "project/$name")
