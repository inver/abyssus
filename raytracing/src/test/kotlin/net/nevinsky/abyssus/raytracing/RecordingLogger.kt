/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.raytracing

import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import java.util.concurrent.CopyOnWriteArrayList

/** Keeps what the backends log, so tests can assert on it (the module has no test dependency on `core`'s fixtures). */
internal class RecordingLogger : LegacyAbstractLogger() {
    data class Entry(val level: Level, val message: String)

    val entries = CopyOnWriteArrayList<Entry>()

    init {
        name = "recording"
    }

    override fun isTraceEnabled() = true
    override fun isDebugEnabled() = true
    override fun isInfoEnabled() = true
    override fun isWarnEnabled() = true
    override fun isErrorEnabled() = true
    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(level: Level, marker: Marker?, messagePattern: String?, arguments: Array<out Any?>?, throwable: Throwable?) {
        entries += Entry(level, MessageFormatter.basicArrayFormat(messagePattern, arguments))
    }
}
