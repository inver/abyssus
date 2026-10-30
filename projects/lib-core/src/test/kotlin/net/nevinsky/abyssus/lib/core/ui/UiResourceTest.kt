package net.nevinsky.abyssus.lib.core.ui

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.assets.ui.*
import net.nevinsky.abyssus.lib.core.ui.style.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

class UiResourceTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val root: File = Files.createTempDirectory("ui-resources").toFile()
    private val asset = File(root, "asset").also { it.mkdirs() }
    private val outside = File(root, "outside").also { it.mkdirs() }

    @After fun cleanUp() { root.deleteRecursively() }

    private fun png(file: File, w: Int = 4, h: Int = 4) {
        file.parentFile.mkdirs()
        ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", file)
    }

    private fun collection(resources: String = "[]", styles: String = "[]", root: String = """{"id":"r","type":"panel"}"""): UiCollection =
        (UiCollectionDecoder().decode(json.readObject("""{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
            "resources":$resources,"styles":$styles,"screens":[{"id":"s","root":$root}]}}}""")) as UiAdmission.Admitted).collection

    private fun prepare(vararg resources: Pair<String, String>, kind: String = "image"): UiPreparedResources {
        val list = resources.joinToString(",", "[", "]") { """{"id":"${it.first}","kind":"$kind","path":"${it.second}"}""" }
        return UiResourcePreparer(UiResourceResolver(asset)).prepare(collection(list))
    }

    private fun problem(prepared: UiPreparedResources, id: String) = (prepared[id] as UiPreparedResource.Failed).problem

    @Test fun validImagesRecordTheirSize() {
        png(File(asset, "logo.png"), 32, 16)
        png(File(asset, "sub/deep.png"), 8192, 1)
        val prepared = prepare("logo" to "logo.png", "deep" to "sub/deep.png")
        assertEquals(32, (prepared["logo"] as UiPreparedResource.Image).width)
        assertEquals(16, (prepared["logo"] as UiPreparedResource.Image).height)
        assertTrue(prepared["deep"] is UiPreparedResource.Image)
        assertTrue(prepared.issues.isEmpty())
    }

    @Test fun missingResourcesFallBackWithoutAffectingOthers() {
        png(File(asset, "ok.png"))
        File(asset, "folder").mkdirs()
        val prepared = prepare("gone" to "gone.png", "ok" to "ok.png", "dir" to "folder")
        assertEquals(UiResourceProblem.MISSING, problem(prepared, "gone"))
        assertEquals(UiResourceProblem.NOT_A_FILE, problem(prepared, "dir"))
        assertTrue(prepared["ok"] is UiPreparedResource.Image)
        assertEquals(setOf("gone", "dir"), prepared.issues.map { it.resourceId }.toSet())
    }

    @Test fun pathsCannotLeaveTheAssetFolder() {
        png(File(outside, "secret.png"))
        val absolute = File(outside, "secret.png").absolutePath
        val prepared = prepare("up" to "../outside/secret.png", "abs" to absolute, "net" to "http://example.com/a.png", "unc" to "//host/a.png")
        assertEquals(UiResourceProblem.ESCAPES_ASSET, problem(prepared, "up"))
        assertEquals(UiResourceProblem.ESCAPES_ASSET, problem(prepared, "abs"))
        assertEquals(UiResourceProblem.NETWORK, problem(prepared, "net"))
        assertEquals(UiResourceProblem.NETWORK, problem(prepared, "unc"))
    }

    @Test fun symbolicLinksCannotLeaveTheAssetFolder() {
        png(File(outside, "secret.png"))
        val fileLink = File(asset, "link.png").toPath()
        val dirLink = File(asset, "linked").toPath()
        try {
            Files.createSymbolicLink(fileLink, File(outside, "secret.png").toPath())
            Files.createSymbolicLink(dirLink, outside.toPath())
        } catch (e: UnsupportedOperationException) { assumeTrue("symlinks unsupported", false) } catch (e: java.io.IOException) { assumeTrue("symlinks unavailable", false) }
        png(File(asset, "inside.png"))
        Files.createSymbolicLink(File(asset, "alias.png").toPath(), File(asset, "inside.png").toPath())
        val prepared = prepare("file" to "link.png", "dir" to "linked/secret.png", "alias" to "alias.png")
        assertEquals(UiResourceProblem.ESCAPES_ASSET, problem(prepared, "file"))
        assertEquals(UiResourceProblem.ESCAPES_ASSET, problem(prepared, "dir"))
        assertTrue("a link that stays inside is fine", prepared["alias"] is UiPreparedResource.Image)
    }

    @Test fun oversizedUnsupportedAndCorruptImagesAreRejected() {
        png(File(asset, "wide.png"), UI_MAX_IMAGE_SIDE + 1, 1)
        png(File(asset, "tall.png"), 1, UI_MAX_IMAGE_SIDE + 1)
        File(asset, "anim.gif").writeBytes(byteArrayOf(1, 2, 3))
        File(asset, "broken.png").writeText("not a png")
        File(asset, "dir.png").mkdirs()
        val prepared = prepare("wide" to "wide.png", "tall" to "tall.png", "gif" to "anim.gif", "broken" to "broken.png", "dir" to "dir.png")
        assertEquals(UiResourceProblem.TOO_LARGE, problem(prepared, "wide"))
        assertEquals(UiResourceProblem.TOO_LARGE, problem(prepared, "tall"))
        assertEquals(UiResourceProblem.UNSUPPORTED_FORMAT, problem(prepared, "gif"))
        assertEquals(UiResourceProblem.UNREADABLE, problem(prepared, "broken"))
        assertEquals(UiResourceProblem.NOT_A_FILE, problem(prepared, "dir"))
    }

    private fun fnt(vararg pages: String) = "info face=\"Test\" size=16\ncommon lineHeight=16 base=12 scaleW=64 scaleH=64 pages=${pages.size}\n" +
        pages.mapIndexed { i, p -> "page id=$i file=\"$p\"" }.joinToString("\n") + "\nchars count=0\n"

    @Test fun bitmapFontsNeedTheirTexturePagesInsideTheAsset() {
        png(File(asset, "fonts/a_0.png")); png(File(asset, "fonts/a_1.png"))
        File(asset, "fonts/a.fnt").writeText(fnt("a_0.png", "a_1.png"))
        File(asset, "fonts/missing.fnt").writeText(fnt("nope.png"))
        File(asset, "fonts/escape.fnt").writeText(fnt("../../outside/secret.png"))
        png(File(outside, "secret.png"))
        File(asset, "fonts/nopages.fnt").writeText("info face=\"x\"\ncommon lineHeight=1\n")
        File(asset, "fonts/binary.fnt").writeBytes(byteArrayOf('B'.code.toByte(), 'M'.code.toByte(), 'F'.code.toByte(), 3))
        File(asset, "fonts/big_0.png").let { png(it, UI_MAX_IMAGE_SIDE + 1, 1) }
        File(asset, "fonts/big.fnt").writeText(fnt("big_0.png"))
        File(asset, "fonts/font.txt").writeText(fnt("a_0.png"))
        val prepared = prepare("ok" to "fonts/a.fnt", "missing" to "fonts/missing.fnt", "escape" to "fonts/escape.fnt", "nopages" to "fonts/nopages.fnt",
            "binary" to "fonts/binary.fnt", "big" to "fonts/big.fnt", "txt" to "fonts/font.txt", kind = "bitmapFont")
        assertEquals(listOf("a_0.png", "a_1.png"), (prepared["ok"] as UiPreparedResource.Font).pages.map { it.name })
        assertEquals(UiResourceProblem.FONT_PAGE_MISSING, problem(prepared, "missing"))
        assertEquals(UiResourceProblem.ESCAPES_ASSET, problem(prepared, "escape"))
        assertEquals(UiResourceProblem.FONT_PAGE_MISSING, problem(prepared, "nopages"))
        assertEquals(UiResourceProblem.UNSUPPORTED_FORMAT, problem(prepared, "binary"))
        assertEquals(UiResourceProblem.TOO_LARGE, problem(prepared, "big"))
        assertEquals(UiResourceProblem.UNSUPPORTED_FORMAT, problem(prepared, "txt"))
    }

    // --- style resolution ---

    private val resolver = UiStyleResolver(json)

    private fun resolved(widget: String, styles: String = "[]", states: Set<UiVisualState> = emptySet()): UiResolvedStyle {
        val c = collection(styles = styles, root = widget)
        return resolver.resolve(c, c.screens[0].root, states)
    }

    @Test fun bundledThemeWorksWithoutAnyCustomResources() {
        val style = resolved("""{"id":"b","type":"button"}""")
        assertEquals(16.0, style.number("fontSize")!!, 0.0)
        assertEquals(0x3a4a5cff, style.color("background"))
        assertEquals(0xffffffff.toInt(), style.color("color"))
        assertNull(style.resource("font")) // the default font stands in
        assertEquals(0x4a5e75ff, resolved("""{"id":"b","type":"button"}""", states = setOf(UiVisualState.HOVER)).color("background"))
    }

    @Test fun layersApplyDefaultsThenNamedThenWidgetThenStates() {
        val styles = """[{"name":"fancy","fontSize":24,"color":"#ff0000","background":"#101010",
            "states":{"hover":{"background":"#202020"},"pressed":{"background":"#303030"}}}]"""
        val named = resolved("""{"id":"b","type":"button","style":"fancy"}""", styles)
        assertEquals(24.0, named.number("fontSize")!!, 0.0)
        assertEquals(0xff0000ff.toInt(), named.color("color"))
        assertEquals(0x101010ff, named.color("background"))
        assertEquals(8.0, named.number("padding")!!, 0.0) // still the bundled button padding
        val own = resolved("""{"id":"b","type":"button","style":"fancy","properties":{"styleOverrides":{"fontSize":40,"states":{"hover":{"background":"#aaaaaa"}}}}}""", styles)
        assertEquals(40.0, own.number("fontSize")!!, 0.0)
        // the named style's state override loses to the widget's, and a state override beats every base value
        assertEquals(0xaaaaaaff.toInt(), resolved("""{"id":"b","type":"button","style":"fancy","properties":{"styleOverrides":{"background":"#0000ff","states":{"hover":{"background":"#aaaaaa"}}}}}""",
            styles, setOf(UiVisualState.HOVER)).color("background"))
        assertEquals(0x202020ff, resolved("""{"id":"b","type":"button","style":"fancy","properties":{"styleOverrides":{"background":"#0000ff"}}}""", styles, setOf(UiVisualState.HOVER)).color("background"))
    }

    @Test fun statePriorityIsDisabledPressedHoverFocusedNormal() {
        val styles = """[{"name":"s","color":"#010101","states":{"focused":{"color":"#020202"},"hover":{"color":"#030303"},"pressed":{"color":"#040404"},"disabled":{"color":"#050505"}}}]"""
        fun color(vararg states: UiVisualState) = resolved("""{"id":"b","type":"button","style":"s"}""", styles, states.toSet()).color("color")
        assertEquals(0x010101ff, color())
        assertEquals(0x020202ff, color(UiVisualState.FOCUSED))
        assertEquals(0x030303ff, color(UiVisualState.FOCUSED, UiVisualState.HOVER))
        assertEquals(0x040404ff, color(UiVisualState.FOCUSED, UiVisualState.HOVER, UiVisualState.PRESSED))
        assertEquals(0x050505ff, color(UiVisualState.PRESSED, UiVisualState.DISABLED, UiVisualState.HOVER))
        // focus keeps its visible indicator from the bundled theme next to the custom colors
        assertEquals(2.0, resolved("""{"id":"b","type":"button","style":"s"}""", styles, setOf(UiVisualState.FOCUSED)).number("borderWidth")!!, 0.0)
    }

    @Test fun missingNamedStyleFallsBackToDefaultsWithADiagnostic() {
        val style = resolved("""{"id":"b","type":"button","style":"nope"}""")
        assertEquals(listOf(UiStyleDiagnostic(UiStyleIssue.MISSING_STYLE, "b", "nope")), style.diagnostics)
        assertEquals(0x3a4a5cff, style.color("background"))
    }

    @Test fun colorParsingAndMalformedValues() {
        assertEquals(0x11223344, parseUiColor("#11223344"))
        assertEquals(0x112233ff, parseUiColor("#112233"))
        assertNull(parseUiColor("112233")); assertNull(parseUiColor("#12")); assertNull(parseUiColor("#gg0000"))
        val style = resolved("""{"id":"b","type":"button","style":"bad"}""", """[{"name":"bad","color":"red","fontSize":"big"}]""")
        assertNull(style.color("color"))
        assertNull(style.number("fontSize"))
        assertTrue(style.has("color"))
    }
}
