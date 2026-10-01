package net.nevinsky.abyssus.language;

import com.intellij.lexer.FlexAdapter;
import net.nevinsky.abyssus.language.lexer.GltfLexer;

public class GltfLexerAdapter extends FlexAdapter {

    public GltfLexerAdapter() {
        super(new GltfLexer(null));
    }
}
