package net.nevinsky.abyssus.filetype

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/**
 * Reading and writing of scene JSON. Nulls are kept (Gson drops them by default, and a scene's `"skyboxName": null`
 * is data), key order and number text are unchanged, and HTML characters are not escaped.
 */
object SceneJson {
    private val prettyGson = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()
    private val compactGson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

    fun pretty(element: JsonElement): String = prettyGson.toJson(element) + "\n"

    fun compact(element: JsonElement): String = compactGson.toJson(element)

    /** [element] in the style of [original]: indented when the original spans several lines, else on one line. */
    fun inStyleOf(original: String, element: JsonElement): String =
        if (original.contains('\n')) pretty(element) else compact(element)

    /** The scene as indented JSON; null when [text] is not a JSON object (so a half-edited or foreign file is never rewritten). */
    fun pretty(text: String): String? = runCatching {
        val element = JsonParser.parseString(text)
        if (!element.isJsonObject) return null
        pretty(element)
    }.getOrNull()
}
