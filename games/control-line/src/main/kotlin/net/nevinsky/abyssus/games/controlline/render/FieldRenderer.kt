/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.games.controlline.render

import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.ModelBatch
import net.nevinsky.abyssus.core.ModelInstance
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.assets.loading.AssetStorage
import net.nevinsky.abyssus.core.assets.loading.CompositeAssetLoader
import net.nevinsky.abyssus.core.assets.loading.PreparedAsset
import net.nevinsky.abyssus.core.assets.loading.ShaderSource
import net.nevinsky.abyssus.core.assets.model.ModelLoader
import net.nevinsky.abyssus.core.assets.sky.Sky
import net.nevinsky.abyssus.core.assets.sky.cube.SkyboxLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.ExrLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.HdrSkyLoader
import net.nevinsky.abyssus.core.assets.sky.hdr.ToneCurve
import net.nevinsky.abyssus.core.assets.sky.procedural.ProceduralSkyLoader
import net.nevinsky.abyssus.core.assets.terrain.TerrainLoader
import net.nevinsky.abyssus.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.core.assets.texture.TextureLoader
import net.nevinsky.abyssus.core.loader.AssimpModelLoader
import net.nevinsky.abyssus.core.model.Model
import net.nevinsky.abyssus.core.shader.DefaultShaderProvider
import net.nevinsky.abyssus.core.shader.ShaderProvider
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Executor

/**
 * The one asset storage of the project in [projectDir], wired by constructors like the plugin does: a composite over
 * every kind of asset, prepared on [executor] and built (and owned) by the storage on the GL thread.
 */
fun fieldAssets(projectDir: File, json: JsonProcessor, log: Logger, executor: Executor): AssetStorage<PreparedAsset, Disposable> {
    val files = FileLoader(projectDir)
    val metas = AssetMetaLoader(json, files, log)
    val skyShaders = ShaderSource("/shader/sky", ShaderSource::class.java)
    val composite = CompositeAssetLoader(
        metas,
        mapOf(
            MetaType.MODEL to ModelLoader(metas, AssimpModelLoader(), files),
            MetaType.TERRAIN to TerrainLoader(files, metas),
            MetaType.TEXTURE to TextureLoader(files, metas),
            MetaType.PIXMAP_TEXTURE to TextureLoader(files, metas),
            MetaType.SKYBOX to SkyboxLoader(files, metas, skyShaders),
            MetaType.SKYBOX_PROCEDURAL to ProceduralSkyLoader(files, metas),
            MetaType.SKYBOX_HDR to HdrSkyLoader(metas, ExrLoader(files), skyShaders, ToneCurve()),
        ),
    )
    return AssetStorage(executor, composite, log)
}

/** A segment to draw: a control line from [from] to [to], in [color]. */
class LineSegment(val from: Vector3, val to: Vector3, val color: Color)

/**
 * Draws a [FieldScene] (design decision 8): the sky, the terrain with the game's own shader, the models with the
 * default shader lit by the scene's sun and ambient light, and the control lines. Assets load through `core`'s
 * [AssetStorage] ([fieldAssets]): prepared off the GL thread, built here. GL thread only.
 */
class FieldRenderer(private val assets: AssetStorage<PreparedAsset, Disposable>, shaders: ShaderSource) : Disposable {
    private val modelShaders = DefaultShaderProvider()
    private val batch = ModelBatch(modelShaders)
    private val terrainShader = shaders.program("terrain")
    private val blank = Texture(Pixmap(1, 1, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }, false)
    private val shapes = ShapeRenderer()
    private val instances = HashMap<Entity, Pair<Model, ModelInstance>>()
    private var scene: FieldScene? = null
    private val environment = Environment()
    private val sun = DirectionalLight()

    /** Whether the scene's assets are all built. */
    fun loaded(field: FieldScene): Boolean =
        field.models.all { assets.getAs<Model>(it.second) != null } && field.terrains.all { assets.getAs<TerrainMesh>(it.second) != null }

    /** Draws [field] seen from [camera] with [lines]; [hidden] (the pilot whose eyes the camera is) is left out. */
    fun draw(field: FieldScene, camera: Camera, lines: List<LineSegment> = emptyList(), hidden: Entity? = null) {
        if (scene !== field) {
            instances.clear()
            scene = field
            environment.clear()
            environment.set(ColorAttribute(ColorAttribute.AmbientLight, field.ambient))
            field.fogColor?.let { environment.set(ColorAttribute(ColorAttribute.Fog, it)) }
            sun.set(field.sunColor, Vector3(field.sunDirection).scl(-1f))
            environment.add(sun)
        }
        val wanted = field.models.map { it.second }.toSet() + field.terrains.map { it.second } + setOfNotNull(field.skyName)
        wanted.forEach(assets::request)
        assets.pump()

        val fog = field.fogColor ?: Color(0.6f, 0.7f, 0.85f, 1f)
        Gdx.gl.glClearColor(fog.r, fog.g, fog.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        field.skyName?.let { assets.getAs<Sky>(it) }?.let { sky ->
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthMask(false)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            sky.draw(camera, field.sunDirection)
            Gdx.gl.glDepthMask(true)
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        terrainShader.bind()
        terrainShader.setUniformMatrix("u_projViewTrans", camera.combined)
        terrainShader.setUniformf("u_cameraPosition", camera.position)
        terrainShader.setUniformf("u_fogDensity", if (field.fogColor != null) field.fogDensity else 0f)
        terrainShader.setUniformf("u_fogGradient", field.fogGradient)
        terrainShader.setUniformf("u_fogColor", fog.r, fog.g, fog.b)
        terrainShader.setUniformf("u_lightDirection", field.sunDirection)
        terrainShader.setUniformf("u_lightColor", field.sunColor.r, field.sunColor.g, field.sunColor.b)
        terrainShader.setUniformf("u_ambient", field.ambient.r, field.ambient.g, field.ambient.b)
        for ((entity, name) in field.terrains) {
            val mesh = assets.getAs<TerrainMesh>(name) ?: continue
            terrainShader.setUniformMatrix("u_worldTrans", field.position(entity).getTransform())
            mesh.draw(terrainShader, blank)
        }

        batch.begin(camera)
        for ((entity, name) in field.models) {
            if (entity === hidden) continue
            val model = assets.getAs<Model>(name) ?: continue
            val instance = instances[entity]?.takeIf { it.first === model }?.second
                ?: ModelInstance(model).also { instances[entity] = model to it }
            instance.transform!!.set(field.position(entity).getTransform())
            batch.render(instance, environment, ShaderProvider.DEFAULT_SHADER_KEY)
        }
        batch.end()

        if (lines.isNotEmpty()) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            shapes.projectionMatrix = camera.combined
            shapes.begin(ShapeRenderer.ShapeType.Line)
            for (line in lines) {
                shapes.color = line.color
                shapes.line(line.from, line.to)
            }
            shapes.end()
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
    }

    override fun dispose() {
        assets.dispose()
        modelShaders.dispose()
        terrainShader.dispose()
        blank.dispose()
        shapes.dispose()
    }
}
