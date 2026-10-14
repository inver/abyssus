package net.nevinsky.abyssus.uitesting

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import java.nio.file.Files
import java.nio.file.Path

/** Identity and flags read from the scene copy, so tests do not hard-code fixture values. */
class SceneFacts(val text: String) {
    private val root = SceneJson().parseObject(text)

    val id: Int = root.get("id").asInt()
    val name: String = root.get("name").asText()
    val fogEnabled: Boolean = root.get("fogEnabled").asBoolean()
    val skyboxName: String = root.get("skyboxName").asText()
    val skyboxEnabled: Boolean = root.get("skyboxEnabled").asBoolean()

    /** How the tree labels the scene row. */
    val treeLabel: String get() = "$name ($id)"

    /** Entity names by id, from `NameComponent`. */
    fun entityName(id: Int): String? = root.get("ecs")?.get(id.toString())?.get("components")?.get("NameComponent")?.get("name")?.asText()

    companion object {
        fun read(scene: Path) = SceneFacts(Files.readString(scene))
    }
}
