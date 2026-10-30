/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.assets.ui

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.ui.model.*
import java.io.File
import javax.imageio.ImageIO

enum class UiResourceProblem {
    NETWORK, ESCAPES_ASSET, MISSING, NOT_A_FILE, UNSUPPORTED_FORMAT, UNREADABLE, TOO_LARGE, FONT_PAGE_MISSING,
}

data class UiResourceIssue(val resourceId: String, val problem: UiResourceProblem, val detail: String? = null)

sealed interface UiFileLookup {
    data class Found(val file: File) : UiFileLookup
    data class Rejected(val problem: UiResourceProblem) : UiFileLookup
}

/**
 * Confines resource paths to the owning UI asset folder: relative paths only, no network schemes, and neither
 * normalization (`..`) nor a symbolic link may lead outside it. IO on the caller's thread.
 */
class UiResourceResolver(assetDir: File) {
    private val root = assetDir.toPath().toAbsolutePath().normalize()
    private val realRoot = runCatchingKeepingCancellation { assetDir.toPath().toRealPath() }.getOrDefault(root)

    /** [path] relative to the asset folder, or relative to [base] (a folder inside it) when given. */
    fun resolve(path: String, base: File? = null): UiFileLookup {
        if (path.isBlank()) return UiFileLookup.Rejected(UiResourceProblem.MISSING)
        if (path.contains("://") || path.startsWith("//") || path.startsWith("\\\\")) return UiFileLookup.Rejected(UiResourceProblem.NETWORK)
        val start = base?.toPath()?.toAbsolutePath()?.normalize() ?: root
        val candidate = runCatchingKeepingCancellation { start.resolve(path).normalize() }.getOrNull()
            ?: return UiFileLookup.Rejected(UiResourceProblem.ESCAPES_ASSET)
        if (File(path).isAbsolute || !candidate.startsWith(root)) return UiFileLookup.Rejected(UiResourceProblem.ESCAPES_ASSET)
        if (!java.nio.file.Files.exists(candidate)) return UiFileLookup.Rejected(UiResourceProblem.MISSING)
        val real = runCatchingKeepingCancellation { candidate.toRealPath() }.getOrNull()
            ?: return UiFileLookup.Rejected(UiResourceProblem.UNREADABLE)
        if (!real.startsWith(realRoot)) return UiFileLookup.Rejected(UiResourceProblem.ESCAPES_ASSET)
        if (!java.nio.file.Files.isRegularFile(real)) return UiFileLookup.Rejected(UiResourceProblem.NOT_A_FILE)
        return UiFileLookup.Found(candidate.toFile())
    }

    /** [file] expressed relative to the asset folder. */
    fun relative(file: File): String = root.relativize(file.toPath().toAbsolutePath().normalize()).toString()
}

/** Reads image dimensions from the header, without decoding the pixels. PNG and JPEG only. */
fun probeImageSize(file: File): Pair<Int, Int>? {
    val ext = file.extension.lowercase()
    if (ext != "png" && ext != "jpg" && ext != "jpeg") return null
    return runCatchingKeepingCancellation {
        ImageIO.createImageInputStream(file).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return@use null
            val reader = readers.next()
            try {
                reader.input = input
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()
}

sealed interface UiPreparedResource {
    val id: String

    data class Image(override val id: String, val file: File, val width: Int, val height: Int) : UiPreparedResource
    data class Font(override val id: String, val fnt: File, val pages: List<File>) : UiPreparedResource
    data class Failed(override val id: String, val problem: UiResourceProblem, val detail: String? = null) : UiPreparedResource
}

class UiPreparedResources(val resources: Map<String, UiPreparedResource>) {
    operator fun get(id: String): UiPreparedResource? = resources[id]
    val issues: List<UiResourceIssue>
        get() = resources.values.filterIsInstance<UiPreparedResource.Failed>().map { UiResourceIssue(it.id, it.problem, it.detail) }
}

/**
 * The CPU-only prepare step: validates every declared resource of a UI asset (confinement, format, size limits, font
 * pages) so it may run off the GL thread. One bad resource never affects another; it is reported and the widget falls
 * back to a placeholder or the default font.
 */
class UiResourcePreparer(private val resolver: UiResourceResolver) {
    fun prepare(collection: UiCollection): UiPreparedResources =
        UiPreparedResources(collection.resources.associate { it.id to prepare(it) })

    private fun prepare(resource: UiResource): UiPreparedResource = when (resource.kind) {
        UiResourceKind.IMAGE -> image(resource.id, resolver.resolve(resource.path))
        UiResourceKind.BITMAP_FONT -> font(resource)
    }

    private fun image(id: String, lookup: UiFileLookup): UiPreparedResource = when (lookup) {
        is UiFileLookup.Rejected -> UiPreparedResource.Failed(id, lookup.problem)
        is UiFileLookup.Found -> {
            val size = probeImageSize(lookup.file)
            when {
                size == null -> UiPreparedResource.Failed(id, if (lookup.file.extension.lowercase() in setOf("png", "jpg", "jpeg")) UiResourceProblem.UNREADABLE else UiResourceProblem.UNSUPPORTED_FORMAT)
                !uiImageWithinLimits(size.first, size.second) -> UiPreparedResource.Failed(id, UiResourceProblem.TOO_LARGE, "${size.first}x${size.second}")
                else -> UiPreparedResource.Image(id, lookup.file, size.first, size.second)
            }
        }
    }

    private fun font(resource: UiResource): UiPreparedResource {
        val id = resource.id
        val found = resolver.resolve(resource.path)
        if (found is UiFileLookup.Rejected) return UiPreparedResource.Failed(id, found.problem)
        val fnt = (found as UiFileLookup.Found).file
        if (!fnt.extension.equals("fnt", true)) return UiPreparedResource.Failed(id, UiResourceProblem.UNSUPPORTED_FORMAT)
        val text = runCatchingKeepingCancellation { fnt.readText(Charsets.UTF_8) }.getOrNull()
            ?: return UiPreparedResource.Failed(id, UiResourceProblem.UNREADABLE)
        if (!text.startsWith("info ")) return UiPreparedResource.Failed(id, UiResourceProblem.UNSUPPORTED_FORMAT, "text .fnt only")
        val names = Regex("""^page\s+.*\bfile="([^"]+)"""", RegexOption.MULTILINE).findAll(text).map { it.groupValues[1] }.toList()
        if (names.isEmpty()) return UiPreparedResource.Failed(id, UiResourceProblem.FONT_PAGE_MISSING)
        val pages = mutableListOf<File>()
        for (name in names) {
            when (val page = resolver.resolve(name, fnt.parentFile)) {
                is UiFileLookup.Rejected -> return UiPreparedResource.Failed(id, if (page.problem == UiResourceProblem.MISSING) UiResourceProblem.FONT_PAGE_MISSING else page.problem, name)
                is UiFileLookup.Found -> {
                    val size = probeImageSize(page.file) ?: return UiPreparedResource.Failed(id, UiResourceProblem.UNREADABLE, name)
                    if (!uiImageWithinLimits(size.first, size.second)) return UiPreparedResource.Failed(id, UiResourceProblem.TOO_LARGE, name)
                    pages += page.file
                }
            }
        }
        return UiPreparedResource.Font(id, fnt, pages)
    }
}
