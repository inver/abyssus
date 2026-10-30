/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.perf

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.parseScene
import net.nevinsky.abyssus.lib.core.editor.pick.OrbitCamera
import net.nevinsky.abyssus.lib.core.editor.scene.renderParamsOf
import net.nevinsky.abyssus.lib.core.testing.perf.PixelDiff
import net.nevinsky.abyssus.lib.core.testing.perf.PixelEvidence
import net.nevinsky.abyssus.lib.core.testing.perf.PixelTolerance
import net.nevinsky.abyssus.plugin.sceneview.GlHarness
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files

/**
 * Pixel evidence of the editor's renderer (docs/ai/testing.md, "Graphics performance evidence"). Opt in with
 * `-Dabyssus.glTests=true -Dabyssus.perf.pixels=record|compare`.
 *
 * - `record` renders every case [RUNS] times at a fixed frame count and fixed time step, writes the first render as
 *   `<case>.png` and the loosest difference between runs as `<case>.tolerance.json` in `-Dabyssus.perf.pixelsDir`.
 * - `compare` renders each case once and requires it to be within the recorded tolerance of the PNG in
 *   `-Dabyssus.perf.pixelsReference`.
 *
 * The cases: `untitled` (plain, PBR and terrain), `animated` (skinned), `stress` (instanced placements and foliage) and
 * `cutout` (Untitled with every textured material turned into an alpha-masked one).
 */
class PixelEvidenceGlTest {
    private class Case(val name: String, val workload: EditorWorkload, val frames: Int, val patch: (File) -> Unit = {})

    private val cases = EditorPerfRunner.workloads().associateBy { it.name }.let { w ->
        listOf(
            Case("untitled", w.getValue("untitled"), 300),
            Case("animated", w.getValue("animated"), 300),
            Case("stress", w.getValue("stress"), 700),
            Case("cutout", w.getValue("untitled"), 300, patch = ::maskTexturedMaterials),
        )
    }

    private val evidence = PixelEvidence()
    private val mode = System.getProperty("abyssus.perf.pixels")

    @Test
    fun recordOrCompareTheEditorPixels() {
        assumeTrue("set -Dabyssus.perf.pixels=record|compare", mode == "record" || mode == "compare")
        assumeTrue("GL tests are opt-in", GlHarness.enabled)
        val selected = System.getProperty("abyssus.perf.cases")?.split(',')?.toSet()
        for (case in cases.filter { selected == null || it.name in selected }) {
            if (mode == "record") record(case) else compare(case)
        }
    }

    private fun render(case: Case): BufferedImage {
        val dir = Files.createTempDirectory("pixels-${case.name}").toFile()
        try {
            case.workload.source().copyRecursively(dir, overwrite = true)
            case.workload.prepare(dir)
            case.patch(dir)
            val text = File(dir, case.workload.sceneFile).readText()
            val params = renderParamsOf(parseScene(text), case.workload.camera(dir), dir, SceneJson().parse(text))
            val result = GlHarness.render(
                params, case.frames, OrbitCamera(params.camera), framebufferSize = 1280 to 720,
                log = org.slf4j.helpers.NOPLogger.NOP_LOGGER, timeoutSeconds = 300, fixedDeltaSeconds = 1f / 60f,
            )
            check(result.error == null) { "${case.name}: ${result.error}" }
            return result.image
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun record(case: Case) {
        val out = File(System.getProperty("abyssus.perf.pixelsDir") ?: "build/perf/pixels")
        val first = render(case)
        val diffs = (1 until RUNS).map { evidence.diff(first, render(case)) }
        evidence.writeImage(first, File(out, "${case.name}.png"))
        evidence.writeTolerance(PixelTolerance(case.name, RUNS, first.width, first.height, evidence.toleranceOf(diffs)), File(out, "${case.name}.tolerance.json"))
        println("pixels ${case.name}: repeated-run tolerance ${evidence.toleranceOf(diffs)}")
    }

    private fun compare(case: Case) {
        val reference = File(checkNotNull(System.getProperty("abyssus.perf.pixelsReference")) { "set -Dabyssus.perf.pixelsReference=<dir>" })
        val tolerance = evidence.readTolerance(File(reference, "${case.name}.tolerance.json")).tolerance
        val diff = evidence.diff(evidence.readImage(File(reference, "${case.name}.png")), render(case))
        assertTrue("${case.name}: $diff is outside the recorded tolerance $tolerance", evidence.within(diff, tolerance))
    }

    private fun maskTexturedMaterials(dir: File) {
        val mapper = jacksonObjectMapper()
        dir.walkTopDown().filter { it.name == "model.gltf" }.forEach { file ->
            val root = mapper.readTree(file)
            var changed = false
            root.path("materials").forEach { material ->
                if (material is ObjectNode && material.path("pbrMetallicRoughness").has("baseColorTexture")) {
                    material.put("alphaMode", "MASK").put("alphaCutoff", 0.5)
                    changed = true
                }
            }
            if (changed) file.writeText(mapper.writeValueAsString(root))
        }
    }

    private companion object {
        const val RUNS = 5
    }
}
