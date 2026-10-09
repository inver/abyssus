/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.loading

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import net.nevinsky.abyssus.lib.gdx.io.FileLoader

/** The classpath folder of the shaders bundled with this module. */
const val DEFAULT_SHADER_ROOT = "/shader/sky"

/**
 * Finds GLSL text and compiles programs from three places, most specific first:
 *
 * 1. **the asset** a program belongs to: a file in that asset's folder of the project ([files], see [withAssets]),
 *    for shaders an author ships with an asset (a procedural sky's `sky.frag`);
 * 2. **resources**: classpath folders the host adds ([withResources]), for the shaders of the application;
 * 3. **defaults**: the shaders bundled with this module ([DEFAULT_SHADER_ROOT]), used when nothing more specific has the
 *    file.
 *
 * A file found in an earlier place hides the same name in a later one, so a project or a host can override a default.
 * Reading is plain IO and safe on any thread; the `program` functions compile, return a new [ShaderProgram] the caller
 * owns and disposes, and need a current GL context.
 */
class ShaderStorage private constructor(
    private val resources: List<ClasspathRoot>,
    private val files: FileLoader?,
) {
    /** The defaults only. */
    constructor() : this(emptyList(), null)

    /** A classpath folder [path] (for example `/shader/scene`), read through the class loader of [anchor]. */
    private class ClasspathRoot(val path: String, val anchor: Class<*>) {
        fun read(file: String): String? =
            anchor.getResourceAsStream("$path/$file")?.use { String(it.readAllBytes(), Charsets.UTF_8) }
    }

    private val defaults = ClasspathRoot(DEFAULT_SHADER_ROOT, ShaderStorage::class.java)

    /** This storage that also reads the asset folders of the project [files] points at. */
    fun withAssets(files: FileLoader): ShaderStorage = ShaderStorage(resources, files)

    /**
     * This storage that also reads the classpath folder [path] (for example `/shader/scene`) through the class loader
     * of [anchor], ahead of the defaults and after earlier resources.
     */
    fun withResources(path: String, anchor: Class<*>): ShaderStorage =
        ShaderStorage(resources + ClasspathRoot(path, anchor), files)

    /**
     * The text of [file]: from the folder of the asset [asset] when it has one, else from the resources, else from the
     * defaults. Throws, naming where it looked, when nobody has it.
     */
    fun read(file: String, asset: String? = null): String = checkNotNull(readOrNull(file, asset)) {
        "Missing shader '$file' (looked in ${places(asset).joinToString()})"
    }

    /** Like [read]; null when nobody has [file]. */
    fun readOrNull(file: String, asset: String? = null): String? {
        if (asset != null) {
            files?.findAssetFile(asset, file)?.let { return it.readText() }
        }
        for (root in resources + defaults) {
            root.read(file)?.let { return it }
        }
        return null
    }

    /** The files [fragment] joined in order (shared functions first), as one fragment shader. */
    fun fragment(vararg fragment: String, asset: String? = null): String =
        fragment.joinToString("\n") { read(it, asset) }

    /** The program of [name]`.vert` and [name]`.frag`. */
    fun program(name: String): ShaderProgram = program("$name.vert", "$name.frag")

    /** The program of the file [vertex] and the files [fragment] joined in order, from the resources and defaults. */
    fun program(vertex: String, vararg fragment: String): ShaderProgram =
        compileProgram(read(vertex), fragment(*fragment), fragment.last())

    /** Like [program], but each file is looked up in the folder of the asset [asset] first. */
    fun assetProgram(asset: String, vertex: String, vararg fragment: String): ShaderProgram =
        compileProgram(read(vertex, asset), fragment(*fragment, asset = asset), fragment.last())

    private fun places(asset: String?): List<String> =
        listOfNotNull(asset?.takeIf { files != null }?.let { "asset '$it'" }) + (resources + defaults).map { it.path }
}

/** Compiles [vertex] and [fragment] on the current GL context. Throws with the compiler log, naming [label], when they do not compile. */
fun compileProgram(vertex: String, fragment: String, label: String): ShaderProgram {
    val program = ShaderProgram(vertex, fragment)
    if (!program.isCompiled) {
        val log = program.log
        program.dispose()
        throw IllegalStateException("Shader '$label' failed to compile: $log")
    }
    return program
}
