/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview


import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageDrawable
import net.nevinsky.abyssus.lib.core.assets.loading.ShaderStorage
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.core.editor.content.*
import net.nevinsky.abyssus.lib.core.editor.pick.*
import net.nevinsky.abyssus.lib.core.editor.scene.LightSet
import net.nevinsky.abyssus.lib.core.editor.scene.MAX_SPOT
import net.nevinsky.abyssus.lib.core.editor.scene.NO_LIGHTS
import net.nevinsky.abyssus.lib.core.editor.scene.SceneContent
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.lib.gdx.g3d.FilteredRenderables
import net.nevinsky.abyssus.lib.gdx.g3d.ModelShaderProvider
import net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute
import net.nevinsky.abyssus.lib.render.light.setSkyLight
import net.nevinsky.abyssus.lib.gdx.shader.DirectionalVisibilityAttribute
import net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute
import net.nevinsky.abyssus.plugin.foliage.FoliageDrafts
import net.nevinsky.abyssus.lib.gdx.shader.ClipPlaneAttribute
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import net.nevinsky.abyssus.plugin.sceneview.terrain.TerrainShader

internal enum class ContentParts { ALL, OPAQUE, BLENDED }

/** Owns content draw lists and programs; only snapshot packing and list queries are CPU-only. */
internal class SceneContentPass(
    assets: ViewAssets,
    private val shaders: ShaderStorage,
    foliageDrafts: FoliageDrafts,
) {
    private var contentBatch: ModelBatch? = null
    private var contentShaders: ModelShaderProvider? = null

    val models = SceneModels(AssetView(assets, com.badlogic.gdx.graphics.g3d.Model::class.java))
    val terrains = SceneTerrains(AssetView(assets, TerrainMesh::class.java))
    val foliage = SceneFoliage(ViewFoliageAssets(AssetView(assets, FoliageDrawable::class.java)), foliageDrafts)
    private var terrainShader: TerrainShader? = null

    val environment = Environment()
    @Volatile var fogCoefficient: Float? = null
    fun create(resources: RenderResources) {
        contentShaders = resources.own(ModelShaderProvider(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config().apply {
            numSpotLights = MAX_SPOT
        }), ModelShaderProvider::dispose).value.also { contentBatch = ModelBatch(it) }
        terrainShader = resources.own(TerrainShader(shaders), TerrainShader::dispose).value
    }

    fun abandon() {
        models.abandon()
        terrains.abandon()
        foliage.abandon()
        clearHandles()
    }

    fun dispose() {
        models.dispose()
        terrains.dispose()
        foliage.dispose()
        clearHandles()
    }

    private fun clearHandles() {
        terrainShader = null
        contentShaders = null
        contentBatch = null
    }

    fun snapshot(camera: PerspectiveCamera, drawnVersion: Long): FrameSnapshot {
        return FrameSnapshot(
            copyOfCamera(camera),
            models.drawn.map { snapshotBoxOf(it.placement.entityId, it.localBounds, it.instance.transform!!) },
            terrains.drawn.map { snapshotTerrainOf(TerrainTarget(it.placement.entityId, it.terrain.data, it.world)) },
            drawnVersion,
        )
    }
    /** Which parts of the content a pass draws: all of it; the opaque and alpha-tested parts; or the blended model parts only. */
    private fun isBlended(renderable: Renderable): Boolean =
        (renderable.material?.get(BlendingAttribute.Type) as? BlendingAttribute)?.blended == true

    fun render(
        p: SceneRenderParams,
        c: SceneContent,
        atlas: ShadowAtlasAttribute?,
        view: Camera,
        clip: FloatArray? = null,
        parts: ContentParts = ContentParts.ALL,
        display: ViewDisplay,
        skybox: SceneSkybox?,
        frameLights: LightSet,
        sunVisibility: DirectionalVisibilityAttribute?,
    ) {
        val unlit = display.shading == Shading.UNLIT
        val contentBatch = contentBatch ?: return
        // after the grid: a built HDR sky replaces the ambient color for the content only (applyEnvironment undoes it)
        val ambient = SceneAmbient.of(c.skybox, p.ambient) { skybox?.environment(it) }
        val sky = if (unlit) null else (ambient as? SceneAmbient.Sky)?.environment
        val to = if (sky != null) skybox?.fadingTo(c.skybox) else null
        if (sky != null) {
            environment.setSkyLight(sky, to?.environment, to?.weight ?: 0f)
        }
        if (atlas != null) environment.set(atlas)
        if (!unlit) sunVisibility?.let { environment.set(it) }
        if (parts != ContentParts.BLENDED) {
            terrainShader?.draw(
                view, terrains.drawn, if (unlit) UNLIT_AMBIENT else p.ambient, if (display.fog) p.fog else null,
                if (unlit) NO_LIGHTS else frameLights, sky?.irradiance, atlas,
                to?.environment?.irradiance, to?.weight ?: 0f, if (unlit) null else sunVisibility, clip,
            )
        }
        if (clip != null) environment.set(ClipPlaneAttribute(clip))
        val accept: ((Renderable) -> Boolean)? = when (parts) {
            ContentParts.ALL -> null
            ContentParts.OPAQUE -> { r -> !isBlended(r) }
            ContentParts.BLENDED -> { r -> isBlended(r) }
        }
        try {
            contentBatch.begin(view)
            for (entity in models.drawn) {
                contentBatch.render(if (accept == null) entity.instance else FilteredRenderables(entity.instance, accept), environment)
            }
            if (parts != ContentParts.BLENDED) foliage.draw(contentBatch, environment, accept)
            contentBatch.end()
        } finally {
            environment.remove(ClipPlaneAttribute.Type)
        }
    }

    /** Fog color comes from the environment; density through [net.nevinsky.abyssus.plugin.sceneview.fog.FogShader] (see [FogParams] for what cannot be matched). */
    fun applyEnvironment(p: SceneRenderParams, display: ViewDisplay) {
        val unlit = display.shading == Shading.UNLIT
        environment.remove(ShadowAtlasAttribute.Type)
        environment.remove(DirectionalVisibilityAttribute.Type)
        environment.remove(EnvironmentLightAttribute.Type)
        val ambient = if (unlit) UNLIT_AMBIENT else p.ambient
        if (ambient != null) {
            environment.set(ColorAttribute(ColorAttribute.AmbientLight, ambient.r, ambient.g, ambient.b, 1f))
        } else {
            environment.remove(ColorAttribute.AmbientLight)
        }
        val fog = if (display.fog) p.fog else null
        if (fog != null) {
            environment.set(ColorAttribute(ColorAttribute.Fog, fog.color.r, fog.color.g, fog.color.b, 1f))
            fogCoefficient = fog.shaderCoefficient
        } else {
            environment.remove(ColorAttribute.Fog)
            fogCoefficient = null
        }
    }

    private val UNLIT_AMBIENT = Color(1f, 1f, 1f, 1f)
}
