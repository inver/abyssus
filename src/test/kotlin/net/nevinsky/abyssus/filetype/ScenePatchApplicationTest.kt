/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.filetype

import net.nevinsky.abyssus.editor.document.SceneJson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.security.MessageDigest

/**
 * Applies a staged edit of native documents (a scene, a project or an asset `meta.json`) through [editSceneJson], the
 * one writer of those files. A tool, not a check: it runs only with `ABYSSUS_SCENE_PATCH` set to a patch file, e.g.
 * `ABYSSUS_SCENE_PATCH=$PWD/games/control-line/importers/trainer.patch.json
 * ./gradlew :test --tests 'net.nevinsky.abyssus.filetype.ScenePatchApplicationTest'`.
 *
 * A patch is `{"documents": [{"target", "expectedSha256", "operations": [...]}]}`. `target` is relative to the patch
 * file's folder; each document is checked against its SHA-256 and edited as one undoable command. Operations:
 * - `{"op": "set", "path": [...], "value": v}`: sets the last key of `path` in the object the rest of `path` names;
 * - `{"op": "remove", "path": [...]}`: removes that key;
 * - `{"op": "removeEntitiesWithAssets", "prefixes": [...], "keep": [...]}`: removes every `ecs.entities` entry drawn
 *   with an asset whose name starts with a prefix, except the ids in `keep`.
 */
class ScenePatchApplicationTest : BasePlatformTestCase() {
    fun testApplyTheStagedPatch() {
        val patchFile = System.getenv("ABYSSUS_SCENE_PATCH")?.let(::File) ?: return
        val patch = SceneJson.parse(patchFile.readText())
        for (document in patch.get("documents")) {
            val target = File(patchFile.parentFile, document.get("target").asText()).canonicalFile
            val actual = MessageDigest.getInstance("SHA-256").digest(target.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("$target changed since the patch was staged", document.get("expectedSha256").asText(), actual)
            VfsRootAccess.allowRootAccess(testRootDisposable, target.parentFile.absolutePath)
            val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)!!
            val written = editSceneJson(project, file, "Apply Staged Patch") { root ->
                document.get("operations").forEach { apply(root, it) }
                true
            }
            assertTrue("editSceneJson wrote nothing to $target", written)
        }
    }

    private fun apply(root: JsonNode, operation: JsonNode) {
        when (val op = operation.get("op").asText()) {
            "set", "remove" -> {
                val path = operation.get("path").map { it.asText() }
                var parent = root
                for (key in path.dropLast(1)) parent = parent.get(key) ?: error("no '$key' on the way to $path")
                val obj = parent as ObjectNode
                if (op == "set") obj.set<JsonNode>(path.last(), operation.get("value")) else obj.remove(path.last())
            }
            "removeEntitiesWithAssets" -> {
                val prefixes = operation.get("prefixes").map { it.asText() }
                val keep = operation.path("keep").map { it.asText() }.toSet()
                val entities = root.get("ecs").get("entities") as ObjectNode
                entities.properties().filter { (id, entity) ->
                    id !in keep && entity.path("components").path("RenderComponent").path("renderable").path("asset")
                        .path("assetName").asText().let { name -> prefixes.any(name::startsWith) }
                }.map { it.key }.forEach(entities::remove)
            }
            else -> error("unknown operation '$op'")
        }
    }
}
