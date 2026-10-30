/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

// Auto-update for an installed game: the signed release manifest, its signature check, the release lookup and the
// download-verify-switch sync. A plain JVM library (no editor, IntelliJ, AWT or libGDX imports) wired by constructors.
plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    id("abyssus.dependency-platforms")
}

dependencies {
    implementation(kotlin("stdlib"))
    api(libs.jackson.databind)
    api(libs.jackson.module.kotlin)
    // RFC 3161 timestamp tokens of GitHub's release attestation (the JDK has no CMS parser)
    implementation(libs.bouncycastle.bcpkix)
    testImplementation(libs.junit4)
    testFixturesImplementation(kotlin("stdlib"))
    testFixturesApi(project(":lib-updater"))
}

extra["abyssusSingletonExcludes"] = listOf<String>()

// The game ships this library through its launcher: `checkNoEditorCode` keeps editor imports out of it.
extra["abyssusShippedLibrary"] = true

// A release folder is checked before it is published (-PreleaseDir=<folder>, -PrequireChecksums=true when SHA256SUMS must be
// there), and again after publishing against GitHub's own release attestation.
tasks.register<JavaExec>("verifyReleaseManifest") {
    group = "verification"
    description = "Checks the manifest, the part hashes and SHA256SUMS of the release folder given as -PreleaseDir=<folder>."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.lib.updater.verify.VerifyReleaseMainKt")
    val folder = providers.gradleProperty("releaseDir")
    val requireChecksums = providers.gradleProperty("requireChecksums").map { it == "true" }.orElse(false)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(folder.orNull ?: throw GradleException("Pass the release folder: -PreleaseDir=<folder>")) +
            if (requireChecksums.get()) listOf("--require-checksums") else emptyList()
    })
}

tasks.register<JavaExec>("verifyPublishedRelease") {
    group = "verification"
    description = "Waits for GitHub's release attestation of -Prepository=<owner/repo> -Ptag=<tag> and checks it lists every file of -PreleaseDir."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.lib.updater.verify.PublishedReleaseVerifierKt")
    val repository = providers.gradleProperty("repository")
    val tag = providers.gradleProperty("tag")
    val folder = providers.gradleProperty("releaseDir")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            repository.orNull ?: throw GradleException("Pass -Prepository=<owner/repo>"), tag.orNull ?: throw GradleException("Pass -Ptag=<tag>"),
            folder.orNull ?: throw GradleException("Pass -PreleaseDir=<folder>"),
        )
    })
}
