package net.nevinsky.abyssus.language;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import net.nevinsky.abyssus.language.parser.GltfParser;
import net.nevinsky.abyssus.language.psi.GltfFile;
import net.nevinsky.abyssus.language.psi.GltfTypes;
import org.jetbrains.annotations.NotNull;

public class GltfParserDefinition implements ParserDefinition {

    public static final IFileElementType FILE = new IFileElementType(GltfLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new GltfLexerAdapter();
    }

    @NotNull
    @Override
    public PsiParser createParser(Project project) {
        return new GltfParser();
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return TokenSet.EMPTY;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return TokenSet.create(GltfTypes.STRING);
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return TokenSet.create(TokenType.WHITE_SPACE);
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return GltfTypes.Factory.createElement(node);
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new GltfFile(viewProvider);
    }
}
