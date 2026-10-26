/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.tools

import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** The committed project of the game, supplied by Gradle; never edited by these tests. */
fun committedProject(): File = File(checkNotNull(System.getProperty("controlLine.project")) { "run through Gradle" })

/** The committed manifest next to the project. */
fun committedManifest(): File = File(committedProject().parentFile, "environment/foliage-manifest.json")

/** A copy of [source] in a new temporary folder; [use] gets it and the folder is deleted afterwards. */
fun <T> withCopy(source: File = committedProject(), use: (File) -> T): T {
    val copy = Files.createTempDirectory("field-foliage-test").toFile()
    try {
        source.copyRecursively(copy, overwrite = true)
        return use(copy)
    } finally {
        copy.deleteRecursively()
    }
}

/** SHA-256 of every file under [project] by relative path: what two runs must agree on. */
fun checksums(project: File): Map<String, String> = project.walkTopDown().filter { it.isFile }.sortedBy { it.path }.associate {
    it.relativeTo(project).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
}

/** Historic source recorded by the immutable manifest; later scene edits are replayed separately. */
fun originalScene(): File = File(committedProject().parentFile.parentFile, "toolsTest/fixtures/Field.original.scene")

/**
 * What the migration owns of a scene, which the editor keeps changing everything else in: the `FoliageComponent` of each
 * terrain entity, and how many of the recorded trees and bushes are still entities.
 */
fun migrationFootprint(project: File, manifest: FieldManifest): Map<String, String> {
    val entities = SceneJson().parse(File(project, "scenes/Field.scene").readText())["ecs"]["entities"]
    val out = sortedMapOf<String, String>()
    for (t in manifest.terrains) {
        out["FoliageComponent of ${t.entity}"] = entities[t.entity.toString()]?.get("components")?.get("FoliageComponent")?.toString() ?: "none"
    }
    out["recorded vegetation still in the scene"] = manifest.instances.count { entities.has(it.id.toString()) }.toString()
    return out
}

/** [checksums] without the scene, which the editor keeps authoring after the migration. */
fun assetChecksums(project: File): Map<String, String> = checksums(project).filterKeys { it != "scenes/Field.scene" }

/**
 * A copy of the committed project with the original scene put back (the generated assets stay and are rewritten by a
 * run): the state a migration starts from.
 */
fun <T> withOriginal(use: (File) -> T): T = withCopy { project ->
    originalScene().copyTo(File(project, "scenes/Field.scene"), overwrite = true)
    use(project)
}
