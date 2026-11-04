/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.verify

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * `verifyReleaseManifest`: `<release folder> [--require-checksums]`. Exits 1 and prints every problem when the folder must not
 * be published (a part or a listed file differs from the manifest).
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: <release folder> [--require-checksums]")
        exitProcess(2)
    }
    try {
        ReleaseVerifier("--require-checksums" in args).verify(Path.of(args[0]))
        println("Release folder ${args[0]} verified")
    } catch (e: ReleaseVerificationException) {
        e.problems.forEach { System.err.println("FAIL $it") }
        exitProcess(1)
    }
}
