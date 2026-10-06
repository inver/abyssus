/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.editor

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

/** The bundle that holds every message of the editing library (`messages/AbyssusEditorBundle.properties`). */
const val EDITOR_BUNDLE = "messages.AbyssusEditorBundle"

/**
 * The text of the editing library's messages. The plugin supplies a `DynamicBundle` over [EDITOR_BUNDLE]; a caller
 * without the IDE uses [ResourceEditorMessages]. Both read the same file and format it the same way.
 */
fun interface EditorMessages {
    fun message(key: String, vararg params: Any): String
}

/**
 * [EDITOR_BUNDLE] read as a plain [ResourceBundle], formatted as the IDE formats its bundles: the text as written when
 * there are no parameters, else through a [MessageFormat] of the bundle's locale.
 */
class ResourceEditorMessages(private val locale: Locale = Locale.ROOT) : EditorMessages {
    private val bundle: ResourceBundle = ResourceBundle.getBundle(EDITOR_BUNDLE, locale, EditorMessages::class.java.classLoader)

    override fun message(key: String, vararg params: Any): String {
        val value = bundle.getString(key)
        if (params.isEmpty() || '{' !in value) return value
        return MessageFormat(value, locale).format(params)
    }
}
