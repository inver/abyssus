/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.flightgear

/** An AC3D material: diffuse colour and transparency (0 opaque, 1 invisible). */
data class Ac3dMaterial(val name: String, val r: Float, val g: Float, val b: Float, val transparency: Float)

/** An AC3D surface: [type] 0 polygon, 1 closed line, 2 line; [refs] index the object's vertices, [uvs] two per ref. */
class Ac3dSurface(val flags: Int, val material: Int, val refs: IntArray, val uvs: FloatArray) {
    val type: Int get() = flags and 0x0F
    val smooth: Boolean get() = flags and 0x10 != 0
    val twoSided: Boolean get() = flags and 0x20 != 0
}

/**
 * An AC3D object: its vertices in its own frame, placed in its parent's by [rotation] (3x3, row-major) and
 * [location]. [textureRepeat] and [textureOffset] apply to its UVs.
 */
class Ac3dObject(
    val type: String,
    val name: String?,
    val texture: String?,
    val textureRepeat: FloatArray,
    val textureOffset: FloatArray,
    val crease: Float,
    val rotation: FloatArray,
    val location: FloatArray,
    val vertices: FloatArray,
    val surfaces: List<Ac3dSurface>,
    val kids: List<Ac3dObject>,
)

class Ac3dModel(val materials: List<Ac3dMaterial>, val world: Ac3dObject)

/** An AC3D file that cannot be read, with the line where reading stopped. */
class Ac3dException(message: String) : RuntimeException(message)

/** The crease angle AC3D uses when an object declares none, in degrees. */
const val DEFAULT_CREASE = 61f

/** Reads AC3D text files (`AC3D` / `AC3Db` headers). */
class Ac3dReader {
    fun read(text: String, path: String): Ac3dModel = Parser(text.lines(), path).model()

    private class Parser(private val lines: List<String>, private val path: String) {
        private var at = 0

        fun model(): Ac3dModel {
            val header = next() ?: fail("empty file")
            if (!header.startsWith("AC3D")) fail("not an AC3D file")
            val materials = ArrayList<Ac3dMaterial>()
            while (true) {
                val line = peek() ?: fail("no OBJECT")
                if (line.startsWith("MATERIAL")) {
                    next()
                    materials += material(line)
                } else if (line.startsWith("OBJECT")) {
                    break
                } else {
                    next()
                }
            }
            return Ac3dModel(materials, obj())
        }

        private fun material(line: String): Ac3dMaterial {
            val tokens = tokens(line)
            val name = tokens.getOrNull(1) ?: ""
            fun after(key: String, count: Int): List<Float> {
                val i = tokens.indexOf(key)
                if (i < 0) return List(count) { 0f }
                return (1..count).map { tokens.getOrNull(i + it)?.toFloatOrNull() ?: 0f }
            }
            val rgb = after("rgb", 3)
            val trans = after("trans", 1)[0]
            return Ac3dMaterial(name, rgb[0], rgb[1], rgb[2], trans)
        }

        private fun obj(): Ac3dObject {
            val head = next() ?: fail("missing OBJECT")
            if (!head.startsWith("OBJECT")) fail("expected OBJECT, found '$head'")
            val type = tokens(head).getOrElse(1) { "poly" }
            var name: String? = null
            var texture: String? = null
            var repeat = floatArrayOf(1f, 1f)
            var offset = floatArrayOf(0f, 0f)
            var crease = DEFAULT_CREASE
            var rotation = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            var location = floatArrayOf(0f, 0f, 0f)
            var vertices = FloatArray(0)
            val surfaces = ArrayList<Ac3dSurface>()
            while (true) {
                val line = next() ?: fail("object '${name ?: type}' has no kids line")
                val t = tokens(line)
                when (t.firstOrNull()) {
                    "name" -> name = t.getOrNull(1)
                    "data" -> skipData(t.getOrNull(1)?.toIntOrNull() ?: 0)
                    "texture" -> texture = t.getOrNull(1)
                    "texrep" -> repeat = floats(t, 2)
                    "texoff" -> offset = floats(t, 2)
                    "crease" -> crease = t.getOrNull(1)?.toFloatOrNull() ?: crease
                    "rot" -> rotation = floats(t, 9)
                    "loc" -> location = floats(t, 3)
                    "numvert" -> {
                        val count = t.getOrNull(1)?.toIntOrNull() ?: fail("bad numvert")
                        vertices = FloatArray(count * 3)
                        for (i in 0 until count) {
                            val v = tokens(next() ?: fail("missing vertex"))
                            for (k in 0..2) vertices[i * 3 + k] = v.getOrNull(k)?.toFloatOrNull() ?: fail("bad vertex '$v'")
                        }
                    }
                    "numsurf" -> {
                        val count = t.getOrNull(1)?.toIntOrNull() ?: fail("bad numsurf")
                        repeat(count) { surfaces += surface(vertices.size / 3) }
                    }
                    "kids" -> {
                        val count = t.getOrNull(1)?.toIntOrNull() ?: fail("bad kids")
                        val kids = (0 until count).map { obj() }
                        return Ac3dObject(type, name, texture, repeat, offset, crease, rotation, location, vertices, surfaces, kids)
                    }
                    else -> Unit // url, subdiv, hidden, locked, folded: not needed
                }
            }
        }

        private fun surface(vertexCount: Int): Ac3dSurface {
            val head = tokens(next() ?: fail("missing SURF"))
            if (head.firstOrNull() != "SURF") fail("expected SURF, found '${head.joinToString(" ")}'")
            val flags = head.getOrNull(1)?.let { java.lang.Long.decode(it).toInt() } ?: fail("bad SURF flags")
            var material = 0
            while (true) {
                val t = tokens(next() ?: fail("surface without refs"))
                when (t.firstOrNull()) {
                    "mat" -> material = t.getOrNull(1)?.toIntOrNull() ?: 0
                    "refs" -> {
                        val count = t.getOrNull(1)?.toIntOrNull() ?: fail("bad refs")
                        val refs = IntArray(count)
                        val uvs = FloatArray(count * 2)
                        for (i in 0 until count) {
                            val r = tokens(next() ?: fail("missing ref"))
                            refs[i] = r.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0 until vertexCount } ?: fail("bad ref '$r'")
                            uvs[i * 2] = r.getOrNull(1)?.toFloatOrNull() ?: 0f
                            uvs[i * 2 + 1] = r.getOrNull(2)?.toFloatOrNull() ?: 0f
                        }
                        return Ac3dSurface(flags, material, refs, uvs)
                    }
                    else -> fail("unexpected '${t.joinToString(" ")}' in a surface")
                }
            }
        }

        private fun skipData(length: Int) {
            var remaining = length
            while (remaining > 0) {
                val line = next() ?: return
                remaining -= line.length + 1
            }
        }

        private fun floats(t: List<String>, count: Int) = FloatArray(count) { t.getOrNull(it + 1)?.toFloatOrNull() ?: fail("bad numbers in '${t.joinToString(" ")}'") }

        /** Whitespace-separated tokens; a quoted token keeps its spaces and loses its quotes. */
        private fun tokens(line: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < line.length) {
                when {
                    line[i].isWhitespace() -> i++
                    line[i] == '"' -> {
                        val end = line.indexOf('"', i + 1).let { if (it < 0) line.length else it }
                        out += line.substring(i + 1, end)
                        i = end + 1
                    }
                    else -> {
                        val start = i
                        while (i < line.length && !line[i].isWhitespace()) i++
                        out += line.substring(start, i)
                    }
                }
            }
            return out
        }

        private fun peek(): String? {
            while (at < lines.size && lines[at].isBlank()) at++
            return lines.getOrNull(at)?.trim()
        }

        private fun next(): String? = peek()?.also { at++ }

        private fun fail(message: String): Nothing = throw Ac3dException("$path line ${at}: $message")
    }
}
