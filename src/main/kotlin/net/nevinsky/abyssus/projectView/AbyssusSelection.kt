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

package net.nevinsky.abyssus.projectView

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.assets.files.Asset
import net.nevinsky.abyssus.dto.ProjectLayout

fun interface AbyssusSelectionListener {
    /** [node] is the user object of the node now selected in the Abyssus view, or null when nothing is. */
    fun selectionChanged(node: Any?)

    companion object {
        @JvmField
        val TOPIC: Topic<AbyssusSelectionListener> = Topic.create("Abyssus view selection", AbyssusSelectionListener::class.java)
    }
}

/** The node selected in the Abyssus view, kept so a panel opened after the selection still knows it. */
@Service(Service.Level.PROJECT)
class AbyssusSelection(private val project: Project) {
    @Volatile
    var current: Any? = null
        private set

    fun select(node: Any?) {
        current = node
        if (!project.isDisposed) project.messageBus.syncPublisher(AbyssusSelectionListener.TOPIC).selectionChanged(node)
    }

    companion object {
        fun of(project: Project): AbyssusSelection = project.getService(AbyssusSelection::class.java)
    }
}

/** The asset folder behind a selected asset row (an [Asset] entry under a project's `assets`), else null. */
fun assetFolderOf(node: Any?): VirtualFile? {
    val entry = (node as? DtoEntryNode)?.value ?: return null
    val info = entry.value as? Asset<*> ?: return null
    val abss = entry.source?.takeIf { it.isValid && it.extension == ProjectLayout.PROJECT_EXTENSION } ?: return null
    return ProjectLayout.assetFolders(abss).firstOrNull { it.name == info.name }
}

/** The name and kind (`a scene`, `the project file`, `an entity or setting`) of a selected node that is not an asset; null for no node. */
fun describeNonAsset(node: Any?): Pair<String, String>? = when (node) {
    is AbyssusAssetNode -> if (!node.virtualFile.isValid) null else
        node.virtualFile.name to AbyssusBundle.message(if (node.virtualFile.extension == ProjectLayout.PROJECT_EXTENSION) "propertiesKindProject" else "propertiesKindScene")
    is DtoEntryNode ->
        (node.label ?: node.value.name) to AbyssusBundle.message(if (sceneFileOf(node.value) != null) "propertiesKindScene" else "propertiesKindOther")
    else -> null
}
