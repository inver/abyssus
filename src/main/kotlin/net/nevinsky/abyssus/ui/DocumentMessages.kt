package net.nevinsky.abyssus.ui

import net.nevinsky.abyssus.EditorBundle
import net.nevinsky.abyssus.editor.document.documentDisplayMessage

fun Throwable.documentDisplayMessage(): String = documentDisplayMessage(EditorBundle)
