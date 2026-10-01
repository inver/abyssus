package net.nevinsky.abyssus.filetype

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/** Pretty-printing of scene files, which the editor saves minified on one line. */
object SceneJson {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /**
     * The scene as indented JSON, with key order and number text unchanged; null when [text] is not a JSON object
     * (so a half-edited or foreign file is never rewritten).
     */
    fun pretty(text: String): String? = runCatching {
        val element = JsonParser.parseString(text)
        if (!element.isJsonObject) return null
        gson.toJson(element) + "\n"
    }.getOrNull()
}
