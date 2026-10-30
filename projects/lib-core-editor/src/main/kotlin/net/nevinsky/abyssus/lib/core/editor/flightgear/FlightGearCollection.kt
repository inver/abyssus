/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.flightgear

/** Reads aircraft placements and inspection data without GL or platform state. */
internal class FlightGearCollection(private val ac3d: Ac3dReader, private val rest: RestState) {
    fun inspect(
        archive: FlightGearArchive,
        aircraft: FlightGearAircraft,
        cancel: () -> Unit = {}
    ): FlightGearInspection {
        val collected = collect(archive, aircraft, cancel)
        val hidden = collected.selects.filter { !rest.shows(it.condition) }.flatMap { it.parts }.toSet()
        val shown = collected.selects.filter { rest.shows(it.condition) }.flatMap { it.parts }.toSet()
        val parts = collected.placements.flatMap { placement -> placement.objects().mapNotNull { it.name } }.distinct()
            .map { ImportPart(it, it in shown || it !in hidden) }
        return FlightGearInspection(
            aircraft, parts, collected.skipped + textureProblems(archive, collected),
            license(archive, aircraft), archive.licenseFiles(aircraft.folder)
        )
    }

    /** The licence the set file states, or the archive's licence files; null when neither exists. */
    fun license(archive: FlightGearArchive, aircraft: FlightGearAircraft): String? =
        aircraft.license ?: archive.licenseFiles(aircraft.folder).takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "see ") { it.substringAfterLast('/') }

    fun textureProblems(archive: FlightGearArchive, collected: Collected): List<SkippedItem> =
        collected.placements.flatMap { placement ->
            placement.objects().mapNotNull { obj ->
                val texture = obj.texture ?: return@mapNotNull null
                if (textureSource(archive, collected.aircraft, placement, texture) != null) null
                else SkippedItem(texture.substringAfterLast('/'), "texture missing from the archive")
            }
        }.distinct()

    /** The archive path of an AC texture: by file name, in the AC file's folder first, then anywhere in the aircraft. */
    fun textureSource(
        archive: FlightGearArchive,
        aircraft: FlightGearAircraft,
        placement: Placement,
        texture: String
    ): String? {
        val name = texture.replace('\\', '/').substringAfterLast('/')
        if (name.isEmpty()) return null
        val folder = placement.acPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        return archive.findByName(folder, name).firstOrNull() ?: archive.findByName(aircraft.folder, name).firstOrNull()
    }

    fun collect(archive: FlightGearArchive, aircraft: FlightGearAircraft, cancel: () -> Unit): Collected {
        val modelPath = aircraft.modelPath ?: throw FlightGearArchiveException("${aircraft.id} names no model")
        val placements = ArrayList<Placement>()
        val selects = ArrayList<SelectAnimation>()
        val skipped = ArrayList<SkippedItem>()
        val reader = FlightGearModelXmlReader(archive)

        fun placeAc(path: String, offsets: List<FlightGearOffsets>) {
            val bytes = archive.read(path)
            if (bytes == null) {
                skipped += SkippedItem(path, "model file missing from the archive")
                return
            }
            placements += Placement(path, ac3d.read(String(bytes, Charsets.ISO_8859_1), path), offsets)
        }

        fun visit(path: String, offsets: List<FlightGearOffsets>, depth: Int) {
            cancel()
            when {
                depth > MAX_MODEL_DEPTH -> skipped += SkippedItem(path, "nested deeper than $MAX_MODEL_DEPTH models")
                path.endsWith(".ac", ignoreCase = true) -> placeAc(path, offsets)
                path.endsWith(".xml", ignoreCase = true) -> {
                    if (archive.read(path) == null) {
                        skipped += SkippedItem(path, "model file missing from the archive")
                        return
                    }
                    val xml = reader.read(path)
                    val here = offsets + xml.offsets
                    if (xml.panels > 0) skipped += SkippedItem(
                        "${xml.panels} instrument panel(s) in ${
                            path.substringAfterLast(
                                '/'
                            )
                        }", "panels are not imported"
                    )
                    selects += xml.selects
                    when {
                        xml.acPath == null && xml.acWritten != null -> skipped += SkippedItem(
                            xml.acWritten,
                            "path leaves the archive"
                        )

                        xml.acPath != null -> visit(xml.acPath, here, depth + 1)
                    }
                    for (nested in xml.nested) {
                        if (nested.path == null) skipped += SkippedItem(nested.written, "path leaves the archive")
                        else visit(nested.path, here + nested.offsets, depth + 1)
                    }
                }

                else -> skipped += SkippedItem(path, "only AC3D models are imported")
            }
        }
        visit(modelPath, emptyList(), 0)
        return Collected(aircraft, placements, selects, skipped)
    }
}

internal class Collected(
    val aircraft: FlightGearAircraft,
    val placements: List<Placement>,
    val selects: List<SelectAnimation>,
    val skipped: List<SkippedItem>,
)

/** One AC3D file placed by [offsets] (FlightGear frame), outermost first. */
internal class Placement(val acPath: String, val model: Ac3dModel, val offsets: List<FlightGearOffsets>) {
    fun objects(): List<Ac3dObject> = objectsWithTransforms().map { it.first }

    /** Every poly object with the matrix from its own vertices to the Abyssus frame (before centring). */
    fun objectsWithTransforms(): List<Pair<Ac3dObject, Matrix>> {
        var fgToAbyssus = FG_TO_ABYSSUS
        for (o in offsets) fgToAbyssus = fgToAbyssus * offsetsMatrix(o)
        val acToAbyssus = fgToAbyssus * AC_TO_FG
        val out = ArrayList<Pair<Ac3dObject, Matrix>>()
        fun walk(obj: Ac3dObject, parent: Matrix) {
            val world = parent * acMatrix(obj.rotation, obj.location)
            if (obj.type == "poly") out += obj to world
            obj.kids.forEach { walk(it, world) }
        }
        walk(model.world, acToAbyssus)
        return out
    }
}

