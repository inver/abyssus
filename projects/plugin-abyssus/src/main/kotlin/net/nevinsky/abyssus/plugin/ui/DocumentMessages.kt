package net.nevinsky.abyssus.plugin.ui

import net.nevinsky.abyssus.lib.core.editor.document.documentDisplayMessage
import net.nevinsky.abyssus.plugin.EditorBundle

fun Throwable.documentDisplayMessage(): String = documentDisplayMessage(EditorBundle)
