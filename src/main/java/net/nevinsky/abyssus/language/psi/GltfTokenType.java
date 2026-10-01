package net.nevinsky.abyssus.language.psi;

import com.intellij.psi.tree.IElementType;
import net.nevinsky.abyssus.language.GltfLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class GltfTokenType extends IElementType {

    public GltfTokenType(@NotNull @NonNls String debugName) {
        super(debugName, GltfLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "GltfTokenType." + super.toString();
    }

}