/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("PlayExportMain")

package net.nevinsky.abyssus.physics.play

import java.io.File
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Writes `<project>/abyssus/play.json` for a game, beside its component schema (see `SchemaExportMain`). Run by the
 * game's Gradle `JavaExec` task on its runtime classpath with main class
 * `net.nevinsky.abyssus.physics.play.PlayExportMain` and two arguments: the class name of its [PlayModule] (with a
 * no-argument constructor) and the project folder. The classpath written is the one this program runs on.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: PlayExportMain <PlayModule class> <project folder>")
        exitProcess(2)
    }
    val classpath = System.getProperty("java.class.path").split(File.pathSeparator)
    val file = exportPlay(args[0], classpath, Path.of(args[1]))
    println("Wrote $file")
}

/**
 * Checks that [module] is a [PlayModule] that can be created, then writes `play.json` into [projectDir] with the
 * [classpath] entries made absolute (empty entries dropped, order kept).
 */
fun exportPlay(module: String, classpath: List<String>, projectDir: Path): Path {
    val type = Class.forName(module)
    require(PlayModule::class.java.isAssignableFrom(type)) { "$module is not a PlayModule" }
    type.getDeclaredConstructor().newInstance()
    val entries = classpath.filter { it.isNotBlank() }.map { Path.of(it).toAbsolutePath().normalize().toString() }
    return PlayFile().export(PlayConfig(PLAY_PROTOCOL, module, entries), projectDir)
}
