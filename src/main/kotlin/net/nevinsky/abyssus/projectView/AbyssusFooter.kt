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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.AssetReadResult
import net.nevinsky.abyssus.dto.ProjectDto
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.scene.SceneDto
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

/** The numbers under the tree. */
data class FooterCounts(val scenes: Int, val assets: Int, val unused: Int)

/**
 * Scenes, assets and unused assets summed over every project in the view; a standalone scene counts as one scene.
 * Independent of the unused filter. Call in a read action.
 */
fun footerCounts(project: Project): FooterCounts {
    var scenes = 0
    var assets = 0
    var unused = 0
    for (file in findTopLevelAssets(project)) {
        when (val root = AssetReadCache.of(project).read(file)?.takeIf { it.success }?.obj) {
            is ProjectDto -> {
                scenes += root.scenes.size
                assets += root.assets.size
                unused += root.assets.count { it.unused }
            }
            is SceneDto -> scenes++
            else -> if (file.extension == ProjectLayout.SCENE_EXTENSION) scenes++
        }
    }
    return FooterCounts(scenes, assets, unused)
}

/** `N scenes · N assets · N unused` strip under the tree; recounts when scene, project or `meta.json` files change. */
class AbyssusFooter(private val project: Project, private val parent: Disposable) : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(16), JBUI.scale(6))) {
    private val scenes = JBLabel()
    private val assets = JBLabel()
    private val unused = JBLabel().apply { foreground = AMBER }

    @Volatile
    private var disposed = false

    /** The counts currently shown; null until the first count arrives. */
    var counts: FooterCounts? = null
        private set

    init {
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
        listOf(scenes, assets, unused).forEach { it.font = JBUI.Fonts.smallFont(); add(it) }
        scenes.foreground = SECONDARY
        assets.foreground = SECONDARY
        com.intellij.openapi.util.Disposer.register(parent, Disposable { disposed = true })
        project.messageBus.connect(parent).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { relevant(it) }) refresh()
            }
        })
        refresh()
    }

    private fun relevant(event: VFileEvent): Boolean {
        val name = event.path.substringAfterLast('/')
        return name == ProjectLayout.META_FILE || name.substringAfterLast('.', "") in ProjectLayout.ASSET_EXTENSIONS ||
            event.file?.isDirectory == true || event.file == null
    }

    /** Recounts in the background and shows the result; later requests replace earlier ones. */
    fun refresh() {
        ReadAction.nonBlocking<FooterCounts> { footerCounts(project) }
            .expireWith(parent)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { if (!disposed) show(it) }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    internal fun show(c: FooterCounts) {
        counts = c
        scenes.text = AbyssusBundle.message("footerScenes", c.scenes)
        assets.text = AbyssusBundle.message("footerAssets", c.assets)
        unused.text = AbyssusBundle.message("footerUnused", c.unused)
    }

    /** The texts shown, for tests. */
    internal fun texts() = listOf(scenes.text, assets.text, unused.text)

    private companion object {
        val SECONDARY: Color = JBColor.GRAY
        val AMBER: Color = JBColor(Color(0xB07A1F), Color(0xE0A458))
    }
}
