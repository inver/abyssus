/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.schema

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.dto.ProjectLayout
import net.nevinsky.abyssus.dto.textOf
import net.nevinsky.abyssus.ecs.scene.ComponentEditor
import net.nevinsky.abyssus.filetype.documentDisplayMessage
import net.nevinsky.abyssus.runtime.schema.SCHEMA_FILE
import java.util.concurrent.ConcurrentHashMap

/** Told after the component schemas of a project changed (a schema file or a contributing plugin). */
fun interface ComponentSchemasListener {
    fun schemasChanged()

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic.create("Abyssus component schemas", ComponentSchemasListener::class.java)
    }
}

/**
 * The component schemas of a project's scenes: the contributions of `componentSchemas` merged with the
 * `abyssus/components.schema.json` next to the scene's `.abss`, the project winning per short name ([SchemaMerge]).
 * Snapshots are cached per Abyssus project folder and dropped when a schema file changes or a contributing plugin loads
 * or unloads; then [ComponentSchemasListener.TOPIC] is published, and the next reader (the Properties panel reads in
 * the background) parses again. Problems are reported once each as a notification.
 */
@Service(Service.Level.PROJECT)
class ComponentSchemas(private val project: Project) : Disposable {
    private val merge = SchemaMerge()
    private val snapshots = ConcurrentHashMap<String, SchemaSnapshot>()
    private val noProject = "" // the key for scenes outside an Abyssus project
    @Volatile private var contributions: List<ContributedSchemaText>? = null
    private val reported = ConcurrentHashMap.newKeySet<String>()

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { isSchemaPath(it.path) || holdsCached(it.path) }) changed(contributionsToo = false)
            }
        })
        COMPONENT_SCHEMAS_EP.addExtensionPointListener(object : ExtensionPointListener<ComponentSchemaBean> {
            override fun extensionAdded(extension: ComponentSchemaBean, pluginDescriptor: PluginDescriptor) = changed(contributionsToo = true)
            override fun extensionRemoved(extension: ComponentSchemaBean, pluginDescriptor: PluginDescriptor) = changed(contributionsToo = true)
        }, this)
    }

    /** A cached project folder is [path] or inside it (a project moved or deleted). */
    private fun holdsCached(path: String) = snapshots.keys.any { it.isNotEmpty() && (it == path || it.startsWith("$path/")) }

    private fun isSchemaPath(path: String) = path.endsWith("/$SCHEMA_FILE") || path.endsWith("/${SCHEMA_FILE.substringBefore('/')}")

    /** The schemas in force for [sceneFile]'s project (only the contributed ones for a scene outside a project). */
    fun snapshotFor(sceneFile: VirtualFile): SchemaSnapshot {
        val dir = ProjectLayout.abssFor(sceneFile)?.parent
        return snapshots.computeIfAbsent(dir?.path ?: noProject) { read(dir) }.also(::report)
    }

    /** The component editor for [sceneFile]'s scene. */
    fun editorFor(sceneFile: VirtualFile): ComponentEditor = snapshotFor(sceneFile).editor

    private fun read(dir: VirtualFile?): SchemaSnapshot {
        val projectText = dir?.let { d ->
            val file = d.findFileByRelativePath(SCHEMA_FILE) ?: return@let null
            val path = file.path.removePrefix(project.basePath.orEmpty()).removePrefix("/")
            runCatchingKeepingCancellation { ProjectSchemaText(path, runReadAction { textOf(file) }) }
                .getOrElse { ProjectSchemaText(path, null, it.documentDisplayMessage()) }
        }
        return merge.merge(projectText, contributions())
    }

    private fun contributions(): List<ContributedSchemaText> = contributions ?: COMPONENT_SCHEMAS_EP.extensionList
        .map { ContributedSchemaText(it.pluginName, it.resource, runCatchingKeepingCancellation { it.text() }.getOrNull()) }
        .also { contributions = it }

    private fun report(snapshot: SchemaSnapshot) {
        val fresh = snapshot.problems.filter(reported::add)
        if (fresh.isEmpty() || project.isDisposed) return
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Abyssus Component Schemas") ?: return
        group.createNotification(AbyssusBundle.message("schemaNotificationTitle"), fresh.joinToString("<br>"), NotificationType.WARNING)
            .notify(project)
    }

    /** Drops the cached snapshots (and the contributions when [contributionsToo]) and tells the listeners. */
    private fun changed(contributionsToo: Boolean) {
        if (contributionsToo) contributions = null
        snapshots.clear()
        // a fixed file may break again with the same message, which is then worth saying again
        reported.clear()
        if (!project.isDisposed) project.messageBus.syncPublisher(ComponentSchemasListener.TOPIC).schemasChanged()
    }

    /** Re-reads every schema on the next request; for tests and callers that changed files without VFS events. */
    fun reload() = changed(contributionsToo = true)

    override fun dispose() = Unit

    companion object {
        fun of(project: Project): ComponentSchemas = project.service()
    }
}
