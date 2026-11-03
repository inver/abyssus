package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Game UI assets in the project (GUI-01..): New UI Asset from the Assets node, the project registry (`uiAssetNames`) kept in
 * step by creation, Unregister and Register, the refusal of a taken name, and Undo and Redo of each step.
 */
class GameUiLifecycleUiTest : UiTestBase() {
    private val frame get() = robot.find<IdeFrame>(Duration.ofSeconds(10))
    private val abss: Path get() = project.resolve("Untitled.abss")
    private val assets: Path get() = project.dir.resolve("assets")
    private val mapper = ObjectMapper()

    @After
    fun closeDialogs() {
        runCatching { frame.closeStrayDialogs() }
        runCatching { if (frame.propertiesWindow() != null) frame.closeProperties() }
    }

    private fun registry(): List<String> = mapper.readTree(Files.readString(abss))["uiAssetNames"]?.map { it.asText() }.orEmpty()

    private fun assetsTree(): AbyssusTree {
        val tree = frame.selectAbyssusView()
        frame.normalizeLayout(ide)
        LifecycleHarness.awaitState("the tree shows Assets", true) { tree.rows().any { it.startsWith("Assets") } }
        return tree
    }

    private fun newAsset(tree: AbyssusTree, name: String): IdeDialog {
        tree.rightClickRow("Assets")
        frame.clickMenuPath("New", "New UI Asset...")
        val dialog = frame.dialog("New UI Asset").awaitOpen()
        dialog.type(name)
        return dialog
    }

    @Test
    fun aTakenNameIsRefusedAndAFreeOneCreatesTheRegisteredAssetWithUndoRedoAndReopen() {
        val before = project.hashes()
        val tree = assetsTree()

        // a taken folder name keeps OK unavailable
        val refused = newAsset(tree, "tree")
        Thread.sleep(800)
        assertFalse("OK waits for a free name", refused.isButtonEnabled("OK"))
        refused.click("Cancel")
        refused.awaitClosed()
        assertEquals("a refused name wrote nothing", before, project.hashes())

        val dialog = newAsset(tree, "hud")
        LifecycleHarness.awaitState("OK is available", true) { dialog.isButtonEnabled("OK") }
        dialog.click("OK")
        dialog.awaitClosed()
        val created = assets.resolve("hud/meta.json")
        LifecycleHarness.awaitState("the asset exists", true) { Files.exists(created) }
        val meta = mapper.readTree(Files.readString(created))
        assertEquals("abyssus", meta["format"].asText())
        assertEquals("UI", meta["type"].asText())
        assertEquals("the project registers the new asset", listOf("hud"), registry().filter { it == "hud" })
        val abssAfter = Files.readString(abss)
        val metaAfter = Files.readString(created)

        // one Undo takes the asset and its registration back, one Redo returns both
        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo removes the asset", false) { Files.exists(assets.resolve("hud")) }
        assertFalse("and its registration", "hud" in registry())
        tree.focus()
        frame.redo()
        LifecycleHarness.awaitState("Redo brings it back", true) { Files.exists(created) }
        assertEquals(metaAfter, Files.readString(created))
        assertEquals(abssAfter, Files.readString(abss))

        reset.reopenProject()
        assertEquals("the asset survives a reopen", metaAfter, Files.readString(created))
        assertTrue("and stays registered", "hud" in registry())
    }

    @Test
    fun unregisterAndRegisterChangeOnlyTheProjectRegistryAndKeepTheAssetFolder() {
        val tree = assetsTree()
        val dialog = newAsset(tree, "hud")
        dialog.click("OK")
        dialog.awaitClosed()
        LifecycleHarness.awaitState("hud is registered", true) { "hud" in registry() }
        frame.saveAll()
        val registered = Files.readString(abss)
        val folder = Files.readString(assets.resolve("hud/meta.json"))

        tree.expand("UI")
        tree.rightClickRow("hud")
        frame.clickMenuItem("Unregister UI Asset")
        LifecycleHarness.awaitState("hud is unregistered", false) { "hud" in registry() }
        assertEquals("the asset folder is untouched", folder, Files.readString(assets.resolve("hud/meta.json")))
        val unregistered = Files.readString(abss)

        tree.rightClickRow("hud")
        frame.clickMenuItem("Register UI Asset")
        LifecycleHarness.awaitState("hud is registered again", true) { "hud" in registry() }
        assertEquals("registering again restores the project file's bytes", registered, Files.readString(abss))
        assertTrue(unregistered != registered)

        tree.focus()
        frame.undo()
        LifecycleHarness.awaitState("Undo unregisters", false) { "hud" in registry() }
        tree.focus()
        frame.redo()
        LifecycleHarness.awaitState("Redo registers", true) { "hud" in registry() }
        assertEquals(folder, Files.readString(assets.resolve("hud/meta.json")))
    }

    private fun hudMeta(): Path = assets.resolve("hud/meta.json")

    private fun createHud(tree: AbyssusTree) {
        val dialog = newAsset(tree, "hud")
        dialog.click("OK")
        dialog.awaitClosed()
        LifecycleHarness.awaitState("hud exists", true) { Files.exists(hudMeta()) }
        frame.saveAll()
    }

    /** Right-clicks the UI folder (its label ends with the count of registered assets) and picks [item]. */
    private fun uiFolderMenu(tree: AbyssusTree, item: String) {
        val folder = tree.rows().first { it.startsWith("UI ") }
        tree.rightClickRow(folder)
        frame.clickMenuItem(item)
    }

    private fun screenNames(): List<String> =
        mapper.readTree(Files.readString(hudMeta())).findValues("screens").flatMap { node -> node.map { it.path("id").asText(it.path("name").asText()) } }

    private fun addScreen(name: String): IdeDialog {
        val dialog = frame.dialog("Add Screen").awaitOpen()
        dialog.type(name)
        return dialog
    }

    @Test
    fun screensAreAddedWithUniqueIdsAndOneUndoPerScreen() {
        val tree = assetsTree()
        createHud(tree)
        val original = Files.readString(hudMeta())
        val before = screenNames()

        uiFolderMenu(tree, "Create Screen")
        val dialog = addScreen("title")
        dialog.click("OK")
        dialog.awaitClosed()
        frame.saveAll()
        LifecycleHarness.awaitState("the screen is added", true) { "title" in screenNames() }
        val added = Files.readString(hudMeta())
        assertEquals("only the screen list grew", before.size + 1, screenNames().size)

        // the same name again gets a unique id instead of replacing or duplicating the first screen
        uiFolderMenu(tree, "Create Screen")
        val again = addScreen("title")
        again.click("OK")
        again.awaitClosed()
        frame.saveAll()
        LifecycleHarness.awaitState("the second screen has its own id", true) { screenNames().size == before.size + 2 }
        assertEquals("the ids are unique", screenNames().size, screenNames().toSet().size)
        assertTrue("the first screen is kept", "title" in screenNames())
        val twice = Files.readString(hudMeta())

        // one Undo per added screen
        undoThroughSceneDocument(hudMeta(), added)
        assertEquals("the first Undo takes only the second screen back", before.size + 1, screenNames().size)
        undoThroughSceneDocument(hudMeta(), original)
        assertEquals("the second Undo takes the first screen back", before, screenNames())
        ide.openTextEditor(hudMeta().toString())
        try {
            frame.redo()
            LifecycleHarness.awaitState("Redo brings the first screen back", true) { ide.documentText(hudMeta().toString()) == added }
            frame.saveAll()
        } finally {
            ide.closeFile(hudMeta().toString())
        }
        assertEquals(added, Files.readString(hudMeta()))
        assertTrue("the second version existed before the Undos", twice != added)
    }

    @Test
    fun anUnsupportedVersionOffersNoScreenAndTheFileIsNotRewritten() {
        val tree = assetsTree()
        createHud(tree)
        val future = Files.readString(hudMeta()).replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertTrue("the version was prepared", future != Files.readString(hudMeta()))
        Files.writeString(hudMeta(), future)
        ide.refresh(hudMeta().toString())
        Thread.sleep(1500)

        // the UI folder offers no screen for a document this build does not support: the menu item is absent or disabled
        val folder = tree.rows().first { it.startsWith("UI ") }
        tree.rightClickRow(folder)
        Thread.sleep(800)
        val offered = frame.menuItemEnabled("Create Screen")
        frame.dismissPopup()
        assertFalse("Create Screen is not offered for an unsupported UI asset", offered)
        frame.saveAll()
        assertEquals("the unsupported document was not rewritten", future, Files.readString(hudMeta()))
    }
}
