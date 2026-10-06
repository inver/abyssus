/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.flightgear

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.collections.iterator

/** The fixture skin texture's pixel (x, y from the top, channel c) as a byte value. */
fun fixtureSkinPixel(x: Int, y: Int, c: Int): Int = (x * 60 + y * 100 + c * 20) and 0xFF

/**
 * An RLE SGI image of [width] x [height] with [channels] channels, [pixel] giving each byte (x, y from the top). Each
 * row is stored as literal runs, so the decoder's copy path is used; a constant row uses a repeat run.
 */
fun sgiRle(width: Int, height: Int, channels: Int, pixel: (Int, Int, Int) -> Int): ByteArray {
    val header = ByteBuffer.allocate(512)
    header.putShort(0, 474)
    header.put(2, 1) // RLE
    header.put(3, 1) // one byte per channel
    header.putShort(4, 3)
    header.putShort(6, width.toShort())
    header.putShort(8, height.toShort())
    header.putShort(10, channels.toShort())
    header.putInt(16, 255)
    val tables = height * channels
    val starts = IntArray(tables)
    val lengths = IntArray(tables)
    val rows = ByteArrayOutputStream()
    val dataStart = 512 + tables * 8
    for (c in 0 until channels) for (row in 0 until height) {
        val y = height - 1 - row // SGI rows are bottom-up
        val values = IntArray(width) { pixel(it, y, c) }
        val start = rows.size()
        if (values.all { it == values[0] }) {
            rows.write(width)
            rows.write(values[0])
        } else {
            rows.write(0x80 or width)
            values.forEach(rows::write)
        }
        rows.write(0)
        starts[row + c * height] = dataStart + start
        lengths[row + c * height] = rows.size() - start
    }
    val out = ByteBuffer.allocate(dataStart + rows.size())
    out.put(header.array())
    starts.forEach(out::putInt)
    lengths.forEach(out::putInt)
    out.put(rows.toByteArray())
    return out.array()
}

/** AC3D text of the fixture plane. Nose toward -X, up +Y, left wing toward +Z, as FlightGear models are. */
fun fixtureAc(): String = buildString {
    appendLine("AC3Db")
    appendLine("MATERIAL \"grey\" rgb 0.5 0.5 0.5  amb 0.2 0.2 0.2  emis 0 0 0  spec 0.1 0.1 0.1  shi 10  trans 0")
    appendLine("MATERIAL \"white\" rgb 1 1 1  amb 0.2 0.2 0.2  emis 0 0 0  spec 0.1 0.1 0.1  shi 10  trans 0")
    appendLine("MATERIAL \"glass\" rgb 0.2 0.3 0.4  amb 0.2 0.2 0.2  emis 0 0 0  spec 0.5 0.5 0.5  shi 64  trans 0.5")
    appendLine("OBJECT world")
    appendLine("kids 5")
    // Body: a box from the nose (x = -1) to the tail (x = 1), smooth, textured through an author's absolute path
    appendLine("OBJECT poly")
    appendLine("name \"Body\"")
    appendLine("texture \"/home/author/src/skin.rgb\"")
    appendLine("crease 45.0")
    val box = listOf(
        -1f to 0.2f to -0.2f, 1f to 0.2f to -0.2f, 1f to 0.6f to -0.2f, -1f to 0.6f to -0.2f,
        -1f to 0.2f to 0.2f, 1f to 0.2f to 0.2f, 1f to 0.6f to 0.2f, -1f to 0.6f to 0.2f,
    )
    appendLine("numvert ${box.size + 1}")
    box.forEach { (xy, z) -> appendLine("${xy.first} ${xy.second} $z") }
    appendLine("0 1.2 0")
    val faces = listOf(listOf(0, 3, 2, 1), listOf(4, 5, 6, 7), listOf(0, 1, 5, 4), listOf(3, 7, 6, 2), listOf(0, 4, 7, 3), listOf(1, 2, 6, 5))
    appendLine("numsurf ${faces.size + 1}")
    for (face in faces) {
        appendLine("SURF 0x10")
        appendLine("mat 0")
        appendLine("refs 4")
        face.forEachIndexed { i, v -> appendLine("$v ${if (i == 1 || i == 2) 1 else 0} ${if (i >= 2) 1 else 0}") }
    }
    appendLine("SURF 0x02") // the antenna: a line, which is not imported
    appendLine("mat 0")
    appendLine("refs 2")
    appendLine("7 0 0")
    appendLine("8 0 0")
    appendLine("kids 0")
    // Wing: a two-sided quad, 3 m from tip to tip
    quad("Wing", null, 1, 0x20, listOf(-0.2f to 0.6f to -1.5f, 0.2f to 0.6f to -1.5f, 0.2f to 0.6f to 1.5f, -0.2f to 0.6f to 1.5f))
    quad("Prop", null, 0, 0, listOf(-1.05f to 0.3f to -0.3f, -1.05f to 0.3f to 0.3f, -1.05f to 0.5f to 0.3f, -1.05f to 0.5f to -0.3f))
    quad("PropDisc", null, 2, 0x20, listOf(-1.06f to 0.1f to -0.5f, -1.06f to 0.1f to 0.5f, -1.06f to 0.7f to 0.5f, -1.06f to 0.7f to -0.5f))
    quad("Tail", "missing.rgb", 0, 0, listOf(0.8f to 0.6f to 0f, 1f to 0.6f to 0f, 1f to 1f to 0f, 0.8f to 1f to 0f))
}

/** AC3D text of the fixture's nested wheel: a box 0.2 m tall at the origin. */
fun fixtureWheelAc(): String = buildString {
    appendLine("AC3Db")
    appendLine("MATERIAL \"black\" rgb 0.05 0.05 0.05  amb 0 0 0  emis 0 0 0  spec 0 0 0  shi 0  trans 0")
    appendLine("OBJECT world")
    appendLine("kids 1")
    quad("Wheel", null, 0, 0, listOf(-0.1f to 0f to 0f, 0.1f to 0f to 0f, 0.1f to 0.2f to 0f, -0.1f to 0.2f to 0f))
}

private fun StringBuilder.quad(name: String, texture: String?, material: Int, flags: Int, points: List<Pair<Pair<Float, Float>, Float>>) {
    appendLine("OBJECT poly")
    appendLine("name \"$name\"")
    texture?.let { appendLine("texture \"$it\"") }
    appendLine("numvert ${points.size}")
    points.forEach { (xy, z) -> appendLine("${xy.first} ${xy.second} $z") }
    appendLine("numsurf 1")
    appendLine("SURF 0x${flags.toString(16)}")
    appendLine("mat $material")
    appendLine("refs ${points.size}")
    points.indices.forEach { appendLine("$it ${it % 2} ${it / 2}") }
    appendLine("kids 0")
}

/** The fixture aircraft's model XML: the AC file, a nested wheel 0.5 m ahead (FlightGear x is aft), a panel, selects. */
fun fixtureModelXml(): String = """
    <?xml version="1.0"?>
    <PropertyList>
     <path>fixture.ac</path>
     <panel><path>Aircraft/fixture/Panels/panel.xml</path></panel>
     <model>
      <path>Aircraft/fixture/Models/wheel.xml</path>
      <offsets><x-m>-0.5</x-m></offsets>
     </model>
     <animation>
      <type>select</type>
      <object-name>Prop</object-name>
      <condition><less-than><property>engines/engine[0]/rpm</property><value>500</value></less-than></condition>
     </animation>
     <animation>
      <type>select</type>
      <object-name>PropDisc</object-name>
      <condition><greater-than><property>engines/engine[0]/rpm</property><value>300</value></greater-than></condition>
     </animation>
     <animation><type>rotate</type><object-name>Prop</object-name></animation>
    </PropertyList>
""".trimIndent()

/** The files of the fixture archive, keyed by their path in it. [license] adds a `COPYING` file. */
fun fixtureFiles(license: Boolean = false): Map<String, ByteArray> = buildMap {
    put("fixture/fixture-set.xml", """
        <?xml version="1.0"?>
        <PropertyList>
         <sim>
          <description>Fixture Plane</description>
          <author>Test Author (3D model)</author>
          <model><path>Aircraft/fixture/Models/fixture.xml</path></model>
         </sim>
        </PropertyList>
    """.trimIndent().toByteArray())
    put("fixture/Models/fixture.xml", fixtureModelXml().toByteArray())
    put("fixture/Models/fixture.ac", fixtureAc().toByteArray())
    put("fixture/Models/wheel.xml", "<?xml version=\"1.0\"?>\n<PropertyList><path>wheel.ac</path></PropertyList>".toByteArray())
    put("fixture/Models/wheel.ac", fixtureWheelAc().toByteArray())
    put("fixture/Models/skin.rgb", sgiRle(4, 2, 3, ::fixtureSkinPixel))
    put("fixture/Panels/panel.xml", "<?xml version=\"1.0\"?>\n<PropertyList/>".toByteArray())
    if (license) put("fixture/COPYING", "Fixture licence text\n".toByteArray())
}

/** Writes [files] as a zip archive to [target] and returns it. */
fun writeZip(target: File, files: Map<String, ByteArray>): File {
    ZipOutputStream(target.outputStream()).use { zip ->
        for ((path, bytes) in files) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return target
}

/** The fixture archive in [folder]. */
fun fixtureArchive(folder: File, license: Boolean = false): File = writeZip(File(folder, "fixture.zip"), fixtureFiles(license))
