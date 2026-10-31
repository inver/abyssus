/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.tools

import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.editor.document.DocumentTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.format.DocumentKind
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
 * Replays the edits authored after migration when reproducing the current committed project: the ray settings
 * (d49555f4) and the Stunter's thrust (add-control-line-wire-physics, which offsets the wire's inertia).
 */
fun applyPostMigrationSceneEdits(project: File) {
    val scene = File(project, "scenes/Field.scene")
    val outcome = DocumentTextEditor().edit(scene.readText(), DocumentKind.SCENE) { root ->
        (root as ObjectNode).putObject("rayTracing")
            .put("maxRaysPerFrame", 268435456)
            .put("targetSamplesPerPixel", 4096)
            .put("maxReflectionBounces", 4)
            .put("maxRefractionBounces", 1)
        val stunter = root.path("ecs").path("entities").path("4").path("components")
        check(stunter.path("NameComponent").path("name").asText() == "Stunter") { "entity 4 is no longer the Stunter" }
        (stunter.path("PlaneComponent") as ObjectNode).put("thrust", 5.2)
        true
    }
    check(outcome is TextEditOutcome.Edited) { "could not replay post-migration ray settings: $outcome" }
    scene.writeText(outcome.text)
}

/**
 * A copy of the committed project with the original scene put back (the generated assets stay and are rewritten by a
 * run): the state a migration starts from.
 */
fun <T> withOriginal(use: (File) -> T): T = withCopy { project ->
    originalScene().copyTo(File(project, "scenes/Field.scene"), overwrite = true)
    use(project)
}
