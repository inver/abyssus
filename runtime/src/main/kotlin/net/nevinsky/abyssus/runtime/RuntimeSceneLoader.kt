/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime

import net.nevinsky.abyssus.core.io.FileLoader
import net.nevinsky.abyssus.core.io.JsonProcessor
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.core.scene.SceneLoader
import net.nevinsky.abyssus.runtime.ecs.EcsConfigurator
import net.nevinsky.abyssus.runtime.ecs.render.FolderAssetResolver
import net.nevinsky.abyssus.runtime.schema.ComponentRegistry
import net.nevinsky.abyssus.runtime.schema.GameComponents
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter

/**
 * Loads the scenes of one project folder into independent Ashley engines, without editor or GL services. Each load
 * owns its engine, entities, resolver and warnings, and logs under the scene's name. The game components of [registry]
 * are checked here, so a registration that fails (see [net.nevinsky.abyssus.runtime.schema.ComponentRegistrationException])
 * fails the construction and no scene loads.
 *
 * A scene that cannot be read is logged once, with its cause, and loads as null, so a caller can carry on with the
 * others.
 */
class RuntimeSceneLoader(
    private val jsonProcessor: JsonProcessor,
    private val fileLoader: FileLoader,
    private val sceneLoader: SceneLoader,
    private val log: Logger,
    registry: ComponentRegistry = ComponentRegistry { emptyList() },
) {
    constructor(
        jsonProcessor: JsonProcessor,
        fileLoader: FileLoader,
        log: Logger,
        registry: ComponentRegistry = ComponentRegistry { emptyList() },
    ) : this(jsonProcessor, fileLoader, SceneLoader(jsonProcessor, fileLoader), log, registry)

    /** The registered game components: their schemas and classes. */
    val game = GameComponents(registry)

    /** The scene file [sceneName] (its name in the project's `scenes` folder, extension included). */
    fun load(sceneName: String): SceneContext? = reported(sceneName) {
        context(sceneLoader.load(sceneName), Prefixed(log, "$sceneName: "))
    }

    /** The scene [text] holds, including unsaved edits; it never reads or writes a scene file. */
    fun loadFromText(text: String): SceneContext? = reported("the scene text") {
        context(sceneLoader.parse(text), log)
    }

    private fun context(scene: Scene, sceneLog: Logger): SceneContext {
        val loaded = EcsConfigurator(jsonProcessor.mapper, FolderAssetResolver(fileLoader.assetNames()), sceneLog, game)
            .load(scene.ecs ?: jsonProcessor.readObject("{}"))
        return SceneContext(loaded.engine, scene, loaded.document)
    }

    private fun <T> reported(source: String, read: () -> T?): T? = runCatchingKeepingCancellation(read)
        .getOrElse { error -> log.warn("Could not load $source: ${error.message}", error); null }
}

/** [delegate] with [prefix] (the scene's path) before every message. */
private class Prefixed(private val delegate: Logger, private val prefix: String) : LegacyAbstractLogger() {
    init {
        name = delegate.name
    }

    override fun isTraceEnabled() = delegate.isTraceEnabled
    override fun isDebugEnabled() = delegate.isDebugEnabled
    override fun isInfoEnabled() = delegate.isInfoEnabled
    override fun isWarnEnabled() = delegate.isWarnEnabled
    override fun isErrorEnabled() = delegate.isErrorEnabled
    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any?>?,
        throwable: Throwable?
    ) {
        delegate.atLevel(level).setCause(throwable)
            .log(prefix + MessageFormatter.basicArrayFormat(messagePattern, arguments))
    }
}
