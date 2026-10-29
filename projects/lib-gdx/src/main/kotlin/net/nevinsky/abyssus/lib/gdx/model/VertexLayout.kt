package net.nevinsky.abyssus.lib.gdx.model

import com.badlogic.gdx.graphics.VertexAttribute

/** Where each attribute of an interleaved vertex starts, counted in floats. */
class VertexLayout(private val attributes: Array<VertexAttribute>) {
    constructor(mesh: ModelMesh) : this(mesh.attributes)

    /** The floats of one vertex. */
    val stride: Int = attributes.sumOf { it.numComponents }

    /** The offset of the first attribute of [usage] (and of [unit], when given), or null when the mesh has none. */
    fun offsetOf(usage: Int, unit: Int? = null): Int? {
        var offset = 0
        for (a in attributes) {
            if (a.usage == usage && (unit == null || a.unit == unit)) return offset
            offset += a.numComponents
        }
        return null
    }

    /** The offsets of every attribute of [usage], in attribute order. */
    fun offsetsOf(usage: Int): List<Int> {
        val result = ArrayList<Int>()
        var offset = 0
        for (a in attributes) {
            if (a.usage == usage) result += offset
            offset += a.numComponents
        }
        return result
    }
}
