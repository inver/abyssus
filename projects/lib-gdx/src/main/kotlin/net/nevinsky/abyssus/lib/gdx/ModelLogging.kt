/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The logger of the model runtime and the Assimp importer. Model code is reached from static loaders, so it cannot be
 * handed a logger through constructors: this holder is process-wide, like `Gdx.*` itself. The default is the SLF4J
 * logger `abyssus.model`; an application that wants another one (the Abyssus plugin installs a logger over the IDE's
 * own) sets [logger] once at startup.
 */
object ModelLogging {
    @Volatile var logger: Logger = LoggerFactory.getLogger("abyssus.model")
}
