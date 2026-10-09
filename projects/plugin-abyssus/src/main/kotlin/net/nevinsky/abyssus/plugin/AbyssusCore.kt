/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.ModelLogging
import net.nevinsky.abyssus.lib.gdx.editor.flightgear.FlightGearImport
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelImport
import net.nevinsky.abyssus.lib.gdx.editor.modelimport.ModelSourceOpener
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat as CoreDocumentFormat
import net.nevinsky.abyssus.lib.gdx.editor.document.AbyssusDocumentFormat
import net.nevinsky.abyssus.plugin.log.IntellijLoggerFactory

/** Application composition root. Groups build only on first use; entry points pass narrow collaborators onward. */
@Service(Service.Level.APP)
class AbyssusCore : Disposable {
    val loggers = IntellijLoggerFactory("Abyssus")
    init { ModelLogging.logger = loggers.getLogger("model") }
    val json by lazy { JsonProcessor(loggers.getLogger("json")) }
    val format by lazy { AbyssusDocumentFormat() }
    val documents by lazy { DocumentServices(json, format, loggers.getLogger("scenes")) }
    val assets by lazy { AssetServices(json, format, loggers.getLogger("assets")) }
    val terrain by lazy { TerrainServices(json) }
    private val rayHolder = lazy { RayServices(loggers.getLogger("ray")) { assets.loading.toneCurve.exposure } }
    internal val ray get() = rayHolder.value

    // Source compatibility for existing callers; production entry points use the groups.
    val metaFiles get() = assets.metaFiles
    val assetFields get() = assets.fields
    val assetEditor get() = assets.editor
    val loading get() = assets.loading
    val hdrPreviews get() = assets.hdrPreviews
    val sceneShaders get() = assets.sceneShaders
    val terrainGenerator get() = terrain.generator
    val heightEncoder get() = terrain.heightEncoder
    val terrainWriter get() = terrain.writer
    val terrainRecipes get() = terrain.recipes
    val newTerrains get() = terrain.newTerrains
    val flightGearImport by lazy { FlightGearImport(json, CoreDocumentFormat()) }
    val modelSources by lazy { ModelSourceOpener() }
    val modelImport by lazy { ModelImport(json, CoreDocumentFormat()) }
    internal val rayService get() = ray.service
    internal val rayConverter get() = ray.converter

    override fun dispose() { if (rayHolder.isInitialized()) rayHolder.value.close() }
}
