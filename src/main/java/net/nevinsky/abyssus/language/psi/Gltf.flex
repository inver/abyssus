/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.language.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;

import static net.nevinsky.abyssus.language.psi.GltfTypes.*;

%%

%public
%class GltfLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

WHITE_SPACE=[\ \n\r\t\f]+
STRING=\"([^\"\\\r\n]|\\.)*\"
UNTERMINATED_STRING=\"([^\"\\\r\n]|\\.)*
NUMBER=-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+\-]?[0-9]+)?

%%

{WHITE_SPACE}          { return TokenType.WHITE_SPACE; }

"{"                    { return BRACE1; }
"}"                    { return BRACE2; }
"["                    { return BRACK1; }
"]"                    { return BRACK2; }
":"                    { return COLON; }
","                    { return COMMA; }
"true"                 { return TRUE; }
"false"                { return FALSE; }
"null"                 { return NULL; }

{STRING}               { return STRING; }
{NUMBER}               { return NUMBER; }

{UNTERMINATED_STRING}  { return TokenType.BAD_CHARACTER; }
[^]                    { return TokenType.BAD_CHARACTER; }
