/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
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

tasks.test {
    System.getProperty("abyssus.metalTests")?.let { systemProperty("abyssus.metalTests", it) }
    System.getProperty("abyssus.metalTimingTests")?.let { systemProperty("abyssus.metalTimingTests", it) }
}

// Use the built jar rather than loose main resources: proves JNI and metallib packaging and real device loading.
val verifyNativePackaging = tasks.register<Test>("verifyNativePackaging") {
    dependsOn(tasks.jar, tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = files(sourceSets.test.get().output, tasks.jar.flatMap { it.archiveFile }) +
        configurations.testRuntimeClasspath.get()
    filter { includeTestsMatching("*MetalNativePackagingTest") }
    systemProperty("abyssus.metalTests", "true")
    if (!hostIsMac) doFirst { error("Metal packaging verification must run on macOS") }
}
