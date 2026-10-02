/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
%{
  // referenced by reset() in idea-flex.skeleton but not declared there
  private boolean zzAtBOL = true;
%}

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
