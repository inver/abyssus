/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin

import com.intellij.DynamicBundle
import net.nevinsky.abyssus.lib.core.editor.EDITOR_BUNDLE
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import org.jetbrains.annotations.PropertyKey

/** The editing library's messages as the IDE shows them; the text lives in `messages/AbyssusEditorBundle.properties`. */
object EditorBundle : DynamicBundle(EditorMessages::class.java, EDITOR_BUNDLE), EditorMessages {
    @Suppress("SpreadOperator")
    override fun message(@PropertyKey(resourceBundle = EDITOR_BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
