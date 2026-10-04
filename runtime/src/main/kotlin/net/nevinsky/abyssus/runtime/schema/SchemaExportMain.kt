/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("SchemaExportMain")

package net.nevinsky.abyssus.runtime.schema

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Writes the schema of a game's registered components to `<project>/abyssus/components.schema.json`. Run by a game's
 * Gradle `JavaExec` task with main class `net.nevinsky.abyssus.runtime.schema.SchemaExportMain` and two arguments: the
 * class name of its [ComponentRegistry] implementation (with a no-argument constructor) and the project folder.
 * Reads and writes no scene or project file.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: SchemaExportMain <ComponentRegistry class> <project folder>")
        exitProcess(2)
    }
    val registry = Class.forName(args[0]).getDeclaredConstructor().newInstance() as ComponentRegistry
    val file = exportSchema(registry, Path.of(args[1]))
    println("Wrote $file")
}

/** Checks the components of [registry] as a scene load would, then writes their schema into [projectDir]. */
fun exportSchema(registry: ComponentRegistry, projectDir: Path): Path =
    SchemaFile().export(GameComponents(registry).schemas, projectDir)
