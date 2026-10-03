/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky

import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.loading.AssetLoader
import net.nevinsky.abyssus.assets.sky.cube.PreparedSkybox
import net.nevinsky.abyssus.assets.sky.cube.SkyboxCube
import net.nevinsky.abyssus.assets.sky.hdr.HdrSky
import net.nevinsky.abyssus.assets.sky.hdr.PreparedHdrSky
import net.nevinsky.abyssus.assets.sky.procedural.PreparedProceduralSky
import net.nevinsky.abyssus.assets.sky.procedural.ProceduralSky

/**
 * What a scene's `skyboxName` was loaded as: the [prepared] data of whichever loader read it (a cube, a procedural sky
 * or an HDR sky) together with that loader, which uploads, builds and discards it.
 */
class PreparedSky internal constructor(
    /** What the sky's own loader prepared. */
    val prepared: Any,
    private val loader: AssetLoader<Any, out Sky>,
) {
    internal fun upload(): Boolean = loader.upload(prepared)
    internal fun build(): Sky = loader.build(prepared)
    internal fun discard() = loader.discard(prepared)
}

/** Pairs [prepared] with the [loader] that made it; the one place the sky kinds are erased. */
@Suppress("UNCHECKED_CAST")
private fun <P : Any> preparedSky(loader: AssetLoader<P, out Sky>, prepared: P?): PreparedSky? =
    prepared?.let { PreparedSky(it, loader as AssetLoader<Any, out Sky>) }

/**
 * Loads whichever kind of sky the named asset folder holds, by its `meta.json` type, through [cube], [procedural] or
 * [hdr]: a [SkyboxCube], a [ProceduralSky] or an [HdrSky].
 */
class SkyLoader(
    private val cube: AssetLoader<PreparedSkybox, SkyboxCube>,
    private val procedural: AssetLoader<PreparedProceduralSky, ProceduralSky>,
    private val hdr: AssetLoader<PreparedHdrSky, HdrSky>,
) : AssetLoader<PreparedSky, Sky> {
    override fun prepare(files: AssetFiles, name: String): PreparedSky? =
        when (files.metaType(name) ?: return null) {
            MetaType.SKYBOX_PROCEDURAL -> preparedSky(procedural, procedural.prepare(files, name))
            MetaType.SKYBOX_HDR -> preparedSky(hdr, hdr.prepare(files, name))
            else -> preparedSky(cube, cube.prepare(files, name))
        }

    override fun upload(prepared: PreparedSky): Boolean = prepared.upload()

    override fun build(prepared: PreparedSky): Sky = prepared.build()

    override fun discard(prepared: PreparedSky) = prepared.discard()
}
