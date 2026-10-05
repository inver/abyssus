/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.sceneview

import com.fasterxml.jackson.databind.JsonNode
import net.nevinsky.abyssus.core.assets.terrain.SPLAT_FIELDS
import net.nevinsky.abyssus.dto.AssetMetaReader
import net.nevinsky.abyssus.core.assets.MetaType
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.terrain.sha256Hex
import net.nevinsky.abyssus.core.AbyssusProjectLayout.Companion.META_FILE
import net.nevinsky.abyssus.runtime.obj
import net.nevinsky.abyssus.runtime.text
import java.io.File
import kotlin.collections.iterator

/** Reads a `meta.json` file as text; null when there is none. The default reads the file from disk. */
fun interface MetaTextSource {
    fun read(metaFile: File): String?
}

/** The default [MetaTextSource]: the file's content on disk. */
class DiskMetaText : MetaTextSource {
    override fun read(metaFile: File): String? = metaFile.takeIf { it.isFile }?.readText()
}

/** The revisions of every asset folder of a project at one moment, by folder name. */
data class ProjectRevisions(val assets: Map<String, AssetRevision>)

/**
 * Everything about one asset folder that can change what it draws: the hash of its `meta.json` text, the stamps of the
 * files that text names ([files], by name; null for a file that is missing), and for a terrain the asset folder each
 * splat field resolves to through its texture's `uuid` ([references], null where nothing resolves).
 */
data class AssetRevision(
    val name: String,
    val uuid: String?,
    val type: String?,
    val metaHash: String?,
    val files: Map<String, FileStamp?>,
    val references: Map<String, String?>,
)

/** What a file looked like when a snapshot was taken; a replaced file changes at least one of them. */
data class FileStamp(val length: Long, val lastModified: Long)

/**
 * Takes [ProjectRevisions] snapshots and says which assets differ between two of them. A terrain also differs when a
 * texture it references changes (its metadata or its image), and, conservatively, whenever the folder one of its splat
 * `uuid`s resolves to changes (a texture added, removed or given another `uuid`), so a late or repaired texture shows
 * up. Assets that did not change and reference nothing that changed are not reported: their drawables are kept.
 */
class AssetRevisionTracker(private val json: JsonProcessor) {
    /** A snapshot of the assets under [assetsDir], reading each `meta.json` through [metaText] (unsaved text, say). */
    fun snapshot(assetsDir: File, metaText: MetaTextSource = DiskMetaText()): ProjectRevisions {
        val folders = assetsDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        val parsed = folders.map { dir ->
            val text = runCatchingKeepingCancellation { metaText.read(File(dir, META_FILE)) }.getOrNull()
            val tree = text?.let { runCatchingKeepingCancellation { AssetMetaReader(json).read(it).json }.getOrNull() }
            Triple(dir, text, tree)
        }
        val byUuid = buildMap { for ((dir, _, tree) in parsed) tree?.text("uuid")?.let { putIfAbsent(it, dir.name) } }
        val assets = parsed.associate { (dir, text, tree) ->
            dir.name to AssetRevision(
                name = dir.name,
                uuid = tree?.text("uuid"),
                type = tree?.text("type"),
                metaHash = text?.let { sha256Hex(it.toByteArray()) },
                files = tree?.let { stamps(dir, it) }.orEmpty(),
                references = if (tree?.text("type") == MetaType.TERRAIN.name) references(tree, byUuid) else emptyMap(),
            )
        }
        return ProjectRevisions(assets)
    }

    /** The files a `meta.json` names directly in its `additional` text values that are files of the folder. */
    private fun stamps(dir: File, tree: JsonNode): Map<String, FileStamp?> {
        val additional = tree.obj("additional") ?: return emptyMap()
        return additional.fields().asSequence()
            .filter { (key, value) -> value.isTextual && key !in SPLAT_FIELDS && value.asText().isNotBlank() }
            .map { (_, value) -> value.asText() }
            .distinct().sorted()
            .associateWith { name ->
                File(dir, name).takeIf { it.isFile }?.let { FileStamp(it.length(), it.lastModified()) }
            }
    }

    private fun references(tree: JsonNode, byUuid: Map<String, String>): Map<String, String?> {
        val additional = tree.obj("additional") ?: return emptyMap()
        return SPLAT_FIELDS.mapNotNull { field -> additional.text(field)?.let { field to byUuid[it] } }.toMap()
    }

    /** The folder names of assets that must be loaded again going from [old] to [new]. */
    fun changed(old: ProjectRevisions, new: ProjectRevisions): Set<String> {
        val direct = buildSet {
            for ((name, revision) in new.assets) if (old.assets[name] != revision) add(name)
            for (name in old.assets.keys) if (name !in new.assets) add(name)
        }
        val dependents = new.assets.values
            .filter { it.name !in direct && it.references.values.any { folder -> folder != null && folder in direct } }
            .map { it.name }
        return direct + dependents
    }
}
