/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import com.badlogic.gdx.utils.Disposable
import org.slf4j.Logger
import java.util.EnumMap
import java.util.EnumSet

/** The technique to try when [technique] cannot be built: volumetric to shells to layered, then none. */
fun simplerThan(technique: CloudTechnique): CloudTechnique? = when (technique) {
    CloudTechnique.VOLUMETRIC -> CloudTechnique.SHELLS
    CloudTechnique.SHELLS -> CloudTechnique.LAYERED
    CloudTechnique.LAYERED -> null
}

/**
 * The cloud renderers of one sky, each [build] the first time its technique is asked for and kept until [dispose]. A
 * technique that cannot be built, or fails while drawing, is logged once and never tried again; the next simpler one
 * is used in its place (see [simplerThan]). GL thread only.
 */
class CloudTechniques(
    private val skyName: String,
    private val log: Logger,
    private val build: (CloudTechnique) -> CloudRenderer,
) : Disposable {
    private val built = EnumMap<CloudTechnique, CloudRenderer>(CloudTechnique::class.java)
    private val failed = EnumSet.noneOf(CloudTechnique::class.java)

    /** True when no technique can draw clouds any more. */
    val exhausted: Boolean get() = failed.size == CloudTechnique.entries.size

    /** The renderer of [wanted] or of the simplest that can stand in for it, with its technique; null when none can. */
    fun renderer(wanted: CloudTechnique): Pair<CloudTechnique, CloudRenderer>? {
        var technique: CloudTechnique? = wanted
        while (technique != null) {
            built[technique]?.let { return technique to it }
            if (technique !in failed) {
                try {
                    return technique to build(technique).also { built[technique] = it }
                } catch (e: Exception) {
                    fail(technique, e)
                }
            }
            technique = simplerThan(technique)
        }
        return null
    }

    /** Gives up on [technique] after [error]: logs it once, releases what was built and falls back from now on. */
    fun fail(technique: CloudTechnique, error: Throwable) {
        if (!failed.add(technique)) return
        val fallback = generateSequence(simplerThan(technique), ::simplerThan).firstOrNull { it !in failed }
        log.warn(
            "Sky '$skyName': ${technique.key} clouds are unavailable (${error.message}); " +
                (fallback?.let { "drawing ${it.key} clouds instead" } ?: "drawing the sky without clouds"),
            error,
        )
        built.remove(technique)?.let { runCatchingDispose(it) }
    }

    override fun dispose() {
        built.values.forEach(::runCatchingDispose)
        built.clear()
    }

    private fun runCatchingDispose(renderer: CloudRenderer) {
        try {
            renderer.dispose()
        } catch (e: Exception) {
            log.debug("Sky '$skyName': releasing a cloud renderer failed", e)
        }
    }
}
