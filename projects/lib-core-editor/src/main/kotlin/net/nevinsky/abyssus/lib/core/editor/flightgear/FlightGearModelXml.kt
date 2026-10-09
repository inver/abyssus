/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.flightgear

import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** FlightGear's XML files, parsed without DTDs or external entities. */
class FlightGearXml {
    fun parse(bytes: ByteArray, path: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        return try {
            factory.newDocumentBuilder().apply { setErrorHandler(null) }.parse(ByteArrayInputStream(bytes)).documentElement
        } catch (e: SAXException) {
            throw FlightGearArchiveException("$path is not readable XML: ${e.message}")
        }
    }
}

/** The direct child elements of this element. */
fun Element.children(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

fun Element.child(name: String): Element? = children().firstOrNull { it.tagName == name }

/** The trimmed text of child [name], or null when it is absent or blank. */
fun Element.text(name: String): String? = child(name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

/** FlightGear's model offsets: metres in its body frame (x aft, y right, z up) and degrees. */
data class FlightGearOffsets(
    val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0,
    val heading: Double = 0.0, val pitch: Double = 0.0, val roll: Double = 0.0,
)

/** A `<model>` nested in a model XML: its path inside the archive (null when it leaves the archive) and offsets. */
data class NestedModel(val path: String?, val written: String, val offsets: FlightGearOffsets)

/** A `select` animation: the parts it shows while [condition] holds. */
data class SelectAnimation(val parts: List<String>, val condition: Element?)

/**
 * A FlightGear model XML: the AC3D file it draws ([acPath], inside the archive), its [offsets], the [nested] models,
 * whether it has instrument panels, and its `select` animations. Other animations are ignored.
 */
data class FlightGearModelXml(
    val acPath: String?,
    val acWritten: String?,
    val offsets: FlightGearOffsets,
    val nested: List<NestedModel>,
    val panels: Int,
    val selects: List<SelectAnimation>,
)

/** Reads model XML files of an archive. */
class FlightGearModelXmlReader(private val archive: FlightGearArchive, private val xml: FlightGearXml = FlightGearXml()) {
    fun read(path: String): FlightGearModelXml {
        val bytes = archive.read(path) ?: throw FlightGearArchiveException("$path is missing from the archive")
        val root = xml.parse(bytes, path)
        val folder = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val written = root.text("path")
        return FlightGearModelXml(
            acPath = written?.let { archive.resolve(folder, it) },
            acWritten = written,
            offsets = offsets(root.child("offsets")),
            nested = root.children().filter { it.tagName == "model" }.mapNotNull { model ->
                val nestedPath = model.text("path") ?: return@mapNotNull null
                NestedModel(archive.resolve(folder, nestedPath), nestedPath, offsets(model.child("offsets")))
            },
            panels = root.children().count { it.tagName == "panel" },
            selects = root.children().filter { it.tagName == "animation" && it.text("type") == "select" }.map { animation ->
                SelectAnimation(
                    animation.children().filter { it.tagName == "object-name" }.map { it.textContent.trim() },
                    animation.child("condition"),
                )
            },
        )
    }

    private fun offsets(element: Element?): FlightGearOffsets {
        if (element == null) return FlightGearOffsets()
        fun number(name: String) = element.text(name)?.toDoubleOrNull() ?: 0.0
        return FlightGearOffsets(number("x-m"), number("y-m"), number("z-m"), number("heading-deg"), number("pitch-deg"), number("roll-deg"))
    }
}

/**
 * FlightGear conditions evaluated at rest: every property is 0. `and`, `or`, `not`, the comparisons and `property` /
 * `value` operands are understood; anything else counts as true, so an unknown condition never hides a part.
 */
class RestState {
    /** Whether a part under [condition] (null: none) is shown at rest. */
    fun shows(condition: Element?): Boolean = condition == null || all(condition)

    private fun all(element: Element): Boolean = element.children().all(::holds)

    private fun holds(element: Element): Boolean = when (element.tagName) {
        "and" -> all(element)
        "or" -> element.children().let { it.isEmpty() || it.any(::holds) }
        "not" -> !all(element)
        "equals" -> compare(element) { a, b -> a == b }
        "not-equals" -> compare(element) { a, b -> a != b }
        "less-than" -> compare(element) { a, b -> a < b }
        "less-than-equals" -> compare(element) { a, b -> a <= b }
        "greater-than" -> compare(element) { a, b -> a > b }
        "greater-than-equals" -> compare(element) { a, b -> a >= b }
        "property" -> false // a bare boolean property is false at rest
        else -> true
    }

    private fun compare(element: Element, test: (Double, Double) -> Boolean): Boolean {
        val operands = element.children().map { operand ->
            when (operand.tagName) {
                "property" -> 0.0
                "value" -> operand.textContent.trim().toDoubleOrNull() ?: return true
                else -> return true
            }
        }
        if (operands.size != 2) return true
        return test(operands[0], operands[1])
    }
}
