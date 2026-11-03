/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.ui.tree.TreeVisitor
import net.nevinsky.abyssus.lib.core.editor.refresh.NavigationState
import net.nevinsky.abyssus.lib.core.editor.refresh.RefreshCoalescer
import net.nevinsky.abyssus.lib.core.editor.refresh.restoreNavigation
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout
import net.nevinsky.abyssus.plugin.dto.NativeFileChanges
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.filetype.AbyssusSceneEdited
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * Keeps the Abyssus tree current: plugin edits ([AbyssusSceneEdited]), document changes (typing, Undo, Redo, unsaved text)
 * and the IDE's VFS create/delete/move/rename/content events for native documents and asset metadata of this project each
 * mark the tree stale. Marks coalesce in a [RefreshCoalescer], so a burst rebuilds once and an event during a rebuild
 * triggers one more. A rebuild never writes a file or opens an undo entry: it re-reads through the reader caches, which
 * prefer unsaved text, and then restores selection and expansion by row identity.
 *
 * Identities are captured on the EDT before the rebuild; the platform builds the children in the background under read
 * access; the UI update and the restoration run on the EDT. The coordinator lives as long as the pane: disposal cancels
 * pending work, and a run that finds the project closed or the tree gone does nothing, so a closed project is never reopened.
 */
internal class AbyssusTreeRefresh(
    private val project: Project,
    private val tree: () -> JTree?,
    /** Rebuilds the tree from its sources and calls the function when it is done. */
    private val rebuild: ((done: () -> Unit) -> Unit),
    parent: Disposable,
    private val onEdt: (Runnable) -> Unit = { run ->
        ApplicationManager.getApplication().invokeLater(run, ModalityState.nonModal(), project.disposed)
    },
) : Disposable {
    private val coalescer = RefreshCoalescer()

    /** A row to select instead of the captured selection when the pending rebuild restores the tree. EDT only. */
    private var selectAfter: TreeVisitor? = null

    /** When an idle-time [selectAfter] request lapses, so it cannot redirect an unrelated later rebuild. */
    private var selectUntil = Long.MAX_VALUE

    /**
     * Asks for [visitor]'s row to be selected once the rebuild that is pending or running has restored the tree, and
     * returns true. A plugin edit publishes [AbyssusSceneEdited] and the rebuild follows later on the EDT, so selecting
     * the row an edit just created right away would look for it in the old tree, find nothing, and then lose to the
     * selection the rebuild restores. Returns false, doing nothing, when no rebuild is pending: select directly then.
     * With [evenIfIdle], the request is kept for a short window even when none is pending: asset creation writes files
     * whose VFS events start a rebuild after the direct selection ran against a tree that did not have the row yet.
     */
    fun selectAfterRebuild(visitor: TreeVisitor, evenIfIdle: Boolean = false): Boolean {
        if (idle && !evenIfIdle) return false
        selectAfter = visitor
        selectUntil = System.nanoTime() + SELECT_AFTER_WINDOW_NANOS
        return true
    }

    /** How many rebuilds this coordinator started; tests read it to tell a rebuild from none. */
    @Volatile
    var rebuilds: Int = 0
        private set

    init {
        Disposer.register(parent, this)
        val connection = project.messageBus.connect(this)
        connection.subscribe(AbyssusSceneEdited.TOPIC, AbyssusSceneEdited { file -> changed(file.path) })
        project.getService(NativeFileChanges::class.java).subscribe(this) { events ->
            for (event in events) {
                if (isRelevant(event)) changed(event.path)
            }
        }
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (inProject(file.path) && isTreePath(file.path, directory = false)) changed(file.path)
            }
        }, this)
    }

    /** True when no rebuild is scheduled or running. */
    val idle: Boolean get() = !coalescer.pending

    /** Marks the tree stale because [key] changed, from any thread. */
    fun changed(key: String) {
        if (coalescer.mark(key)) onEdt { run() }
    }

    private fun isRelevant(event: VFileEvent): Boolean {
        if (event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME) {
            val renamed = listOf(event.oldValue, event.newValue).filterIsInstance<String>()
            val parent = event.path.substringBeforeLast('/', "")
            return inProject(event.path) && renamed.any { isTreePath("$parent/$it", event.file.isDirectory) }
        }
        return inProject(event.path) && isTreePath(event.path, event.file?.isDirectory == true)
    }

    private fun inProject(path: String): Boolean =
        ProjectRootManager.getInstance(project).contentRoots.any { path == it.path || path.startsWith(it.path + "/") }

    private fun run() {
        val keys = coalescer.begin() ?: return
        val tree = tree()
        if (keys.isEmpty() || project.isDisposed || tree == null) {
            selectAfter = null
            finishRun()
            return
        }
        rebuilds++
        val state = capture(tree)
        rebuild { onEdt { restore(tree, state); finishRun() } }
    }

    private fun finishRun() {
        if (coalescer.finish()) onEdt { run() }
    }

    private fun capture(tree: JTree): NavigationState {
        val selected = tree.selectionPaths.orEmpty().mapNotNull { keyOf(it) }
        val expanded = TreeUtil.collectExpandedPaths(tree).mapNotNull { keyOf(it) }
        return NavigationState(selected, expanded)
    }

    /**
     * Opens the captured rows that survived, shallow first, then selects the surviving selected rows. A selected row that is
     * gone leaves no selection (the platform may have moved it onto the row's parent), so no stale details stay shown.
     */
    private fun restore(tree: JTree, state: NavigationState) {
        if (project.isDisposed) return
        val found = LinkedHashSet<String>()
        fun step(index: Int, order: List<String>) {
            if (index == order.size) {
                select(tree, state, found)
                return
            }
            val key = order[index]
            TreeUtil.promiseExpand(tree, TreeVisitor { path -> visitStep(key, path) })
                .onProcessed { if (it != null) found += key; step(index + 1, order) }
        }
        step(0, restoreNavigation(state, state.expanded.toSet()).expand)
    }

    private fun select(tree: JTree, state: NavigationState, found: MutableSet<String>) {
        selectAfter?.takeIf { System.nanoTime() <= selectUntil }?.let { wanted ->
            selectAfter = null
            // a row that cannot be found (the entity is gone again) falls back to what the rebuild would have restored
            TreeUtil.promiseSelect(tree, wanted).onError { if (!project.isDisposed) selectCaptured(tree, state, found) }
            return
        }
        selectCaptured(tree, state, found)
    }

    private fun selectCaptured(tree: JTree, state: NavigationState, found: MutableSet<String>) {
        val restore = restoreNavigation(state, found + state.selected.toSet())
        if (restore.select.isEmpty()) {
            if (state.selected.isNotEmpty()) tree.clearSelection()
            return
        }
        val visitors = restore.select.map { key -> TreeVisitor { path -> visitStep(key, path) } }
        TreeUtil.promiseSelect(tree, visitors.stream()).onError {
            // none of the selected rows survived the rebuild
            if (!project.isDisposed) tree.clearSelection()
        }
    }

    private fun visitStep(key: String, path: TreePath): TreeVisitor.Action {
        val node = TreeUtil.getUserObject(path.lastPathComponent)
        val entry = (node as? DtoEntryNode)?.value?.path
        val asset = (node as? AbyssusAssetNode)?.virtualFile?.path
        return when (reselectStep(key, entry, asset)) {
            ReselectStep.FOUND -> TreeVisitor.Action.INTERRUPT
            ReselectStep.DESCEND -> TreeVisitor.Action.CONTINUE
            ReselectStep.SKIP -> TreeVisitor.Action.SKIP_CHILDREN
        }
    }

    override fun dispose() = coalescer.dispose()

    companion object {
        private const val SELECT_AFTER_WINDOW_NANOS = 5_000_000_000L

        /** The identity of the row [path] ends at: a DTO entry's path or an asset folder's path; null for the view root. */
        fun keyOf(path: TreePath): String? = when (val node = TreeUtil.getUserObject(path.lastPathComponent)) {
            is DtoEntryNode -> node.value.path
            is AbyssusAssetNode -> node.virtualFile.path
            else -> null
        }

        /**
         * True when a change at [path] can change what the tree shows: a native document (`.scene`, `.abss`), an asset's
         * `meta.json`, or a folder created, deleted, moved or renamed under the project's `assets` folder.
         */
        fun isTreePath(path: String, directory: Boolean): Boolean {
            val name = path.substringAfterLast('/')
            if (!directory) {
                return net.nevinsky.abyssus.plugin.dto.isNativeDocumentPath(path)
            }
            val assets = AbyssusProjectLayout.ASSETS_DIR
            return name == assets || path.contains("/$assets/")
        }
    }
}
