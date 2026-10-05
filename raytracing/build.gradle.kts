/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

repositories { mavenCentral() }

val lwjglVersion = providers.gradleProperty("lwjglVersion").get()

dependencies {
    implementation(kotlin("stdlib"))
    api("org.slf4j:slf4j-api:2.0.6")
    // Vulkan bindings load nothing until a probe runs. lwjgl-vulkan publishes natives only for macOS (MoltenVK);
    // Windows and Linux use the system loader. lwjgl-vma has natives for every target.
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-vulkan:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-vma:$lwjglVersion")
    listOf("natives-macos-arm64", "natives-macos", "natives-windows", "natives-linux").forEach {
        runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$it")
        runtimeOnly("org.lwjgl:lwjgl-vma:$lwjglVersion:$it")
    }
    listOf("natives-macos-arm64", "natives-macos").forEach { runtimeOnly("org.lwjgl:lwjgl-vulkan:$lwjglVersion:$it") }
    testImplementation("junit:junit:4.13.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

// Explicit properties keep native builds compatible with Gradle's configuration cache.
abstract class BuildMetalNative : DefaultTask() {
    @get:Input abstract val architecture: Property<String>
    @get:Input abstract val toolchainVersion: Property<String>
    @get:InputFile abstract val shader: RegularFileProperty
    @get:InputFile abstract val bridge: RegularFileProperty
    @get:InputDirectory abstract val jdkInclude: DirectoryProperty
    @get:OutputDirectory abstract val destination: DirectoryProperty
    @get:Inject abstract val execOperations: ExecOperations

    @TaskAction fun build() {
        val architectures = architecture.get().split(',')
        check(architectures.isNotEmpty() && architectures.all { it in listOf("arm64", "x86_64") }) { "Unsupported Metal architecture" }
        for (arch in architectures) {
        val output = destination.get().asFile.resolve("native/macos-$arch")
        output.mkdirs()
        val air = temporaryDir.resolve("slice.air")
        val includes = jdkInclude.get().asFile
        val commands = listOf(
            listOf("xcrun", "-sdk", "macosx", "metal", "-std=metal3.0", "-mmacosx-version-min=13.0", "-c",
                shader.get().asFile.absolutePath, "-o", air.absolutePath),
            listOf("xcrun", "-sdk", "macosx", "metallib", air.absolutePath, "-o", output.resolve("slice.metallib").absolutePath),
            listOf("xcrun", "-sdk", "macosx", "clang++", "-std=c++17", "-fobjc-arc", "-dynamiclib", "-arch", arch,
                "-mmacosx-version-min=13.0", "-I${includes.absolutePath}", "-I${includes.resolve("darwin").absolutePath}",
                "-framework", "Foundation", "-framework", "Metal", bridge.get().asFile.absolutePath,
                "-o", output.resolve("libabyssus_ray.dylib").absolutePath)
        )
        commands.forEach { command -> execOperations.exec { commandLine(command) }.assertNormalExitValue() }
        }
    }
}

val metalResources = layout.buildDirectory.dir("generated/metal-resources")
val hostIsMac = System.getProperty("os.name").startsWith("Mac")
val buildMetalNative = tasks.register<BuildMetalNative>("buildMetalNative") {
    architecture.set(providers.gradleProperty("abyssus.metalArch").orElse("arm64,x86_64"))
    if (hostIsMac) toolchainVersion.set(providers.exec { commandLine("xcodebuild", "-version") }.standardOutput.asText)
    else toolchainVersion.set("unavailable")
    shader.set(layout.projectDirectory.file("src/main/native/metal/slice.metal"))
    bridge.set(layout.projectDirectory.file("src/main/native/metal/bridge.mm"))
    jdkInclude.set(file(System.getProperty("java.home")).resolve("include"))
    destination.set(metalResources)
}

if (hostIsMac) {
    sourceSets.main { resources.srcDir(metalResources) }
    tasks.processResources { dependsOn(buildMetalNative) }
}

// GLSL compute shaders -> SPIR-V at build time. No shaderc is packaged. Without a compiler on PATH the task warns and
// ships no SPIR-V (the Vulkan backend then reports itself unavailable); -Pabyssus.requireShaders=true makes that an error.
abstract class CompileSpirv : DefaultTask() {
    @get:InputDirectory abstract val sources: DirectoryProperty
    @get:Input abstract val compiler: Property<String>
    @get:Input abstract val required: Property<Boolean>
    @get:OutputDirectory abstract val destination: DirectoryProperty
    @get:Inject abstract val execOperations: ExecOperations

    @TaskAction fun compile() {
        val out = destination.get().asFile.resolve("native/vulkan")
        out.deleteRecursively()
        val tool = compiler.get().takeIf { it.isNotEmpty() }
        if (tool == null) {
            check(!required.get()) { "glslangValidator (or glslc) is required to build the Vulkan shaders" }
            logger.warn("No glslangValidator/glslc found (PATH, VULKAN_SDK, Android NDK shader-tools): Vulkan SPIR-V shaders are not built and Vulkan ray tracing will be unavailable. Install one or pass -Pabyssus.glslc=/path/to/tool")
            return
        }
        out.mkdirs()
        sources.get().asFile.listFiles { f -> f.extension == "comp" }!!.sorted().forEach { shader ->
            val target = out.resolve(shader.nameWithoutExtension + ".spv")
            val command = if (tool.endsWith("glslc") || tool.endsWith("glslc.exe"))
                listOf(tool, "--target-env=vulkan1.2", shader.absolutePath, "-o", target.absolutePath) // no -O: it inlines the shading code tenfold and drivers optimize SPIR-V anyway
            else listOf(tool, "--target-env", "vulkan1.2", "-V", shader.absolutePath, "-o", target.absolutePath)
            execOperations.exec { commandLine(command) }.assertNormalExitValue()
        }
    }
}

/**
 * The compiler to use: `PATH` first, then the places SDKs put one (`VULKAN_SDK`, and the Android NDK's `shader-tools`
 * under `ANDROID_HOME`, `ANDROID_SDK_ROOT` or the default `~/Android/Sdk`, `~/Library/Android/sdk`).
 */
fun findShaderCompiler(): String? {
    val extensions = if (System.getProperty("os.name").startsWith("Windows")) listOf(".exe", ".bat", "") else listOf("")
    val names = listOf("glslangValidator", "glslc")
    fun inDirs(dirs: List<File>) = names.firstNotNullOfOrNull { name ->
        dirs.firstNotNullOfOrNull { dir -> extensions.map { File(dir, name + it) }.firstOrNull { it.isFile } }
    }?.absolutePath
    inDirs((System.getenv("PATH") ?: "").split(File.pathSeparator).filter { it.isNotEmpty() }.map(::File))?.let { return it }
    val home = System.getProperty("user.home")
    val sdkDirs = listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"), "$home/Android/Sdk", "$home/Library/Android/sdk").map(::File)
    val ndkTools = sdkDirs.flatMap { sdk -> File(sdk, "ndk").listFiles { f -> f.isDirectory }.orEmpty().sortedDescending() }
        .flatMap { ndk -> File(ndk, "shader-tools").listFiles { f -> f.isDirectory }.orEmpty().toList() }
    val vulkanSdk = System.getenv("VULKAN_SDK")?.let { listOf(File(it, "bin"), File(it, "Bin")) }.orEmpty()
    return inDirs(vulkanSdk + ndkTools)
}

val spirvResources = layout.buildDirectory.dir("generated/spirv-resources")
val compileSpirv = tasks.register<CompileSpirv>("compileSpirv") {
    sources.set(layout.projectDirectory.dir("src/main/glsl"))
    compiler.set(providers.gradleProperty("abyssus.glslc").orElse(provider { findShaderCompiler() ?: "" }))
    required.set(providers.gradleProperty("abyssus.requireShaders").map { it.toBoolean() }.orElse(false))
    destination.set(spirvResources)
}
sourceSets.main { resources.srcDir(spirvResources) }
tasks.processResources { dependsOn(compileSpirv) }

tasks.test {
    System.getProperty("abyssus.vulkanTests")?.let { systemProperty("abyssus.vulkanTests", it) }
    System.getProperty("abyssus.raytracing.validation")?.let { systemProperty("abyssus.raytracing.validation", it) }
    System.getProperty("abyssus.raytracing.validationReport")?.let { systemProperty("abyssus.raytracing.validationReport", it) }
    System.getProperty("abyssus.raytracing.validationMessenger")?.let { systemProperty("abyssus.raytracing.validationMessenger", it) }
    System.getProperty("abyssus.metalTests")?.let { systemProperty("abyssus.metalTests", it) }
    System.getProperty("abyssus.metalTimingTests")?.let { systemProperty("abyssus.metalTimingTests", it) }
    System.getProperty("abyssus.vulkanTimingTests")?.let { systemProperty("abyssus.vulkanTimingTests", it) }
}

// Use the built jar rather than loose main resources: proves resource packaging, native loading and device behavior.
val jarTask = tasks.jar
val testClassesTask = tasks.testClasses
val testOutput = sourceSets.test.get().output
val testRuntime = configurations.testRuntimeClasspath
fun packagingTest(name: String, vararg classes: String, configure: Test.() -> Unit = {}) = tasks.register<Test>(name) {
    dependsOn(jarTask, testClassesTask)
    testClassesDirs = testOutput.classesDirs
    classpath = files(testOutput, jarTask.flatMap { it.archiveFile }) + testRuntime.get()
    filter { classes.forEach { includeTestsMatching(it) } }
    configure()
}

val verifyMetalPackaging = packagingTest("verifyMetalPackaging", "*MetalNativePackagingTest") {
    systemProperty("abyssus.metalTests", "true")
    if (!hostIsMac) doFirst { error("Metal packaging verification must run on macOS") }
}

// SPIR-V present and valid, no shaderc, natives for every target, MoltenVK only for macOS, and "runtime not found"
// (not an exception) without a loader. Add -Dabyssus.vulkanTests=true on a machine with a Vulkan device to also render.
val verifyVulkanPackaging = packagingTest("verifyVulkanPackaging", "*VulkanNativePackagingTest", "*VulkanRuntimeMissingTest") {
    forkEvery = 1 // LWJGL's loader choice is process-global
    systemProperty("abyssus.vulkanMissingLoaderTest", "true")
    System.getProperty("abyssus.vulkanTests")?.let { systemProperty("abyssus.vulkanTests", it) }
}

val verifyNativePackaging = tasks.register("verifyNativePackaging") {
    dependsOn(verifyVulkanPackaging)
    if (hostIsMac) dependsOn(verifyMetalPackaging)
}
