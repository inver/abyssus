package net.nevinsky.abyssus.uitesting

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import java.nio.file.Files
import java.nio.file.Path

/** Identity and flags read from the scene copy, so tests do not hard-code fixture values. */
class SceneFacts(val text: String) {
    private val root = SceneJson().parseObject(text)

    val id: Int = root.get("id").asInt()
    val name: String = root.get("name").asText()
    val fogEnabled: Boolean = root.get("fogEnabled")?.asBoolean() ?: false
    val skyboxName: String = root.get("skyboxName")?.asText() ?: ""
    val skyboxEnabled: Boolean = root.get("skyboxEnabled")?.asBoolean() ?: false

    /** How the tree labels the scene row. */
    val treeLabel: String get() = "$name ($id)"

    /** Entity names by id, from `NameComponent`. */
    fun entityName(id: Int): String? {
        val ecs = root.get("ecs") ?: return null
        // a scene with archetypes keeps its entities under `ecs.entities`
        val entity = ecs.get(id.toString())
        return entity?.get("components")?.get("NameComponent")?.get("name")?.asText()
    }

    companion object {
        fun read(scene: Path) = SceneFacts(Files.readString(scene))
    }
}
