/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.messages.Topic
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectSettings
import net.nevinsky.abyssus.plugin.dto.ProjectSettingsReader
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import java.util.concurrent.ConcurrentHashMap

fun interface ProjectSettingsListener {
    fun settingsChanged(abss: VirtualFile, settings: ProjectSettings)

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Abyssus project settings", ProjectSettingsListener::class.java)
    }
}

@Service(Service.Level.PROJECT)
class AbyssusProjectSettings(private val project: Project) : Disposable {

    private data class Cached(val file: VirtualFile, val stamp: Long, val settings: ProjectSettings)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val watched = ConcurrentHashMap.newKeySet<VirtualFile>()
    private val reported = ConcurrentHashMap<String, List<String>>()
    private val reader = ProjectSettingsReader()
    private val listeners = project.messageBus.syncPublisher(ProjectSettingsListener.TOPIC)

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun before(events: List<VFileEvent>) {
                events.filter { it is VFileDeleteEvent || it is VFileMoveEvent ||
                    (it is VFilePropertyChangeEvent && it.propertyName == VirtualFile.PROP_NAME) }
                    .forEach { event -> event.file?.let { cache.remove(it.path); reported.remove(it.path) } }
            }
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    val file = event.file
                    if (file != null && file in watched) invalidate(file)
                }
            }
        })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (file in watched) invalidate(file)
            }
        }, this)
    }

    fun getSettings(abss: VirtualFile): ProjectSettings {
        watched.add(abss)
        return runReadAction {
            val stamp = FileDocumentManager.getInstance().getCachedDocument(abss)?.modificationStamp ?: abss.modificationStamp
            cache.compute(abss.path) { _, previous ->
                if (previous?.file === abss && previous.stamp == stamp) previous
                else Cached(abss, stamp, read(abss))
            }!!.settings
        }
    }

    fun setPhysicsEnabled(abss: VirtualFile, on: Boolean): Boolean {
        if (!getSettings(abss).supported) return false
        val commandName = if (on) AbyssusBundle.message("commandTurnPhysicsOn") else AbyssusBundle.message("commandTurnPhysicsOff")
        return editSceneJson(project, abss, commandName) { root ->
            if (root !is ObjectNode || root.get("physicsEnabled")?.let { it.isBoolean && it.booleanValue() == on } == true) return@editSceneJson false
            root.put("physicsEnabled", on)
            true
        }
    }

    private fun invalidate(abss: VirtualFile) {
        val previous = cache.remove(abss.path)?.settings
        val settings = getSettings(abss)
        if (previous == settings) return
        onEdt { listeners.settingsChanged(abss, settings) }
    }

    private fun read(abss: VirtualFile): ProjectSettings = runCatchingKeepingCancellation {
        val result = runReadAction {
            if (!abss.isValid) ProjectSettings(false, emptyList(), supported = false)
            else reader.read(FileDocumentManager.getInstance().getCachedDocument(abss)?.text
                ?: String(abss.contentsToByteArray(), abss.charset))
        }
        if (result.problems.isEmpty()) reported.remove(abss.path)
        else if (reported.put(abss.path, result.problems) != result.problems) onEdt {
            NotificationGroupManager.getInstance().getNotificationGroup("Abyssus Project Settings")
                .createNotification(AbyssusBundle.message("projectSettingsProblem", abss.name,
                    if (result.supported) AbyssusBundle.message("physicsEnabledNotBoolean") else result.problems.joinToString()),
                    NotificationType.WARNING).notify(project)
        }
        result
    }.getOrElse { ProjectSettings(false, listOf(it.message ?: it.toString()), supported = false) }

    private fun onEdt(action: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) { if (!project.isDisposed) action() }
        else app.invokeLater { if (!project.isDisposed) action() }
    }

    override fun dispose() = Unit
}
