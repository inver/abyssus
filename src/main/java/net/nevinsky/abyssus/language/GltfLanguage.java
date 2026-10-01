package net.nevinsky.abyssus.language;

import com.intellij.lang.Language;

public class GltfLanguage extends Language {

    public static final GltfLanguage INSTANCE = new GltfLanguage();

    protected GltfLanguage() {
        super("GLTF");
    }
}
