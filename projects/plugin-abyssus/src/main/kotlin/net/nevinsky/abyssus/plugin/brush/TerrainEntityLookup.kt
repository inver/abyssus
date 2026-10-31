/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.brush

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.lib.core.assets.BaseMeta
import net.nevinsky.abyssus.lib.core.editor.document.SceneDocument
import net.nevinsky.abyssus.lib.core.io.AbyssusProjectLayout.Companion.ASSETS_DIR
import net.nevinsky.abyssus.plugin.dto.ProjectLayout
import net.nevinsky.abyssus.plugin.dto.SceneDocumentCache

/** A terrain entity of a scene with the project files around it: its [document], its [terrain] asset, the `.abss` and the `assets` folder. */
class TerrainEntity(val document: SceneDocument, val terrain: BaseMeta, val abss: VirtualFile, val assets: VirtualFile) {
    /** The folder `assets/[name]`, or null when there is none. */
    fun assetFolder(name: String): VirtualFile? = assets.findChild(name)
}

/** The terrain [entity] of [scene], or null when it is not a terrain or the scene sits in no project with an `assets` folder. */
fun resolveTerrainEntity(project: Project, scene: VirtualFile, entity: String): TerrainEntity? {
    val document = SceneDocumentCache.of(project).read(scene)?.root?.let(::SceneDocument) ?: return null
    val terrain = document.BaseMeta(entity)?.takeIf { it.isTerrain() } ?: return null
    val abss = ProjectLayout.abssFor(scene) ?: return null
    val assets = abss.parent?.findChild(ASSETS_DIR) ?: return null
    return TerrainEntity(document, terrain, abss, assets)
}
