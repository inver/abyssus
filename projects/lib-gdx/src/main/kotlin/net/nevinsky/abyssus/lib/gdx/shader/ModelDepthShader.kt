/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import net.nevinsky.abyssus.lib.gdx.Renderable

/** Parameters shared by the depth shader for a single tile/pass. */
class ShadowDepthPass {
    val viewProjection = Matrix4()
    val lightPosition = Vector3()
    var radialDepth: Boolean = false
    var far: Float = 1f
}

/** Depth shader for the library's custom mesh/index implementation, including posed vertices and alpha cutouts. */
class ModelDepthShader(private val pass: ShadowDepthPass, renderable: Renderable) : net.nevinsky.abyssus.lib.gdx.shader.Shader {
    private val meshAttributes = renderable.meshPart.mesh!!.vertexAttributes
    private val boneSlots = (0..7).filter { slot -> (0 until meshAttributes.size()).any { meshAttributes.get(it).alias == "a_boneWeight$slot" } }
    private val boneCount = (renderable.bones?.size ?: 12).coerceAtLeast(1)
    private val boneMatrices = FloatArray(boneCount * 16)
    private val program = ShaderProgram(vertexSource(), FRAGMENT)
    private var context: RenderContext? = null

    init { check(program.isCompiled) { program.log } }

    override fun init(renderable: Renderable?) = Unit
    override fun begin(camera: Camera?, context: RenderContext?) {
        this.context = context
        if (camera != null) pass.viewProjection.set(camera.combined)
        program.bind()
    }
    override fun canRender(instance: Renderable?): Boolean = instance?.meshPart?.mesh?.vertexAttributes?.getMaskWithSizePacked() == meshAttributes.getMaskWithSizePacked() &&
        (instance.bones?.size ?: 12).coerceAtLeast(1) == boneCount
    override fun render(renderable: Renderable) {
        if (!castsShadow(renderable) || renderable.worldTransform.det3x3() == 0f) return
        val mesh = renderable.meshPart.mesh ?: return
        context!!.setCullFace((renderable.material?.get(IntAttribute.CullFace) as? IntAttribute)?.value ?: GL20.GL_BACK)
        context!!.setDepthTest(GL20.GL_LEQUAL)
        context!!.setDepthMask(true)
        context!!.setBlending(false, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        program.bind()
        program.setUniformMatrix("u_projViewTrans", pass.viewProjection)
        program.setUniformMatrix("u_worldTrans", renderable.worldTransform)
        program.setUniformi("u_radialDepth", if (pass.radialDepth) 1 else 0)
        program.setUniformf("u_lightPosition", pass.lightPosition)
        program.setUniformf("u_far", pass.far)
        val bones = renderable.bones
        val matrices = boneMatrices
        for (i in 0 until boneCount) {
            val matrix = bones?.getOrNull(i) ?: IDENTITY
            matrix.`val`.copyInto(matrices, i * 16)
        }
        if (boneSlots.isNotEmpty()) program.setUniformMatrix4fv("u_bones[0]", matrices, 0, matrices.size)
        val diffuse = renderable.material?.get(TextureAttribute.Diffuse) as? TextureAttribute
        val texture = diffuse?.textureDescription?.texture
        program.setUniformi("u_diffuseTexture", if (diffuse == null) 0 else context!!.textureBinder.bind(diffuse.textureDescription))
        if ((0 until mesh.vertexAttributes.size()).any { mesh.vertexAttributes.get(it).alias == "a_texCoord0" }) {
            program.setUniformf("u_uvTransform", diffuse?.offsetU ?: 0f, diffuse?.offsetV ?: 0f, diffuse?.scaleU ?: 1f, diffuse?.scaleV ?: 1f)
        }
        val alpha = (renderable.material?.get(FloatAttribute.AlphaTest) as? FloatAttribute)?.value ?: 0f
        program.setUniformf("u_alphaTest", alpha)
        program.setUniformf("u_diffuseAlpha", (renderable.material?.get(ColorAttribute.Diffuse) as? ColorAttribute)?.color?.a ?: 1f)
        program.setUniformi("u_hasDiffuse", if (texture != null) 1 else 0)
        val locations = IntArray(mesh.vertexAttributes.size()) { program.getAttributeLocation(mesh.vertexAttributes[it].alias) }
        mesh.bind(program, locations)
        try { renderable.meshPart.render(program, false) } finally { mesh.unbind(program, locations) }
    }
    override fun end() { context = null }
    override fun dispose() { program.dispose() }

    private fun vertexSource(): String = buildString {
        val hasUv = (0 until meshAttributes.size()).any { meshAttributes.get(it).alias == "a_texCoord0" }
        val color = meshAttributes.findByUsage(VertexAttributes.Usage.ColorUnpacked) ?: meshAttributes.findByUsage(VertexAttributes.Usage.ColorPacked)
        append("attribute vec3 a_position; uniform mat4 u_projViewTrans; uniform mat4 u_worldTrans; varying vec3 v_worldPos; varying vec2 v_uv; varying float v_alpha;\n")
        if (color != null) append("attribute vec4 ${color.alias};\n")
        if (hasUv) append("attribute vec2 a_texCoord0; uniform vec4 u_uvTransform;\n")
        boneSlots.forEach { append("attribute vec2 a_boneWeight$it;\n") }
        if (boneSlots.isNotEmpty()) {
            append("uniform mat4 u_bones[$boneCount];\nvoid main(){ mat4 skin=mat4(0.0); float total=0.0;\n")
            boneSlots.forEach { slot -> append("if(a_boneWeight$slot.y>0.0){skin+=a_boneWeight$slot.y*u_bones[int(a_boneWeight$slot.x)];total+=a_boneWeight$slot.y;}\n") }
            append("if(total==0.0)skin=mat4(1.0); vec4 world=u_worldTrans*skin*vec4(a_position,1.0);\n")
        } else append("void main(){vec4 world=u_worldTrans*vec4(a_position,1.0);\n")
        append("v_worldPos=world.xyz;\n")
        append(if (color != null) "v_alpha=${color.alias}.a;\n" else "v_alpha=1.0;\n")
        if (hasUv) append("v_uv=a_texCoord0*u_uvTransform.zw+u_uvTransform.xy;\n") else append("v_uv=vec2(0.0);\n")
        append("gl_Position=u_projViewTrans*world;}\n")
    }

    companion object {
        private val IDENTITY = Matrix4()
        fun castsShadow(renderable: Renderable): Boolean =
            (renderable.material?.get(BlendingAttribute.Type) as? BlendingAttribute)?.blended != true
        internal const val FRAGMENT = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform sampler2D u_diffuseTexture;
            uniform int u_hasDiffuse;
            uniform float u_alphaTest;
            uniform float u_diffuseAlpha;
            uniform vec4 u_uvTransform;
            uniform int u_radialDepth;
            uniform vec3 u_lightPosition;
            uniform float u_far;
            varying vec2 v_uv;
            varying vec3 v_worldPos;
            varying float v_alpha;
            vec4 packDepth(float depth){ vec4 value=fract(min(depth,0.99999994)*vec4(16581375.0,65025.0,255.0,1.0)); value-=value.xxyz*vec4(0.0,1.0/255.0,1.0/255.0,1.0/255.0); return value; }
            void main(){ float alpha=u_diffuseAlpha*v_alpha; if(u_hasDiffuse==1) alpha*=texture2D(u_diffuseTexture,v_uv).a; if(alpha<u_alphaTest) discard; float depth=gl_FragCoord.z; if(u_radialDepth==1) depth=length(v_worldPos-u_lightPosition)/u_far; gl_FragColor=packDepth(clamp(depth,0.0,1.0)); }
        """
    }
}

/** Caches depth shaders by vertex layout and poses; blended materials are intentionally skipped by [get]. */
class ModelDepthShaderProvider(private val pass: ShadowDepthPass) : Disposable {
    private val shaders = mutableListOf<ModelDepthShader>()
    fun get(renderable: Renderable): ModelDepthShader? {
        return shaders.firstOrNull { it.canRender(renderable) } ?: ModelDepthShader(pass, renderable).also(shaders::add)
    }
    override fun dispose() { shaders.forEach(ModelDepthShader::dispose); shaders.clear() }
}
