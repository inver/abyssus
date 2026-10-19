package net.nevinsky.abyssus.plugin.projectView.scene

import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.core.format.DocumentKind
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneEntry
import net.nevinsky.abyssus.plugin.dto.textOf
import net.nevinsky.abyssus.plugin.projectView.DtoEntryNode

internal val SCENE_MENU_ACTION_IDS = listOf("Abyssus.RenameScene", "Abyssus.Menu.Add")


fun sceneNodeProjectFile(node: Any?): VirtualFile? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    if (entry.value !is SceneEntry) {
        return null
    }
    return entry.source?.takeIf {
        it.isValid && it.extension == ProjectLayout.PROJECT_EXTENSION &&
                runCatchingKeepingCancellation {
                    AbyssusDocumentFormat().validate(
                        SceneJson().parse(textOf(it)),
                        DocumentKind.PROJECT,
                    ) == null
                }.getOrDefault(false)
    }
}
