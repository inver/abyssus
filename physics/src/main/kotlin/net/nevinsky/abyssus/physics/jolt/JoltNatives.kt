/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.jolt

import com.github.stephengold.joltjni.Jolt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val PACKAGE = "com/github/stephengold"

/** The jolt-jni version of the classpath; keeps the extracted libraries of different versions apart. */
private const val VERSION = "6.1.1"

/**
 * Loads jolt-jni's native library from the classpath (a `ReleaseSp` or `DebugSp` classifier jar) and sets Jolt up
 * once per process: allocator, callbacks, factory and types. Loading again in the same process does nothing.
 *
 * The library is extracted to [cacheDir], to one file per platform and Jolt version, so repeated loads use the
 * same path.
 */
class JoltNatives(
    private val cacheDir: Path = Path.of(System.getProperty("java.io.tmpdir")),
    private val osName: String = System.getProperty("os.name"),
    private val osArch: String = System.getProperty("os.arch"),
    private val classLoader: ClassLoader = JoltNatives::class.java.classLoader,
) {
    /** The classpath resource holding the library for this platform. */
    val resource: String
        get() {
            val os = osName.lowercase()
            val arm = osArch == "aarch64" || osArch == "arm64"
            return when {
                os.contains("mac") -> if (arm) "osx/aarch64/$PACKAGE/libjoltjni.dylib" else "osx/x86-64/$PACKAGE/libjoltjni.dylib"
                os.contains("win") -> "windows/x86-64/$PACKAGE/joltjni.dll"
                os.contains("linux") -> if (arm) "linux/aarch64/$PACKAGE/libjoltjni.so" else "linux/x86-64/$PACKAGE/libjoltjni.so"
                else -> throw UnsupportedOperationException("Jolt has no native library for $osName $osArch")
            }
        }

    /** Loads and sets Jolt up unless this process already has. */
    fun load() {
        if (loaded()) return
        val url = classLoader.getResource(resource)
            ?: throw IllegalStateException("Jolt native library $resource is not on the classpath")
        val target = cacheDir.resolve("abyssus-jolt-jni-$VERSION").resolve(resource.replace('/', '_'))
        if (!Files.isRegularFile(target)) {
            Files.createDirectories(target.parent)
            val part = Files.createTempFile(target.parent, "joltjni", ".part")
            url.openStream().use { Files.copy(it, part, StandardCopyOption.REPLACE_EXISTING) }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        System.load(target.toAbsolutePath().toString())
        Jolt.registerDefaultAllocator()
        Jolt.installDefaultAssertCallback()
        Jolt.installDefaultTraceCallback()
        check(Jolt.newFactory()) { "Jolt could not create its factory" }
        Jolt.registerTypes()
    }

    /** Whether this process has loaded the library: Jolt's native methods link only after it is loaded. */
    fun loaded(): Boolean = try {
        Jolt.versionString()
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }
}
