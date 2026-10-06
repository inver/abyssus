package net.nevinsky.abyssus.ui

import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.core.assets.displayMessage
import net.nevinsky.abyssus.format.UnsupportedDocumentFormat

fun Throwable.documentDisplayMessage(): String = when (this) {
    is UnsupportedDocumentFormat -> AbyssusBundle.message("unsupportedFormat.${reason.problem.name}", reason.path)
    else -> displayMessage()
}
