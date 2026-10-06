/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.testing

import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An SLF4J logger that keeps what it is given, for tests of the modules that log through SLF4J. [echo] also prints
 * warnings and errors to standard error, the way the IDE logger does in tests; [debugEnabled] turns the lazy debug
 * messages on or off.
 */
class RecordingLogger(
    private val echo: Boolean = false,
    private val debugEnabled: Boolean = true,
    private val warningsInto: MutableList<String>? = null,
) : LegacyAbstractLogger() {
    data class Entry(val level: Level, val message: String, val error: Throwable?)

    val entries = CopyOnWriteArrayList<Entry>()

    init {
        name = "recording"
    }

    /** The messages logged at [level], oldest first. */
    fun messages(level: Level): List<String> = entries.filter { it.level == level }.map { it.message }

    val warnings: List<String> get() = messages(Level.WARN)

    /** The exceptions attached to warnings, oldest first. */
    val throwables: List<Throwable> get() = entries.filter { it.level == Level.WARN }.mapNotNull { it.error }

    override fun isTraceEnabled() = debugEnabled
    override fun isDebugEnabled() = debugEnabled
    override fun isInfoEnabled() = true
    override fun isWarnEnabled() = true
    override fun isErrorEnabled() = true
    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(level: Level, marker: Marker?, messagePattern: String?, arguments: Array<out Any?>?, throwable: Throwable?) {
        val message = MessageFormatter.basicArrayFormat(messagePattern, arguments)
        entries += Entry(level, message, throwable)
        if (level == Level.WARN || level == Level.ERROR) warningsInto?.add(message)
        if (echo && level.toInt() >= Level.WARN.toInt()) {
            System.err.println("WARN: $message")
            throwable?.printStackTrace()
        }
    }
}

/** A logger that appends every warning (and error) message to [list], for tests that only care about problems. */
fun warningsTo(list: MutableList<String>): org.slf4j.Logger = RecordingLogger(warningsInto = list)

/** A logger that fails the test with the message of the first warning (or error) logged. */
fun failOnWarnings(): org.slf4j.Logger = RecordingLogger(warningsInto = object : java.util.AbstractList<String>() {
    override val size get() = 0
    override fun get(index: Int): String = throw IndexOutOfBoundsException()
    override fun add(element: String): Boolean = throw AssertionError(element)
})
