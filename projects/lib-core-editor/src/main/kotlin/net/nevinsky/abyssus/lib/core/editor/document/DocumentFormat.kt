package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.lib.core.format.FormatProblem
import net.nevinsky.abyssus.lib.core.format.FormatRejection
import net.nevinsky.abyssus.lib.core.format.UnsupportedDocumentFormat

// Source compatibility for editor callers; native admission belongs to the plain JVM core.
typealias DocumentKind = DocumentKind
typealias FormatProblem = FormatProblem
typealias FormatRejection = FormatRejection
typealias UnsupportedDocumentFormat = UnsupportedDocumentFormat
typealias AbyssusDocumentFormat = AbyssusDocumentFormat
