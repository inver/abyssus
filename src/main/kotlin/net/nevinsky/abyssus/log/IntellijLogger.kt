/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.log

import com.intellij.openapi.diagnostic.Logger
import org.slf4j.ILoggerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * An SLF4J [org.slf4j.Logger] over the IDE's [Logger], so the plain libraries (`gdx-model`, `core`, `raytracing`), which
 * only know SLF4J, write to `idea.log` through the IDE logger and obey Help | Diagnostic Tools | Debug Log Settings.
 *
 * The platform already ships SLF4J, bound to `java.util.logging` (`lib/util-8.jar`), and its `LoggerFactory` binds one
 * provider for the whole IDE, so a plugin cannot add a second `SLF4JServiceProvider`. This adapter is therefore handed
 * out explicitly, by [IntellijLoggerFactory], instead of being discovered through `LoggerFactory`.
 *
 * Level mapping: TRACE/DEBUG/INFO/WARN map to the same IDE levels. ERROR maps to the IDE's *warn*: `Logger.error`
 * raises the "IDE error" notification and the fatal-error dialog, which is wrong for a problem with one asset or one
 * GPU. [name] is the IDE category.
 */
class IntellijLogger(name: String, private val ide: Logger = Logger.getInstance(name)) : LegacyAbstractLogger() {
    init {
        this.name = name
    }

    override fun isTraceEnabled() = ide.isTraceEnabled
    override fun isDebugEnabled() = ide.isDebugEnabled
    override fun isInfoEnabled() = true
    override fun isWarnEnabled() = true
    override fun isErrorEnabled() = true

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(level: Level, marker: Marker?, messagePattern: String?, arguments: Array<out Any?>?, throwable: Throwable?) {
        val message = MessageFormatter.basicArrayFormat(messagePattern, arguments)
        when (level) {
            Level.TRACE -> ide.trace(message)
            Level.DEBUG -> if (throwable != null) ide.debug(message, throwable) else ide.debug(message)
            Level.INFO -> if (throwable != null) ide.info(message, throwable) else ide.info(message)
            Level.WARN, Level.ERROR -> if (throwable != null) ide.warn(message, throwable) else ide.warn(message)
        }
    }
}

/** Creates [IntellijLogger]s under one category prefix: `IntellijLoggerFactory("Abyssus").getLogger("ray")` is `Abyssus.ray`. */
class IntellijLoggerFactory(private val prefix: String = "Abyssus") : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, IntellijLogger>()

    override fun getLogger(name: String): org.slf4j.Logger = loggers.getOrPut(name) { IntellijLogger(if (name.isEmpty()) prefix else "$prefix.$name") }
}
