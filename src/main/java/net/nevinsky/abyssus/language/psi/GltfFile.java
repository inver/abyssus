package net.nevinsky.abyssus.language.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import net.nevinsky.abyssus.language.GltfFileType;
import net.nevinsky.abyssus.language.GltfLanguage;
import org.jetbrains.annotations.NotNull;

public class GltfFile extends PsiFileBase {

    public GltfFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, GltfLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return GltfFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Gltf File";
    }
}
