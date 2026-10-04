/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.runtime

import net.nevinsky.abyssus.assets.ASSETS_DIR
import net.nevinsky.abyssus.assets.AssetLog
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.runtime.ecs.EcsConfigurator
import net.nevinsky.abyssus.runtime.ecs.LoadedScene
import net.nevinsky.abyssus.runtime.ecs.render.FolderAssetResolver
import net.nevinsky.abyssus.runtime.project.ProjectFolder
import net.nevinsky.abyssus.runtime.project.ProjectInfo
import net.nevinsky.abyssus.runtime.scene.SceneDto
import net.nevinsky.abyssus.runtime.scene.SceneParser
import java.nio.file.Files
import java.nio.file.Path

/** Reads projects and creates independent scene engines without editor or GL services. */
class SceneLoading(private val json: JsonProcessor, private val log: AssetLog, private val format: net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat = net.nevinsky.abyssus.assets.format.AbyssusDocumentFormat()) {
    private val parser = SceneParser(json, format)

    /** A missing project has no result; an unreadable project also reports its cause. */
    fun project(dir: Path): ProjectInfo? {
        val folder = ProjectFolder(dir)
        val abss = reported(dir.toString()) { folder.abss() } ?: return null
        return reported(abss.toString()) {
            ProjectInfo(projectName(Files.readString(abss)) ?: abss.fileName.toString().substringBeforeLast('.'), folder.sceneFiles())
        }
    }

    fun projectName(text: String): String? {
        val root = json.readObject(text)
        format.requireSupported(root, net.nevinsky.abyssus.assets.format.DocumentKind.PROJECT)
        return json.bind(root, ProjectName::class.java).name
    }
    fun projectName(source: String, readText: () -> String): String? =
        reportedOrThrow("project $source") { projectName(readText()) }
    fun parse(text: String): SceneDto = parser.parse(text)

    /** Editor adapter: keep the parsing failure available to its error row and report the source once. */
    fun parse(text: String, source: String): SceneDto = parse(source) { text }

    /** Includes failures obtaining text from an editor's filesystem in the same logging boundary. */
    fun parse(source: String, readText: () -> String): SceneDto =
        reportedOrThrow("scene $source") { parse(readText()) }

    /** Failures are logged once and returned as null, allowing other scene files to load. */
    fun load(scene: Path): LoadedScene? = reported(scene.toString()) {
        val path = scene.toAbsolutePath()
        loadParsed(Files.readString(path), path.parent.parent, AssetLog { message, error -> log.warn("$scene: $message", error) })
    }

    /** Uses caller-supplied text, including unsaved edits; never reads or writes a scene file. */
    fun load(text: String, projectDir: Path): LoadedScene? = reported(projectDir.toString()) {
        loadParsed(text, projectDir)
    }

    private fun loadParsed(text: String, dir: Path, sceneLog: AssetLog = log): LoadedScene {
        val scene = parse(text)
        val assets = dir.resolve(ASSETS_DIR)
        val names = if (Files.isDirectory(assets)) Files.list(assets).use { paths ->
            paths.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.toList()
        } else emptyList()
        val loaded = EcsConfigurator(FolderAssetResolver(names), sceneLog).load(scene.ecs ?: json.readObject("{}"))
        return LoadedScene(loaded.engine, loaded.document, scene)
    }

    private fun <T> reported(source: String, read: () -> T?): T? = runCatchingKeepingCancellation(read)
        .getOrElse { error -> log.warn("Could not load $source: ${error.message}", error); null }

    private fun <T> reportedOrThrow(source: String, read: () -> T): T = runCatchingKeepingCancellation(read)
        .getOrElse { error -> log.warn("Could not read $source: ${error.message}", error); throw error }
}

private data class ProjectName(val name: String? = null)
