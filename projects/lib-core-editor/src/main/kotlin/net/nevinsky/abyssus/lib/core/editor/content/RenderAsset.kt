package net.nevinsky.abyssus.lib.core.editor.content

import net.nevinsky.abyssus.lib.core.assets.MetaType

/** A model or terrain a render component may show: [type] is `MODEL` or `TERRAIN`, [name] its asset folder. */
data class RenderAsset(val type: String, val name: String) {
    /** [type] parsed once; null when the text names no [MetaType]. */
    val metaType: MetaType? get() = MetaType.entries.firstOrNull { it.name == type }

    fun isTerrain(): Boolean = metaType == MetaType.TERRAIN
}
