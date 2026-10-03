/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.language

import com.intellij.lexer.FlexAdapter
import net.nevinsky.abyssus.language.lexer.GltfLexer

class GltfLexerAdapter : FlexAdapter(GltfLexer(null))
