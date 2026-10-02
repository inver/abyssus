package net.nevinsky.abyssus

import net.nevinsky.abyssus.scene.SceneDto
import net.nevinsky.abyssus.sceneview.Asset
import net.nevinsky.abyssus.sceneview.MetaBase
import net.nevinsky.abyssus.sceneview.MetaType
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
    MetaBase(1, 0L, MetaType.valueOf(type), Any(), uuid?.let { UUID.nameUUIDFromBytes(it.toByteArray()) }),
    File(name),
    references,
    unused,
)
