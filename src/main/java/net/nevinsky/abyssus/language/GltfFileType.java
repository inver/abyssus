package net.nevinsky.abyssus.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GltfFileType extends LanguageFileType {

    public static final GltfFileType INSTANCE = new GltfFileType();

    private GltfFileType() {
        super(GltfLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Gltf File";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Gltf file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "gltf";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return GltfIcons.FILE;
    }

}
