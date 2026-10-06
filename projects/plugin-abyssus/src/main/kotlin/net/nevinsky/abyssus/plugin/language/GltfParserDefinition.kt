/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.language

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import net.nevinsky.abyssus.plugin.language.parser.GltfParser
import net.nevinsky.abyssus.plugin.language.psi.GltfFile
import net.nevinsky.abyssus.plugin.language.psi.GltfTypes

class GltfParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer {
        return GltfLexerAdapter()
    }

    override fun createParser(project: Project?): PsiParser {
        return GltfParser()
    }

    override fun getFileNodeType(): IFileElementType {
        return FILE
    }

    override fun getCommentTokens(): TokenSet {
        return TokenSet.EMPTY
    }

    override fun getStringLiteralElements(): TokenSet {
        return TokenSet.create(GltfTypes.STRING)
    }

    override fun getWhitespaceTokens(): TokenSet {
        return TokenSet.create(TokenType.WHITE_SPACE)
    }

    override fun createElement(node: ASTNode): PsiElement {
        return GltfTypes.Factory.createElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return GltfFile(viewProvider)
    }

    companion object {
        val FILE: IFileElementType = IFileElementType(GltfLanguage)
    }
}
