/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.filetype

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.gdx.editor.document.SceneJson
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.dto.ProjectSettings
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

    private val cache = ConcurrentHashMap<String, ProjectSettings>()
    private val listeners = project.messageBus.syncPublisher(ProjectSettingsListener.TOPIC)

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    val file = event.file
                    if (file != null && file.path.endsWith(".abss")) invalidate(file)
                }
            }
            override fun before(events: List<VFileEvent>) = Unit
        })
    }

    fun getSettings(abss: VirtualFile): ProjectSettings = cache.computeIfAbsent(abss.path) { read(abss) }

    fun setPhysicsEnabled(abss: VirtualFile, on: Boolean): Boolean {
        val commandName = if (on) AbyssusBundle.message("commandTurnPhysicsOn") else AbyssusBundle.message("commandTurnPhysicsOff")
        return editSceneJson(project, abss, commandName) { root ->
            if (root !is ObjectNode) return@editSceneJson false
            root.put("physicsEnabled", on)
            true
        }.also { if (it) invalidate(abss) }
    }

    private fun invalidate(abss: VirtualFile) {
        cache.remove(abss.path)
        val settings = getSettings(abss)
        listeners.settingsChanged(abss, settings)
    }

    private fun read(abss: VirtualFile): ProjectSettings = runCatchingKeepingCancellation {
        val text = String(abss.contentsToByteArray(), abss.charset)
        val root = SceneJson().parse(text)
        val physicsEnabled = root.get("physicsEnabled")?.let { it.isBoolean && it.asBoolean() } ?: false
        val problems = if (root.has("physicsEnabled") && !root.get("physicsEnabled")!!.isBoolean) {
            listOf("physicsEnabled")
        } else {
            emptyList<String>()
        }
        ProjectSettings(physicsEnabled, problems)
    }.getOrElse { ProjectSettings(false, listOf(it.message ?: it.toString())) }

    override fun dispose() = Unit
}
