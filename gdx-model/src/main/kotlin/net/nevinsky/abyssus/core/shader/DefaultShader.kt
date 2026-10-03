/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.core.shader

import org.slf4j.LoggerFactory

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.environment.SpotLight
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.GdxRuntimeException
import net.nevinsky.abyssus.core.Renderable
import java.util.function.Function
import java.util.function.Supplier

private val log = LoggerFactory.getLogger(DefaultShader::class.java)
open class DefaultShader(requestedConfig: ShaderConfig, renderable: Renderable) : BaseShader() {
    protected var vertexShader: String
    protected var fragmentShader: String
    private var initialized = false
    private val tmpAttributes = Attributes()

    // Global uniforms
    protected var u_projTrans: Int = 0
    protected var u_viewTrans: Int = 0
    protected var u_projViewTrans: Int = 0
    protected var u_cameraPosition: Int = 0
    protected var u_cameraDirection: Int = 0
    protected var u_cameraUp: Int = 0
    protected var u_cameraNearFar: Int = 0
    protected var u_time: Int = 0

    // Object uniforms
    protected var u_worldTrans: Int = 0
    protected var u_viewWorldTrans: Int = 0
    protected var u_projViewWorldTrans: Int = 0
    protected var u_normalMatrix: Int = 0
    protected var u_bones: Int = -1

    // Material uniforms
    protected var u_shininess: Int = 0
    protected var u_opacity: Int = 0
    protected var u_diffuseColor: Int = 0
    protected var u_diffuseTexture: Int = 0
    protected var u_diffuseUVTransform: Int = 0
    protected var u_specularColor: Int = 0
    protected var u_specularTexture: Int = 0
    protected var u_specularUVTransform: Int = 0
    protected var u_emissiveColor: Int = 0
    protected var u_emissiveTexture: Int = 0
    protected var u_emissiveUVTransform: Int = 0
    protected var u_reflectionColor: Int = 0
    protected var u_reflectionTexture: Int = 0
    protected var u_reflectionUVTransform: Int = 0
    protected var u_normalTexture: Int = 0
    protected var u_normalUVTransform: Int = 0
    protected var u_ambientTexture: Int = 0
    protected var u_ambientUVTransform: Int = 0
    protected var u_alphaTest: Int = 0

    // Lighting uniforms
    protected var u_ambientCubemap: Int = -1
    protected var u_environmentCubemap: Int = -1
    protected val u_dirLights0color: Int = register(Uniform("u_dirLights[0].color"))
    protected val u_dirLights0direction: Int = register(Uniform("u_dirLights[0].direction"))
    protected val u_dirLights1color: Int = register(Uniform("u_dirLights[1].color"))
    protected val u_pointLights0color: Int = register(Uniform("u_pointLights[0].color"))
    protected val u_pointLights0position: Int = register(Uniform("u_pointLights[0].position"))
    protected val u_pointLights0intensity: Int = register(Uniform("u_pointLights[0].intensity"))
    protected val u_pointLights1color: Int = register(Uniform("u_pointLights[1].color"))
    protected val u_spotLights0color: Int = register(Uniform("u_spotLights[0].color"))
    protected val u_spotLights0position: Int = register(Uniform("u_spotLights[0].position"))
    protected val u_spotLights0intensity: Int = register(Uniform("u_spotLights[0].intensity"))
    protected val u_spotLights0direction: Int = register(Uniform("u_spotLights[0].direction"))
    protected val u_spotLights0cutoffAngle: Int = register(Uniform("u_spotLights[0].cutoffAngle"))
    protected val u_spotLights0exponent: Int = register(Uniform("u_spotLights[0].exponent"))
    protected val u_spotLights1color: Int = register(Uniform("u_spotLights[1].color"))
    protected val u_fogColor: Int = register(Uniform("u_fogColor"))
    protected val u_shadowMapProjViewTrans: Int = register(Uniform("u_shadowMapProjViewTrans"))
    protected val u_shadowTexture: Int = register(Uniform("u_shadowTexture"))
    protected val u_shadowPCFOffset: Int = register(Uniform("u_shadowPCFOffset"))

    // TODO Cache vertex attribute locations...
    protected var dirLightsLoc: Int = 0
    protected var dirLightsColorOffset: Int = 0
    protected var dirLightsDirectionOffset: Int = 0
    protected var dirLightsSize: Int = 0
    protected var pointLightsLoc: Int = 0
    protected var pointLightsColorOffset: Int = 0
    protected var pointLightsPositionOffset: Int = 0
    protected var pointLightsIntensityOffset: Int = 0
    protected var pointLightsSize: Int = 0
    protected var spotLightsLoc: Int = 0
    protected var spotLightsColorOffset: Int = 0
    protected var spotLightsPositionOffset: Int = 0
    protected var spotLightsDirectionOffset: Int = 0
    protected var spotLightsIntensityOffset: Int = 0
    protected var spotLightsCutoffAngleOffset: Int = 0
    protected var spotLightsExponentOffset: Int = 0
    protected var spotLightsSize: Int = 0

    protected val lighting: Boolean
    protected val environmentCubemap: Boolean
    protected val shadowMap: Boolean

    protected val config: ShaderConfig

    protected val directionalLights: kotlin.Array<DirectionalLight>
    protected val pointLights: kotlin.Array<PointLight>
    protected val spotLights: kotlin.Array<SpotLight>

    /**
     * The attributes that this shader supports
     */
    protected val attributesMask: Long
    private val vertexMask: Long

    private var time = 0f
    private var lightsSet = false

    /**
     * @param requestedConfig the config of the shader. If the renderable has more bones than
     * [ShaderConfig.getNumBones] the shader gets room for all of them.
     */
    init {
        val config: ShaderConfig = fitBones(requestedConfig, renderable)
        this.config = config
        this.vertexShader = if (config.vertexShader != null)
            config.vertexShader!!
        else
            ShaderSources.read(ShaderSources.DEFAULT_VERTEX)
        this.fragmentShader = if (config.fragmentShader != null)
            config.fragmentShader!!
        else
            ShaderSources.read(ShaderSources.DEFAULT_FRAGMENT)
        this.lighting = renderable.environment != null

        val attributes = combineAttributes(renderable)
        this.environmentCubemap = attributes.has(CubemapAttribute.EnvironmentMap) ||
                (lighting && attributes.has(CubemapAttribute.EnvironmentMap))
        this.shadowMap = lighting && renderable.environment!!.shadowMap != null

        attributesMask = attributes.getMask() or optionalAttributes
        vertexMask = renderable.meshPart.mesh!!.vertexAttributes.getMaskWithSizePacked()

        directionalLights = createAndInit(config.numDirectionalLights) { DirectionalLight() }

        pointLights = createAndInit(config.numPointLights) { PointLight() }
        spotLights = createAndInit(config.numSpotLights) { SpotLight() }

        if (!config.ignoreUnimplemented && (implementedFlags and attributesMask) != attributesMask) {
            throw GdxRuntimeException("Some attributes not implemented yet (" + attributesMask + ")")
        }

        initGlobalUniforms()
        initObjectUniforms(renderable)
        initMaterialUniforms()
        initLightUniforms()
    }

    private inline fun <reified T> createAndInit(length: Int, create: () -> T): kotlin.Array<T> =
        kotlin.Array(if (!lighting && length < 1) 0 else length) { create() }

    private fun initGlobalUniforms() {
        u_projTrans = registerUniformGlobal(
            "u_projTrans",
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    shader.camera!!.projection
                )
            })
        u_viewTrans = registerUniformGlobal(
            "u_viewTrans",
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    shader.camera!!.view
                )
            })
        u_projViewTrans = registerUniformGlobal(
            "u_projViewTrans",
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    shader.camera!!.combined
                )
            })
        u_cameraPosition = registerUniformGlobal(
            "u_cameraPosition",
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID, shader.camera!!.position.x,
                    shader.camera!!.position.y, shader.camera!!.position.z,
                    1.1881f / (shader.camera!!.far * shader.camera!!.far)
                )
            })
        u_cameraDirection = registerUniformGlobal(
            "u_cameraDirection",
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    shader.camera!!.direction
                )
            })
        u_cameraUp = registerUniformGlobal(
            "u_cameraUp",
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    shader.camera!!.up
                )
            })
        u_cameraNearFar = registerUniformGlobal(
            "u_cameraNearFar",
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID, shader.camera!!.near,
                    shader.camera!!.far
                )
            })
        u_time = register(Uniform("u_time"))
    }

    private fun initObjectUniforms(inputRenderable: Renderable) {
        u_worldTrans = registerUniformLocal(
            "u_worldTrans",
            SetterFunction { shader: BaseShader?, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    renderable!!.worldTransform
                )
            })

        u_viewWorldTrans = register(Uniform("u_viewWorldTrans"), object : LocalSetterWithAttr<Matrix4?>(Matrix4()) {
            override fun set(
                shader: BaseShader, inputID: Int, renderable: Renderable?,
                combinedAttributes: Attributes?
            ) {
                shader.set(inputID, attr!!.set(shader.camera!!.view).mul(renderable!!.worldTransform))
            }
        })
        u_projViewWorldTrans =
            register(Uniform("u_projViewWorldTrans"), object : LocalSetterWithAttr<Matrix4?>(Matrix4()) {
                override fun set(
                    shader: BaseShader, inputID: Int, renderable: Renderable?,
                    combinedAttributes: Attributes?
                ) {
                    shader.set(inputID, attr!!.set(shader.camera!!.combined).mul(renderable!!.worldTransform))
                }
            })
        u_normalMatrix = register(Uniform("u_normalMatrix"), object : LocalSetterWithAttr<Matrix3?>(Matrix3()) {
            override fun set(
                shader: BaseShader, inputID: Int, renderable: Renderable?,
                combinedAttributes: Attributes?
            ) {
                shader.set(inputID, attr!!.set(renderable!!.worldTransform).inv().transpose())
            }
        })
        if (inputRenderable.bones != null && config.numBones > 0) {
            u_bones = register(Uniform("u_bones"), object : LocalSetterWithAttr<Matrix4?>(Matrix4()) {
                private val bones = FloatArray(config.numBones * 16)

                override fun set(
                    shader: BaseShader, inputID: Int, renderable: Renderable?,
                    combinedAttributes: Attributes?
                ) {
                    var i = 0
                    while (i < bones.size) {
                        val idx = i / 16
                        if (renderable!!.bones == null || idx >= renderable.bones!!.size) {
                            System.arraycopy(attr!!.`val`, 0, bones, i, 16)
                        } else {
                            System.arraycopy(renderable.bones!![idx].`val`, 0, bones, i, 16)
                        }
                        i += 16
                    }
                    shader.program!!.setUniformMatrix4fv(shader.loc(inputID), bones, 0, bones.size)
                }
            })
        }
    }

    private fun initMaterialUniforms() {
        u_shininess = registerUniformLocal(
            "u_shininess", FloatAttribute.Shininess,
            SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                shader!!.set(
                    inputID,
                    ((combinedAttributes!!.get(FloatAttribute.Shininess)) as FloatAttribute).value
                )
            })
        u_opacity = register(Uniform("u_opacity", BlendingAttribute.Type))

        var materialPart = initMaterialPart("diffuse", ColorAttribute.Diffuse, TextureAttribute.Diffuse)
        u_diffuseColor = materialPart.colorUniform
        u_diffuseTexture = materialPart.textureUniform
        u_diffuseUVTransform = materialPart.uVTransformUniform
        materialPart = initMaterialPart("specular", ColorAttribute.Specular, TextureAttribute.Specular)
        u_specularColor = materialPart.colorUniform
        u_specularTexture = materialPart.textureUniform
        u_specularUVTransform = materialPart.uVTransformUniform
        materialPart = initMaterialPart("emissive", ColorAttribute.Emissive, TextureAttribute.Emissive)
        u_emissiveColor = materialPart.colorUniform
        u_emissiveTexture = materialPart.textureUniform
        u_emissiveUVTransform = materialPart.uVTransformUniform
        materialPart = initMaterialPart("reflection", ColorAttribute.Reflection, TextureAttribute.Reflection)
        u_reflectionColor = materialPart.colorUniform
        u_reflectionTexture = materialPart.textureUniform
        u_reflectionUVTransform = materialPart.uVTransformUniform

        materialPart = initMaterialPart("normal", false, -1, TextureAttribute.Normal)
        u_normalTexture = materialPart.textureUniform
        u_normalUVTransform = materialPart.uVTransformUniform

        materialPart = initMaterialPart("ambient", false, -1, TextureAttribute.Ambient)
        u_ambientTexture = materialPart.textureUniform
        u_ambientUVTransform = materialPart.uVTransformUniform

        u_alphaTest = register(Uniform("u_alphaTest"))
    }

    private fun initLightUniforms() {
        if (lighting) {
            u_ambientCubemap = register(
                Uniform("u_ambientCubemap"),
                ACubemapSetter(config.numDirectionalLights, config.numPointLights)
            )
        }
        if (environmentCubemap) {
            u_environmentCubemap = register(Uniform("u_environmentCubemap"), object : LocalSetter() {
                override fun set(
                    shader: BaseShader, inputID: Int, renderable: Renderable?,
                    combinedAttributes: Attributes?
                ) {
                    if (combinedAttributes!!.has(CubemapAttribute.EnvironmentMap)) {
                        shader.set(
                            inputID, shader.context!!.textureBinder
                                .bind(
                                    (combinedAttributes.get(
                                        CubemapAttribute.EnvironmentMap
                                    ) as CubemapAttribute).textureDescription
                                )
                        )
                    }
                }
            })
        }
    }

    private fun initMaterialPart(
        prefix: String?,
        colorAttribute: Long,
        textureAttribute: Long
    ): MaterialPartUniformHolder {
        return initMaterialPart(prefix, true, colorAttribute, textureAttribute)
    }

    private fun initMaterialPart(
        prefix: String?, colorNeeded: Boolean, colorAttribute: Long,
        textureAttribute: Long
    ): MaterialPartUniformHolder {
        var colorAttr = 0
        if (colorNeeded) {
            colorAttr = registerUniformLocal(
                String.format("u_%sColor", prefix), colorAttribute,
                SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                    shader!!.set(
                        inputID,
                        ((combinedAttributes!!.get(colorAttribute)) as ColorAttribute).color
                    )
                })
        }
        return MaterialPartUniformHolder(
            colorAttr,
            registerUniformLocal(
                String.format("u_%sTexture", prefix), textureAttribute,
                SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                    shader!!.set(
                        inputID,
                        shader.context!!.textureBinder
                            .bind(
                                ((combinedAttributes!!.get(
                                    textureAttribute
                                )) as TextureAttribute).textureDescription
                            )
                    )
                }),
            registerUniformLocal(
                String.format("u_%sUVTransform", prefix), textureAttribute,
                SetterFunction { shader: BaseShader?, inputID: Int, renderable1: Renderable?, combinedAttributes: Attributes? ->
                    val ta = combinedAttributes!!.get(textureAttribute) as TextureAttribute
                    shader!!.set(inputID, ta.offsetU, ta.offsetV, ta.scaleU, ta.scaleV)
                })
        )
    }

    protected fun compile() {
        program = ShaderProgram(vertexShader, fragmentShader)
        if (!program!!.isCompiled()) {
            throw GdxRuntimeException(program!!.getLog())
        }
    }

    override fun init(renderable: Renderable?) {
        check(!initialized) { "Try to call init for initialized shader" }
        initialized = true

        preprocessShaderContents(renderable!!)
        compile()
        init(program!!, renderable)

        dirLightsLoc = loc(u_dirLights0color)
        dirLightsColorOffset = loc(u_dirLights0color) - dirLightsLoc
        dirLightsDirectionOffset = loc(u_dirLights0direction) - dirLightsLoc
        dirLightsSize = loc(u_dirLights1color) - dirLightsLoc
        if (dirLightsSize < 0) {
            dirLightsSize = 0
        }

        pointLightsLoc = loc(u_pointLights0color)
        pointLightsColorOffset = loc(u_pointLights0color) - pointLightsLoc
        pointLightsPositionOffset = loc(u_pointLights0position) - pointLightsLoc
        pointLightsIntensityOffset =
            if (has(u_pointLights0intensity)) loc(u_pointLights0intensity) - pointLightsLoc else -1
        pointLightsSize = loc(u_pointLights1color) - pointLightsLoc
        if (pointLightsSize < 0) {
            pointLightsSize = 0
        }

        spotLightsLoc = loc(u_spotLights0color)
        spotLightsColorOffset = loc(u_spotLights0color) - spotLightsLoc
        spotLightsPositionOffset = loc(u_spotLights0position) - spotLightsLoc
        spotLightsDirectionOffset = loc(u_spotLights0direction) - spotLightsLoc
        spotLightsIntensityOffset = if (has(u_spotLights0intensity)) loc(u_spotLights0intensity) - spotLightsLoc else -1
        spotLightsCutoffAngleOffset = loc(u_spotLights0cutoffAngle) - spotLightsLoc
        spotLightsExponentOffset = loc(u_spotLights0exponent) - spotLightsLoc
        spotLightsSize = loc(u_spotLights1color) - spotLightsLoc
        if (spotLightsSize < 0) {
            spotLightsSize = 0
        }
    }

    override fun begin(camera: Camera?, context: RenderContext?) {
        super.begin(camera, context)

        for (dirLight in directionalLights) {
            dirLight.set(0f, 0f, 0f, 0f, -1f, 0f)
        }
        for (pointLight in pointLights) {
            pointLight.set(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        for (spotLight in spotLights) {
            spotLight.set(0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 1f, 0f)
        }
        lightsSet = false

        if (has(u_time)) {
            set(u_time, Gdx.graphics.getDeltaTime().let { time += it; time })
        }
    }

    override fun render(renderable: Renderable, combinedAttributes: Attributes?) {
        if (!combinedAttributes!!.has(BlendingAttribute.Type)) {
            context!!.setBlending(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }
        bindMaterial(combinedAttributes)
        if (lighting) {
            bindLights(renderable, combinedAttributes)
        }
        super.render(renderable, combinedAttributes)
    }

    override fun canRender(instance: Renderable?): Boolean {
        val renderable = instance!!
        return canRenderNonNull(renderable)
    }

    private fun canRenderNonNull(renderable: Renderable): Boolean {
        if (renderable.bones != null && renderable.bones!!.size > config.numBones) {
            return false
        }

        val renderableMask = combineAttributeMasks(renderable)
        return (attributesMask == (renderableMask or optionalAttributes))
                && (vertexMask == renderable.meshPart.mesh!!.vertexAttributes.getMaskWithSizePacked())
                && (renderable.environment != null) == lighting
    }

    private fun combineAttributeMasks(renderable: Renderable): Long {
        var mask: Long = 0
        if (renderable.environment != null) {
            mask = mask or renderable.environment!!.getMask()
        }
        if (renderable.material != null) {
            mask = mask or renderable.material!!.getMask()
        }
        return mask
    }


    protected fun bindMaterial(attributes: Attributes) {
        var cullFace = config.defaultCullFace
        var depthFunc = config.defaultDepthFunc
        var depthRangeNear = 0f
        var depthRangeFar = 1f
        var depthMask = true

        for (attr in attributes) {
            val t = attr.type
            if (BlendingAttribute.`is`(t)) {
                context!!.setBlending(
                    true, (attr as BlendingAttribute).sourceFunction,
                    attr.destFunction
                )
                set(u_opacity, attr.opacity)
            } else if ((t and IntAttribute.CullFace) == IntAttribute.CullFace) {
                cullFace = (attr as IntAttribute).value
            } else if ((t and FloatAttribute.AlphaTest) == FloatAttribute.AlphaTest) {
                set(u_alphaTest, (attr as FloatAttribute).value)
            } else if ((t and DepthTestAttribute.Type) == DepthTestAttribute.Type) {
                val dta = attr as DepthTestAttribute
                depthFunc = dta.depthFunc
                depthRangeNear = dta.depthRangeNear
                depthRangeFar = dta.depthRangeFar
                depthMask = dta.depthMask
            } else if (!config.ignoreUnimplemented) {
                throw GdxRuntimeException("Unknown material attribute: " + attr)
            }
        }

        context!!.setCullFace(cullFace)
        context!!.setDepthTest(depthFunc, depthRangeNear, depthRangeFar)
        context!!.setDepthMask(depthMask)
    }

    protected fun bindLights(renderable: Renderable, attributes: Attributes) {
        val lights = renderable.environment
        val dla =
            attributes.get<DirectionalLightsAttribute?>(
                DirectionalLightsAttribute::class.java,
                DirectionalLightsAttribute.Type
            )
        val dirs = if (dla == null) null else dla.lights
        val pla = attributes.get<PointLightsAttribute?>(PointLightsAttribute::class.java, PointLightsAttribute.Type)
        val points = if (pla == null) null else pla.lights
        val sla = attributes.get<SpotLightsAttribute?>(SpotLightsAttribute::class.java, SpotLightsAttribute.Type)
        val spots = if (sla == null) null else sla.lights

        if (dirLightsLoc >= 0) {
            processDirectionalLightLocations(dirs)
        }

        if (pointLightsLoc >= 0) {
            processPointLightLocations(points)
        }

        if (spotLightsLoc >= 0) {
            processSpotLightLocations(spots)
        }

        if (attributes.has(ColorAttribute.Fog)) {
            set(u_fogColor, (attributes.get(ColorAttribute.Fog) as ColorAttribute).color)
        }

        if (lights != null && lights.shadowMap != null) {
            set(u_shadowMapProjViewTrans, lights.shadowMap.getProjViewTrans())
            set(u_shadowTexture, lights.shadowMap.getDepthMap())
            set(u_shadowPCFOffset, 1f / (2f * lights.shadowMap.getDepthMap().texture.getWidth()))
        }

        lightsSet = true
    }

    private fun processSpotLightLocations(spots: Array<SpotLight>?) {
        for (i in spotLights.indices) {
            if (spots == null || i >= spots.size) {
                if (lightsSet && spotLights[i].intensity == 0f) {
                    continue
                }
                spotLights[i].intensity = 0f
            } else if (lightsSet && spotLights[i].equals(spots.get(i))) {
                continue
            } else {
                spotLights[i].set(spots.get(i))
            }

            val idx = spotLightsLoc + i * spotLightsSize
            program!!.setUniformf(
                idx + spotLightsColorOffset, spotLights[i].color.r * spotLights[i].intensity,
                spotLights[i].color.g * spotLights[i].intensity,
                spotLights[i].color.b * spotLights[i].intensity
            )
            program!!.setUniformf(idx + spotLightsPositionOffset, spotLights[i].position)
            program!!.setUniformf(idx + spotLightsDirectionOffset, spotLights[i].direction)
            program!!.setUniformf(idx + spotLightsCutoffAngleOffset, spotLights[i].cutoffAngle)
            program!!.setUniformf(idx + spotLightsExponentOffset, spotLights[i].exponent)
            if (spotLightsIntensityOffset >= 0) {
                program!!.setUniformf(idx + spotLightsIntensityOffset, spotLights[i].intensity)
            }
            if (spotLightsSize <= 0) {
                break
            }
        }
    }

    private fun processPointLightLocations(points: Array<PointLight>?) {
        for (i in pointLights.indices) {
            if (points == null || i >= points.size) {
                if (lightsSet && pointLights[i].intensity == 0f) {
                    continue
                }
                pointLights[i].intensity = 0f
            } else if (lightsSet && pointLights[i].equals(points.get(i))) {
                continue
            } else {
                pointLights[i].set(points.get(i))
            }

            val idx = pointLightsLoc + i * pointLightsSize
            program!!.setUniformf(
                idx + pointLightsColorOffset, pointLights[i].color.r * pointLights[i].intensity,
                pointLights[i].color.g * pointLights[i].intensity,
                pointLights[i].color.b * pointLights[i].intensity
            )
            program!!.setUniformf(
                idx + pointLightsPositionOffset, pointLights[i].position.x,
                pointLights[i].position.y,
                pointLights[i].position.z
            )
            if (pointLightsIntensityOffset >= 0) {
                program!!.setUniformf(idx + pointLightsIntensityOffset, pointLights[i].intensity)
            }
            if (pointLightsSize <= 0) {
                break
            }
        }
    }

    private fun processDirectionalLightLocations(dirs: Array<DirectionalLight>?) {
        for (i in directionalLights.indices) {
            if (dirs == null || i >= dirs.size) {
                if (lightsSet && directionalLights[i].color.r == 0f && directionalLights[i].color.g == 0f && directionalLights[i].color.b == 0f) {
                    continue
                }
                directionalLights[i].color.set(0f, 0f, 0f, 1f)
            } else if (lightsSet && directionalLights[i].equals(dirs.get(i))) {
                continue
            } else {
                directionalLights[i].set(dirs.get(i))
            }

            val idx = dirLightsLoc + i * dirLightsSize
            program!!.setUniformf(
                idx + dirLightsColorOffset, directionalLights[i].color.r,
                directionalLights[i].color.g,
                directionalLights[i].color.b
            )
            program!!.setUniformf(
                idx + dirLightsDirectionOffset, directionalLights[i].direction.x,
                directionalLights[i].direction.y, directionalLights[i].direction.z
            )
            if (dirLightsSize <= 0) {
                break
            }
        }
    }


    /**
     * Method can preprocess shader contents before shader compile.
     *
     * @param renderable for getting attributes
     */
    protected open fun preprocessShaderContents(renderable: Renderable) {
        val attributes = combineAttributes(renderable)
        val sb = StringBuilder()
        val attributesMask = attributes.getMask()
        val vertexMask = renderable.meshPart.mesh!!.vertexAttributes.getMask()
        processVertexAttributes(renderable, config, vertexMask, sb, attributes)
        if ((attributesMask and BlendingAttribute.Type) == BlendingAttribute.Type) {
            sb.append("#define " + BlendingAttribute.Alias + "Flag\n")
        }
        processTextureAttributes(attributesMask, sb)
        processColorAttributes(attributesMask, sb)
        if (renderable.bones != null && config.numBones > 0) {
            sb.append("#define numBones ").append(config.numBones).append("\n")
        }

        log.debug("Shader prefix: \n{}", sb)

        vertexShader = sb.toString() + vertexShader
        fragmentShader = sb.toString() + fragmentShader
    }

    private fun processVertexAttributes(
        renderable: Renderable, config: ShaderConfig, vertexMask: Long,
        prefix: StringBuilder, attributes: Attributes
    ) {
        if (and(vertexMask, VertexAttributes.Usage.Position.toLong())) {
            prefix.append("#define positionFlag\n")
        }
        if (or(vertexMask, (VertexAttributes.Usage.ColorUnpacked or VertexAttributes.Usage.ColorPacked).toLong())) {
            prefix.append("#define colorFlag\n")
        }
        if (and(vertexMask, VertexAttributes.Usage.BiNormal.toLong())) {
            prefix.append("#define binormalFlag\n")
        }
        if (and(vertexMask, VertexAttributes.Usage.Tangent.toLong())) {
            prefix.append("#define tangentFlag\n")
        }
        if (and(vertexMask, VertexAttributes.Usage.Normal.toLong())) {
            prefix.append("#define normalFlag\n")
        }
        if (and(vertexMask, VertexAttributes.Usage.Normal.toLong()) ||
            and(vertexMask, (VertexAttributes.Usage.Tangent or VertexAttributes.Usage.BiNormal).toLong())
        ) {
            if (renderable.environment != null) {
                prefix.append("#define lightingFlag\n")
                prefix.append("#define ambientCubemapFlag\n")
                prefix.append("#define numDirectionalLights ").append(config.numDirectionalLights).append("\n")
                prefix.append("#define numPointLights ").append(config.numPointLights).append("\n")
                prefix.append("#define numSpotLights ").append(config.numSpotLights).append("\n")
                if (attributes.has(ColorAttribute.Fog)) {
                    prefix.append("#define fogFlag\n")
                }
                if (renderable.environment!!.shadowMap != null) {
                    prefix.append("#define shadowMapFlag\n")
                }
                if (attributes.has(CubemapAttribute.EnvironmentMap)) {
                    prefix.append("#define environmentCubemapFlag\n")
                }
            }
        }
        val n = renderable.meshPart.mesh!!.vertexAttributes.size()
        for (i in 0..<n) {
            val attr = renderable.meshPart.mesh!!.vertexAttributes.get(i)
            if (attr.usage == VertexAttributes.Usage.BoneWeight) {
                prefix.append("#define boneWeight").append(attr.unit).append("Flag\n")
            } else if (attr.usage == VertexAttributes.Usage.TextureCoordinates) {
                prefix.append("#define texCoord").append(attr.unit).append("Flag\n")
            }
        }
    }

    // TODO: Perhaps move responsibility for combining attributes to RenderableProvider?
    private fun combineAttributes(renderable: Renderable): Attributes {
        tmpAttributes.clear()
        if (renderable.environment != null) {
            tmpAttributes.set(renderable.environment)
        }
        if (renderable.material != null) {
            tmpAttributes.set(renderable.material)
        }
        return tmpAttributes
    }

    private fun and(mask: Long, flag: Long): Boolean {
        return (mask and flag) == flag
    }

    private fun or(mask: Long, flag: Long): Boolean {
        return (mask and flag) != 0L
    }

    override fun dispose() {
        if (program != null) {
            program!!.dispose()
        }
        super.dispose()
    }

    fun registerUniformGlobal(alias: String?, func: SetterFunction): Int {
        return registerUniformGlobal(alias, 0, func)
    }

    fun registerUniformGlobal(alias: String?, overallMask: Long, func: SetterFunction): Int {
        return register(
            Uniform(alias!!, overallMask),
            object : BaseShader.Setter {
                override fun set(shader: BaseShader, inputID: Int, renderable: Renderable?, combinedAttributes: Attributes?) {
                    func.set(shader, inputID, renderable, combinedAttributes)
                }
            })
    }

    fun registerUniformLocal(alias: String?, func: SetterFunction): Int {
        return registerUniformLocal(alias, 0, func)
    }

    fun registerUniformLocal(alias: String?, overallMask: Long, func: SetterFunction): Int {
        return register(Uniform(alias!!, overallMask), object : LocalSetter() {
            override fun set(
                shader: BaseShader, inputID: Int, renderable: Renderable?,
                combinedAttributes: Attributes?
            ) {
                func.set(shader, inputID, renderable, combinedAttributes)
            }
        })
    }

    private abstract class LocalSetterWithAttr<T>(protected val attr: T?) : Setter {
        override fun isGlobal(shader: BaseShader, inputID: Int): Boolean {
            return false
        }
    }

    private class MaterialPartUniformHolder(
        val colorUniform: Int,
        val textureUniform: Int,
        val uVTransformUniform: Int
    )

    internal class ACubemapSetter(private val dirLightsOffset: Int, private val pointLightsOffset: Int) : LocalSetter() {
        private val cacheAmbientCubemap = AmbientCubemap()


        override fun set(
            shader: BaseShader, inputID: Int, renderable: Renderable?,
            combinedAttributes: Attributes?
        ) {
            if (renderable!!.environment == null) {
                shader.program!!.setUniform3fv(shader.loc(inputID), ones, 0, ones.size)
                return
            }

            renderable.worldTransform.getTranslation(tmpV1)
            setAmbientBase(cacheAmbientCubemap, combinedAttributes!!)

            if (combinedAttributes.has(DirectionalLightsAttribute.Type)) {
                val lights = (combinedAttributes
                    .get(DirectionalLightsAttribute.Type) as DirectionalLightsAttribute).lights
                for (i in dirLightsOffset..<lights.size) {
                    cacheAmbientCubemap.add(lights.get(i)!!.color, lights.get(i)!!.direction)
                }
            }

            if (combinedAttributes.has(PointLightsAttribute.Type)) {
                val lights =
                    (combinedAttributes.get(PointLightsAttribute.Type) as PointLightsAttribute).lights
                for (i in pointLightsOffset..<lights.size) {
                    cacheAmbientCubemap.add(
                        lights.get(i)!!.color, lights.get(i)!!.position, tmpV1,
                        lights.get(i)!!.intensity
                    )
                }
            }

            cacheAmbientCubemap.clamp()
            shader.program!!.setUniform3fv(
                shader.loc(inputID), cacheAmbientCubemap.data, 0,
                cacheAmbientCubemap.data.size
            )
        }

        companion object {
            /**
             * Starts [cubemap] from the sky's six irradiance colors when [attributes] has an [EnvironmentLightAttribute]
             * (it replaces the ambient color), else from the ambient color when there is one; otherwise leaves it as it
             * is. Lights beyond the shader's count are added after this.
             */
            fun setAmbientBase(cubemap: AmbientCubemap, attributes: Attributes) {
                val sky = attributes.get(EnvironmentLightAttribute.Type) as EnvironmentLightAttribute?
                if (sky != null) {
                    cubemap.set(sky.ambient)
                } else if (attributes.has(ColorAttribute.AmbientLight)) {
                    cubemap.set((attributes.get(ColorAttribute.AmbientLight) as ColorAttribute).color)
                }
            }

            private val ones: FloatArray =
                floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
            private val tmpV1 = Vector3()
        }
    }

    companion object {
        private val optionalAttributes = IntAttribute.CullFace or DepthTestAttribute.Type
        protected var implementedFlags: Long = (BlendingAttribute.Type or FloatAttribute.AlphaTest
                or FloatAttribute.Shininess or ColorAttribute.Diffuse or ColorAttribute.Specular or ColorAttribute.Emissive
                or TextureAttribute.Diffuse or TextureAttribute.Specular or TextureAttribute.Emissive
                or TextureAttribute.Normal)

        private const val BONES_STEP = 8

        /**
         * The bone array of the shader has a fixed size. Skinned models often have more bones than the default, so the
         * size is raised to the number of bones of the renderable (in steps, so similar models share a shader).
         */
        private fun fitBones(config: ShaderConfig, renderable: Renderable): ShaderConfig {
            if (renderable.bones == null || renderable.bones!!.size <= config.numBones) {
                return config
            }
            val fitted = config.copy()
            fitted.numBones = (renderable.bones!!.size + BONES_STEP - 1) / BONES_STEP * BONES_STEP
            return fitted
        }

        private fun processTextureAttributes(attributesMask: Long, prefix: StringBuilder) {
            if ((attributesMask and TextureAttribute.Diffuse) == TextureAttribute.Diffuse) {
                // TODO implement UV mapping
                prefix.append("#define " + TextureAttribute.DiffuseAlias + "Flag\n")
                prefix.append("#define " + TextureAttribute.DiffuseAlias + "Coord texCoord0\n")
            }
            if ((attributesMask and TextureAttribute.Specular) == TextureAttribute.Specular) {
                // TODO implement UV mapping
                prefix.append("#define " + TextureAttribute.SpecularAlias + "Flag\n")
                prefix.append("#define " + TextureAttribute.SpecularAlias + "Coord texCoord0\n")
            }
            if ((attributesMask and TextureAttribute.Normal) == TextureAttribute.Normal) {
                // TODO implement UV mapping
                prefix.append("#define " + TextureAttribute.NormalAlias + "Flag\n")
                prefix.append("#define " + TextureAttribute.NormalAlias + "Coord texCoord0\n")
            }
            if ((attributesMask and TextureAttribute.Emissive) == TextureAttribute.Emissive) {
                // TODO implement UV mapping
                prefix.append("#define " + TextureAttribute.EmissiveAlias + "Flag\n")
                prefix.append("#define " + TextureAttribute.EmissiveAlias + "Coord texCoord0\n")
            }
            if ((attributesMask and TextureAttribute.Reflection) == TextureAttribute.Reflection) {
                // TODO implement UV mapping
                prefix.append("#define " + TextureAttribute.ReflectionAlias + "Flag\n")
                prefix.append("#define " + TextureAttribute.ReflectionAlias + "Coord texCoord0\n")
            }
            if ((attributesMask and TextureAttribute.Ambient) == TextureAttribute.Ambient) {
                // TODO implement UV mapping
                prefix.append("#define " + TextureAttribute.AmbientAlias + "Flag\n")
                prefix.append("#define " + TextureAttribute.AmbientAlias + "Coord texCoord0\n")
            }
        }

        private fun processColorAttributes(attributesMask: Long, prefix: StringBuilder) {
            if ((attributesMask and ColorAttribute.Diffuse) == ColorAttribute.Diffuse) {
                prefix.append("#define " + ColorAttribute.DiffuseAlias + "Flag\n")
            }
            if ((attributesMask and ColorAttribute.Specular) == ColorAttribute.Specular) {
                prefix.append("#define " + ColorAttribute.SpecularAlias + "Flag\n")
            }
            if ((attributesMask and ColorAttribute.Emissive) == ColorAttribute.Emissive) {
                prefix.append("#define " + ColorAttribute.EmissiveAlias + "Flag\n")
            }
            if ((attributesMask and ColorAttribute.Reflection) == ColorAttribute.Reflection) {
                prefix.append("#define " + ColorAttribute.ReflectionAlias + "Flag\n")
            }
            if ((attributesMask and FloatAttribute.Shininess) == FloatAttribute.Shininess) {
                prefix.append("#define " + FloatAttribute.ShininessAlias + "Flag\n")
            }
            if ((attributesMask and FloatAttribute.AlphaTest) == FloatAttribute.AlphaTest) {
                prefix.append("#define " + FloatAttribute.AlphaTestAlias + "Flag\n")
            }
        }
    }
}
