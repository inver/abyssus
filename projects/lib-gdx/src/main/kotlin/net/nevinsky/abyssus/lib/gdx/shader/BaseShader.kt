/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.shader

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GLTexture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Attributes
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.GdxRuntimeException
import com.badlogic.gdx.utils.IntIntMap
import net.nevinsky.abyssus.lib.gdx.Renderable
import net.nevinsky.abyssus.lib.gdx.mesh.Mesh

/**
 * @author Xoppa
 *
 *
 * A BaseShader is a wrapper around a ShaderProgram that keeps track of the uniform and attribute
 * locations. It does not manage the ShaderPogram, you are still responsible for disposing the ShaderProgram.
 */
abstract class BaseShader : Shader {
    interface Validator {
        /**
         * @return True if the input is valid for the renderable, false otherwise.
         */
        fun validate(shader: BaseShader, inputID: Int, renderable: Renderable?): Boolean
    }

    /**
     * Setter interface. By default all setters are global.
     */
    interface Setter {
        /**
         * @return True if the uniform only has to be set once per render call, false if the uniform must be set for
         * each renderable.
         */
        fun isGlobal(shader: BaseShader, inputID: Int): Boolean {
            return true
        }

        fun set(
            shader: BaseShader, inputID: Int, renderable: Renderable?,
            combinedAttributes: Attributes?
        )
    }

    fun interface SetterFunction {
        fun set(
            shader: BaseShader, inputID: Int, renderable: Renderable?,
            combinedAttributes: Attributes?
        )
    }

    abstract class LocalSetter : Setter {
        override fun isGlobal(shader: BaseShader, inputID: Int): Boolean {
            return false
        }
    }

    class Uniform(val alias: String, val materialMask: Long, val environmentMask: Long, val overallMask: Long) :
        Validator {
        constructor(alias: String, materialMask: Long = 0, environmentMask: Long = 0) : this(
            alias,
            materialMask,
            environmentMask,
            0
        )

        constructor(alias: String, overallMask: Long) : this(alias, 0, 0, overallMask)

        override fun validate(shader: BaseShader, inputID: Int, renderable: Renderable?): Boolean {
            val matFlags =
                if (renderable != null && renderable.material != null) renderable.material!!.getMask() else 0
            val envFlags =
                if (renderable != null && renderable.environment != null) renderable.environment!!.getMask() else 0
            return ((matFlags and materialMask) == materialMask) && ((envFlags and environmentMask) == environmentMask)
                    && (((matFlags or envFlags) and overallMask) == overallMask)
        }
    }

    private val uniforms = Array<String>()
    private val validators = Array<Validator>()
    private val setters = Array<Setter>()
    private var locations: IntArray? = null
    private val globalUniforms = com.badlogic.gdx.utils.IntArray()
    private val localUniforms = com.badlogic.gdx.utils.IntArray()
    private val attributes = IntIntMap()

    var program: ShaderProgram? = null
    var context: RenderContext? = null
    var camera: Camera? = null
    private var currentMesh: Mesh? = null

    /**
     * Register an uniform which might be used by this shader. Only possible prior to the call to init().
     *
     * @return The ID of the uniform to use in this shader.
     */
    @JvmOverloads
    fun register(alias: String?, validator: Validator? = null, setter: Setter? = null): Int {
        if (locations != null) {
            throw GdxRuntimeException("Cannot register an uniform after initialization")
        }
        val existing = getUniformID(alias)
        if (existing >= 0) {
            validators.set(existing, validator)
            setters.set(existing, setter)
            return existing
        }
        uniforms.add(alias)
        validators.add(validator)
        setters.add(setter)
        return uniforms.size - 1
    }

    fun register(alias: String?, setter: Setter?): Int {
        return register(alias, null, setter)
    }

    @JvmOverloads
    fun register(uniform: Uniform, setter: Setter? = null): Int {
        return register(uniform.alias, uniform, setter)
    }

    /**
     * @return the ID of the input or negative if not available.
     */
    fun getUniformID(alias: String?): Int {
        val n = uniforms.size
        for (i in 0..<n) {
            if (uniforms.get(i) == alias) {
                return i
            }
        }
        return -1
    }

    /**
     * @return The input at the specified id.
     */
    fun getUniformAlias(id: Int): String? {
        return uniforms.get(id)
    }

    /**
     * Initialize this shader, causing all registered uniforms/attributes to be fetched.
     */
    fun init(program: ShaderProgram, renderable: Renderable?) {
        if (locations != null) {
            throw GdxRuntimeException("Already initialized")
        }
        if (!program.isCompiled()) {
            throw GdxRuntimeException(program.getLog())
        }
        this.program = program

        val n = uniforms.size
        locations = IntArray(n)
        for (i in 0..<n) {
            val input = uniforms.get(i)
            val validator = validators.get(i)
            val setter = setters.get(i)
            if (validator != null && !validator.validate(this, i, renderable)) {
                locations!![i] = -1
            } else {
                locations!![i] = program.fetchUniformLocation(input, false)
                if (locations!![i] >= 0 && setter != null) {
                    if (setter.isGlobal(this, i)) {
                        globalUniforms.add(i)
                    } else {
                        localUniforms.add(i)
                    }
                }
            }
            if (locations!![i] < 0) {
                validators.set(i, null)
                setters.set(i, null)
            }
        }
        if (renderable != null) {
            val attrs = renderable.meshPart.mesh!!.vertexAttributes
            val c = attrs.size()
            for (i in 0..<c) {
                val attr = attrs.get(i)
                val location = program.getAttributeLocation(attr.alias)
                if (location >= 0) {
                    attributes.put(attr.key, location)
                }
            }
        }
    }

    override fun begin(camera: Camera?, context: RenderContext?) {
        this.camera = camera
        this.context = context
        program!!.bind()
        currentMesh = null
        var u: Int
        var i = 0
        while (i < globalUniforms.size) {
            if (setters.get(globalUniforms.get(i).also { u = it }) != null) {
                setters.get(u)!!.set(this, u, null, null)
            }
            ++i
        }
    }

    private val tempArray = com.badlogic.gdx.utils.IntArray()

    private fun getAttributeLocations(attrs: VertexAttributes): kotlin.IntArray? {
        tempArray.clear()
        val n = attrs.size()
        for (i in 0..<n) {
            tempArray.add(attributes.get(attrs.get(i).getKey(), -1))
        }
        tempArray.shrink()
        return tempArray.items
    }

    private val combinedAttributes = Attributes()

    override fun render(renderable: Renderable) {
        if (renderable.worldTransform.det3x3() == 0f) {
            return
        }
        combinedAttributes.clear()
        if (renderable.environment != null) {
            combinedAttributes.set(renderable.environment)
        }
        if (renderable.material != null) {
            combinedAttributes.set(renderable.material)
        }
        render(renderable, combinedAttributes)
    }

    open fun render(renderable: Renderable, combinedAttributes: Attributes?) {
        var u: Int
        var i = 0
        while (i < localUniforms.size) {
            if (setters.get(localUniforms.get(i).also { u = it }) != null) {
                setters.get(u)!!.set(this, u, renderable, combinedAttributes)
            }
            ++i
        }
        if (currentMesh !== renderable.meshPart.mesh) {
            if (currentMesh != null) {
                currentMesh!!.unbind(program, tempArray.items)
            }
            currentMesh = renderable.meshPart.mesh
            currentMesh!!.bind(program, getAttributeLocations(renderable.meshPart.mesh!!.vertexAttributes))
        }
        renderable.meshPart.render(program, false)
    }

    override fun end() {
        if (currentMesh != null) {
            currentMesh!!.unbind(program, tempArray.items)
            currentMesh = null
        }
    }

    override fun dispose() {
        program = null
        uniforms.clear()
        validators.clear()
        setters.clear()
        localUniforms.clear()
        globalUniforms.clear()
        locations = null
    }

    /**
     * Whether this Shader instance implements the specified uniform, only valid after a call to init().
     */
    fun has(inputID: Int): Boolean {
        return inputID >= 0 && inputID < locations!!.size && locations!![inputID] >= 0
    }

    fun loc(inputID: Int): Int {
        return if (inputID >= 0 && inputID < locations!!.size) locations!![inputID] else -1
    }

    fun set(uniform: Int, value: Matrix4?): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformMatrix(locations!![uniform], value)
        return true
    }

    fun set(uniform: Int, value: Matrix3?): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformMatrix(locations!![uniform], value)
        return true
    }

    fun set(uniform: Int, value: Vector3?): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformf(locations!![uniform], value)
        return true
    }

    fun set(uniform: Int, value: Vector2?): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformf(locations!![uniform], value)
        return true
    }

    fun set(uniform: Int, value: Color?): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformf(locations!![uniform], value)
        return true
    }

    fun set(uniform: Int, value: Float): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformf(locations!![uniform], value)
        return true
    }

    fun set(uniform: Int, v1: Float, v2: Float): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformf(locations!![uniform], v1, v2)
        return true
    }

    fun set(uniform: Int, v1: Float, v2: Float, v3: Float): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformf(locations!![uniform], v1, v2, v3)
        return true
    }

    fun set(uniform: Int, v1: Float, v2: Float, v3: Float, v4: Float): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformf(locations!![uniform], v1, v2, v3, v4)
        return true
    }

    fun set(uniform: Int, value: Int): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformi(locations!![uniform], value)
        return true
    }

    fun set(uniform: Int, v1: Int, v2: Int): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformi(locations!![uniform], v1, v2)
        return true
    }

    fun set(uniform: Int, v1: Int, v2: Int, v3: Int): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformi(locations!![uniform], v1, v2, v3)
        return true
    }

    fun set(uniform: Int, v1: Int, v2: Int, v3: Int, v4: Int): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformi(locations!![uniform], v1, v2, v3, v4)
        return true
    }

    fun set(uniform: Int, textureDesc: TextureDescriptor<*>?): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformi(locations!![uniform], context!!.textureBinder.bind(textureDesc))
        return true
    }

    fun set(uniform: Int, texture: GLTexture?): Boolean {
        if (locations!![uniform] < 0) {
            return false
        }
        program!!.setUniformi(locations!![uniform], context!!.textureBinder.bind(texture))
        return true
    }
}
