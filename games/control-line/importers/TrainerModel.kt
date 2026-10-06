/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("TrainerModel")

package net.nevinsky.abyssus.games.controlline.tools

import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.flightgear.FlightGearArchive
import net.nevinsky.abyssus.core.flightgear.FlightGearImport
import net.nevinsky.abyssus.core.flightgear.FlightGearImportRequest
import net.nevinsky.abyssus.core.flightgear.IMPORTED_MODEL_FILE
import net.nevinsky.abyssus.core.flightgear.ImportOrigin
import net.nevinsky.abyssus.core.flightgear.ImportSize
import net.nevinsky.abyssus.core.format.AbyssusDocumentFormat
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

/** FlightGear's Cessna 172R aircraft archive. */
const val C172R_URL = "https://mirrors.ibiblio.org/flightgear/ftp/Aircraft-2024/c172r.zip"

/** The SHA-256 of the archive the trainer was made from. */
const val C172R_SHA256 = "592fe840e6ab9f69619fe25045bd3b766a9931c1d36965b3b255cbabe6162b2e"

/** The trainer's wingspan, as the generated trainer had (metres). */
const val TRAINER_SPAN = 1.0

/** The Cessna's cabin interior and the propeller's spinning disc: not part of a control-line model. */
val TRAINER_LEFT_OUT = setOf(
    "Cabin", "Seat.1", "Seat.2", "Seat.3", "Seat.4", "Yoke.1", "Yoke.2", "Pedal.1", "Pedal.2", "Pedal.3", "Pedal.4",
    "Throttle", "Mixture", "Compass", "Pedestal", "Propeller.2",
)

/**
 * The trainer's centre of gravity in the Cessna model's FlightGear body frame (metres: x aft, y right, z up): a
 * quarter of the wing's chord behind its leading edge, at the cabin's mid-height. It becomes the model's origin, as
 * the generated planes have theirs (`PlaneModels`). An estimate.
 */
val TRAINER_CENTRE_OF_GRAVITY = ImportOrigin.SourcePoint(0.35, 0.0, 0.0)

/**
 * Re-imports the bundled trainer (`assets/model_trainer`) from FlightGear's Cessna 172R through `core`'s FlightGear
 * import: downloads the archive into the cache folder (checking [C172R_SHA256]), scales it to [TRAINER_SPAN], leaves
 * out [TRAINER_LEFT_OUT] and puts its origin at [TRAINER_CENTRE_OF_GRAVITY]. Writes `model.glb`, `textures/`,
 * `source.json` and `COPYING` (GPL-2.0), and keeps `meta.json`, whose `additional.file` is edited through the editor's
 * scene writer. Running it again gives the same bytes. Run by `./gradlew :games:control-line:importTrainer`.
 *
 * Usage: `TrainerModel <project folder> <cache folder> <GPL-2.0 text>`
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "usage: TrainerModel <project folder> <cache folder> <GPL-2.0 text>" }
    val folder = File(args[0], "assets/model_trainer")
    val archiveFile = cached(File(args[1]))
    val json = JsonProcessor()
    val meta = json.readObject(File(folder, "meta.json").readText())
    val staged = FlightGearArchive(archiveFile).use { archive ->
        val aircraft = archive.aircraft().single { it.id == "c172r" }
        val request = FlightGearImportRequest(aircraft, folder.name, ImportSize.Span(TRAINER_SPAN), TRAINER_LEFT_OUT, TRAINER_CENTRE_OF_GRAVITY)
        FlightGearImport(json, AbyssusDocumentFormat()).stage(archive, request, UUID.fromString(meta["uuid"].asText()), meta["lastModified"].asLong())
    }
    for (old in listOf("model.gltf", "model.bin", IMPORTED_MODEL_FILE, "source.json", "COPYING")) File(folder, old).delete()
    File(folder, "textures").deleteRecursively()
    for ((path, bytes) in staged.files) {
        if (path == "meta.json") continue
        File(folder, path).apply { parentFile.mkdirs() }.writeBytes(bytes)
    }
    val source = json.readObject(File(folder, "source.json").readText()) as ObjectNode
    source.put("license", "GPL-2.0-or-later")
    source.put("licenseNote", "FlightGear aircraft licence; the archive itself states none. Bundled as a separately licensed asset.")
    source.putArray("licenseFiles").add("COPYING")
    source.put("credit", "3D model by David Megginson (FlightGear c172r)")
    File(folder, "source.json").writeText(json.pretty(source))
    File(args[2]).copyTo(File(folder, "COPYING"), overwrite = true)
    println("model_trainer: ${staged.files.size - 1} files, skipped ${staged.skipped.size} item(s)")
}

/** The archive in [cache], downloaded when missing; refuses a file whose SHA-256 is not [C172R_SHA256]. */
private fun cached(cache: File): File {
    val file = File(cache, "c172r.zip")
    if (!file.isFile) {
        cache.mkdirs()
        val part = File(cache, "c172r.zip.part")
        URI(C172R_URL).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
        part.renameTo(file)
    }
    val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    check(sha == C172R_SHA256) { "$file has SHA-256 $sha, not $C172R_SHA256: delete it to download again" }
    return file
}
