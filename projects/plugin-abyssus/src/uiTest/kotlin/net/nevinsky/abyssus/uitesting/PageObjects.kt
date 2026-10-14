package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.fixtures.JListFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

private val SHORT = Duration.ofSeconds(10)

@DefaultXpath("IdeFrame", "//div[@class='IdeFrameImpl']")
@FixtureName("IDE frame")
class IdeFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

    val projectToolWindow get() = find<ComponentFixture>(byXpath("//div[@class='InternalDecoratorImpl' and (@accessiblename='Project Tool Window' or @accessiblename='Abyssus Tool Window')]"), SHORT)

    /** The Project tool window's view selector ("Project", "Abyssus", ...). */
    private val viewSelector get() = find<ComponentFixture>(byXpath("//div[@class='ContentComboLabel']"), SHORT)

    fun currentView(): String = viewSelector.callJs("component.getText()")

    /** Chooses a Project tool window view ("Project", "Abyssus", ...) through the selector popup, using the mouse. */
    fun selectView(name: String) {
        if (currentView() == name) return
        viewSelector.click()
        val popup = find<JListFixture>(byXpath("//div[@class='HeavyWeightWindow']//div[@class='MyList']"), SHORT)
        popup.clickItem(name, fullMatch = true)
        waitFor(SHORT, Duration.ofMillis(200), "$name view selected") { currentView() == name }
    }

    /** Selects the Abyssus view and returns its tree collapsed to the project row, so every scenario starts from the same state. */
    /** Leaves the Abyssus view and comes back, so its tree is shown again rather than reused. */
    fun reopenAbyssusView() {
        selectView("Project")
        selectView("Abyssus")
    }

    fun selectAbyssusView(): AbyssusTree {
        selectView("Abyssus")
        return tree().also { it.collapseToProjectRow() }
    }

    /** Save All, as the IDE's own shortcut does. */
    fun saveAll() = keyboard { hotKey(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_S) }

    /** The IDE's Undo shortcut. */
    fun undo() = keyboard { hotKey(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_Z) }

    fun tree(): AbyssusTree = AbyssusTree(this)

    /**
     * Fixes the window and tool window sizes that screenshots depend on, and moves the pointer off any control, so hover
     * highlights never reach a capture.
     */
    fun normalizeLayout(ide: IdeQueries) {
        callJs<Boolean>("component.setBounds(0, 0, $FRAME_WIDTH, $FRAME_HEIGHT); true", runInEdt = true)
        // text carets must not blink into a capture
        callJs<Boolean>("javax.swing.UIManager.put('TextField.caretBlinkRate', java.lang.Integer.valueOf(0)); true", runInEdt = true)
        ide.setToolWindowWidth("Project", PROJECT_WIDTH)
        if (propertiesWindow() != null) ide.setToolWindowWidth("Abyssus Properties", PROPERTIES_WIDTH)
        movePointerAway()
    }

    /** Gives the keyboard focus to the Abyssus tree, a control with no caret or focus ring of its own in a Properties capture. */
    fun focusTree() {
        tree().focus()
    }

    fun movePointerAway() {
        callJs<Boolean>("var p = component.getLocationOnScreen(); new java.awt.Robot().mouseMove(p.x + $FRAME_WIDTH - 4, p.y + $FRAME_HEIGHT - 4); true")
    }

    companion object {
        const val FRAME_WIDTH = 1900
        const val FRAME_HEIGHT = 1000
        const val PROJECT_WIDTH = 460
        const val PROPERTIES_WIDTH = 1250
    }

    /** A modal dialog with this title, searched across the IDE's windows. */
    fun dialog(title: String): IdeDialog = IdeDialog(this, title)

    /** Closes any modal dialog left open (Escape), so a failed scenario cannot block the next one. */
    fun closeStrayDialogs() {
        repeat(5) {
            if (remoteRobot.findAll<CommonContainerFixture>(byXpath("//div[@class='MyDialog']")).isEmpty()) return
            keyboard { escape() }
            Thread.sleep(300)
        }
    }

    fun openDialogs(title: String): Boolean =
        remoteRobot.findAll<CommonContainerFixture>(byXpath("//div[@class='MyDialog' and @title='$title']")).isNotEmpty()

    /** Clicks the named item of the context menu that is open. */
    fun clickMenuItem(text: String) = find<ComponentFixture>(byXpath("//div[@class='ActionMenuItem' and @text='$text']"), SHORT).click()

    fun properties(): PropertiesPanel = PropertiesPanel(this)

    fun propertiesStripeButton() = find<ComponentFixture>(byXpath("//div[@class='SquareStripeButton' and @accessiblename='Abyssus Properties']"), SHORT)

    fun propertiesWindow(): CommonContainerFixture? =
        findAll<CommonContainerFixture>(byXpath("//div[@class='InternalDecoratorImpl' and @accessiblename='Abyssus Properties Tool Window']")).firstOrNull()

    fun openProperties(): CommonContainerFixture {
        if (propertiesWindow() == null) propertiesStripeButton().click()
        waitFor(SHORT, Duration.ofMillis(200), "Abyssus Properties visible") { propertiesWindow() != null }
        return propertiesWindow()!!
    }

    fun closeProperties() {
        if (propertiesWindow() != null) propertiesStripeButton().click()
        waitFor(SHORT, Duration.ofMillis(200), "Abyssus Properties hidden") { propertiesWindow() == null }
    }
}

/** The Abyssus project view tree. Re-resolve it after any refresh; fixtures hold on to a component instance. */
class AbyssusTree(private val frame: IdeFrame) {
    private val fixture get() = frame.find<JTreeFixture>(byXpath("//div[@class='EyeTree']"), SHORT)

    /** Row texts as the tree draws them, one string per visible row. */
    fun rows(): List<String> = fixture.collectRows()

    private fun find(all: List<String>, text: String): Int =
        all.indexOf(text).takeIf { it >= 0 } ?: all.indexOfFirst { it.startsWith("$text ") }.takeIf { it >= 0 } ?: all.indexOfFirst { it.contains(text) }

    /**
     * The row whose text is exactly [text], else the first one that starts with it, else the first that contains it.
     * The tree loads in the background, so a missing row is awaited before it is an error.
     */
    private fun indexOf(text: String): Int {
        try {
            waitFor(Duration.ofSeconds(20), Duration.ofMillis(300), "tree row '$text'") { find(rows(), text) >= 0 }
        } catch (e: Exception) {
            throw IllegalStateException("no row '$text' in ${rows()}", e)
        }
        return find(rows(), text)
    }

    fun isExpanded(text: String): Boolean = fixture.callJs<Boolean>("component.isExpanded(${indexOf(text)})", runInEdt = true)

    /**
     * Opens the row containing [text] with the keyboard unless it is open already, then waits for its children to load
     * (the tree builds them in the background): the row count must grow.
     */
    fun expand(text: String) {
        if (isExpanded(text)) return
        val before = rows().size
        clickRow(text)
        frame.keyboard { key(java.awt.event.KeyEvent.VK_RIGHT) }
        waitFor(SHORT, Duration.ofMillis(200), "children of '$text'") { rows().size > before }
    }

    /** Collapses every row below the first (view state only), then selects the project row. */
    fun collapseToProjectRow() {
        fixture.callJs<Boolean>("for (var i = component.getRowCount() - 1; i >= 1; i--) component.collapseRow(i); true", runInEdt = true)
    }

    fun focus() {
        fixture.callJs<Boolean>("component.requestFocusInWindow(); true", runInEdt = true)
    }

    fun rightClickRow(text: String) = fixture.rightClickRow(indexOf(text))

    fun clickRow(text: String) = fixture.clickRow(indexOf(text))

    /**
     * Clicks a painted row action. The tree paints actions from the right edge of its visible area leftwards, 8px
     * apart, so [fromRight] pixels in from that edge on the row's vertical centre hits one: 14 is the eye (rightmost),
     * 70 the skybox Choose button (left of the eye).
     */
    fun clickRowAction(text: String, fromRight: Int) {
        val i = indexOf(text)
        fixture.callJs<Boolean>("component.scrollRowToVisible($i); true", runInEdt = true)
        val x = fixture.callJs<Int>("component.getVisibleRect().x + component.getVisibleRect().width - $fromRight", runInEdt = true)
        val y = fixture.callJs<Int>("component.getRowBounds($i).y + Math.floor(component.getRowBounds($i).height / 2)", runInEdt = true)
        fixture.click(java.awt.Point(x, y))
    }

    fun clickRowEye(text: String) = clickRowAction(text, EYE_FROM_RIGHT)

    fun clickRowChoose(text: String) = clickRowAction(text, CHOOSE_FROM_RIGHT)

    private companion object {
        const val EYE_FROM_RIGHT = 14
        const val CHOOSE_FROM_RIGHT = 70
    }
}

/** The Abyssus Properties tool window's content. Re-resolve after the selection changes; the panel is rebuilt. */
class PropertiesPanel(private val frame: IdeFrame) {
    private val window get() = frame.openProperties()

    /** Every text the panel draws, including painted ones. */
    fun texts(): List<String> = window.findAllText().map { it.text }

    /** The panel's component, for captures. */
    val component: ComponentFixture get() = window

    /** Waits until every preview thumbnail has a real image (not the bordered placeholder); throws otherwise. */
    fun awaitPreviewsLoaded(timeout: Duration = Duration.ofSeconds(30)) =
        waitFor(timeout, Duration.ofMillis(500), "previews loaded") { previewCount() > 0 && unloadedPreviews().isEmpty() }

    fun fieldNames(prefix: String): List<String> =
        window.findAll<ComponentFixture>(byXpath("//div[starts-with(@name, '$prefix')]")).map { it.callJs<String>("component.getName()", runInEdt = true) }

    private fun field(name: String) = window.find<ComponentFixture>(byXpath("//div[@class='JBTextField' and @name='$name']"), SHORT)

    fun fieldText(name: String): String = field(name).callJs("component.getText()", runInEdt = true)

    /** Types [text] into the named field with the keyboard and commits it with Enter. */
    fun enter(name: String, text: String) {
        val f = field(name)
        f.click()
        frame.keyboard {
            hotKey(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_A)
            enterText(text)
            enter()
        }
    }

    fun errorText(kind: String, field: String): String =
        window.findAll<ComponentFixture>(byXpath("//div[@class='JBLabel' and @name='error-$kind-$field']")).firstOrNull()
            ?.callJs<String>("component.getText()", runInEdt = true).orEmpty()

    /** Thumbnails (`face-*`, `hdr-preview`) whose image has not loaded yet; empty once every preview is real. */
    fun unloadedPreviews(): List<String> =
        window.findAll<ComponentFixture>(byXpath("//div[@name='hdr-preview' or starts-with(@name, 'face-')]"))
            .filter { !it.callJs<Boolean>("component.getImage() != null", runInEdt = true) }
            .map { it.callJs<String>("component.getName()", runInEdt = true) }

    fun previewCount(): Int =
        window.findAll<ComponentFixture>(byXpath("//div[@name='hdr-preview' or starts-with(@name, 'face-')]")).size
}

class IdeDialog(private val frame: IdeFrame, val title: String) {
    /** The dialog's component, for captures. */
    val component: ComponentFixture get() = root

    private val root get() = frame.remoteRobot.find<CommonContainerFixture>(byXpath("//div[@class='MyDialog' and @title='$title']"), SHORT)

    /**
     * True once every chooser row that has face files or an HDR image has its thumbnails (they are decoded off the EDT after
     * the dialog opens); an unreadable image leaves null cells and counts as not loaded. Read-only.
     */
    fun thumbnailsLoaded(): Boolean = root.find<ComponentFixture>(byXpath("//div[@class='JBList']"), SHORT).callJs<Boolean>(
        "var m = component.getModel(); var ok = true;" +
            "for (var i = 0; i < m.getSize(); i++) { var c = m.getElementAt(i); if (c == null || c.getFolder() == null) continue;" +
            " var files = c.getFaceFiles(); var named = false; for (var j = 0; j < files.size(); j++) if (files.get(j) != null) named = true;" +
            " if (!named && c.getHdr() == null) continue;" +
            " var t = c.getThumbs(); var real = false; for (var k = 0; k < t.size(); k++) if (t.get(k) != null) real = true;" +
            " if (!real) ok = false; }" +
            "ok",
        runInEdt = true,
    )

    fun awaitThumbnailsLoaded() = waitFor(Duration.ofSeconds(30), Duration.ofMillis(500), "chooser thumbnails loaded") { thumbnailsLoaded() }

    fun isOpen() = frame.openDialogs(title)

    fun awaitOpen(): IdeDialog = apply { waitFor(SHORT, Duration.ofMillis(200), "dialog '$title' open") { isOpen() } }

    fun awaitClosed() = waitFor(SHORT, Duration.ofMillis(200), "dialog '$title' closed") { !isOpen() }

    fun click(button: String) = root.find<ComponentFixture>(byXpath("//div[@class='JButton' and @text='$button']"), SHORT).click()

    /** Every text the dialog draws, including painted list rows. */
    fun texts(): List<String> = root.findAllText().map { it.text }

    /** Presses Enter-less typing into the focused field (the chooser focuses its filter first). */
    fun typeIntoFocus(text: String) {
        frame.keyboard { hotKey(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_A); enterText(text) }
    }

    /** Clicks list row [index] (0 is the None entry), at the vertical middle of its cell bounds. */
    fun clickListRow(index: Int) {
        val list = root.find<ComponentFixture>(byXpath("//div[@class='JBList']"), SHORT)
        val y = list.callJs<Int>("component.getCellBounds($index, $index).y + Math.floor(component.getCellBounds($index, $index).height / 2)", runInEdt = true)
        list.click(java.awt.Point(100, y))
    }

    fun textFieldText(): String = root.find<ComponentFixture>(byXpath("//div[@class='JTextField' or @class='JBTextField']"), SHORT).callJs("component.getText()", runInEdt = true)

    /** Replaces the text of the dialog's single text field with the keyboard. */
    fun type(text: String) {
        root.find<ComponentFixture>(byXpath("//div[@class='JTextField' or @class='JBTextField']"), SHORT).click()
        frame.keyboard {
            hotKey(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_A)
            enterText(text)
        }
    }
}

/** The Scene View editor's toolbar and tool status, once a scene is open in the Scene View tab. */
class SceneViewToolbar(private val frame: IdeFrame) {
    val component get() = frame.find<CommonContainerFixture>(byXpath("//div[@accessiblename='Scene toolbar']"), Duration.ofSeconds(60))

    private fun button(tooltip: String) =
        buttons().first { it.callJs<String>("component.getPresentation().getText()", runInEdt = true) == tooltip }

    private fun buttons() = component.findAll<ComponentFixture>(byXpath("//div[contains(@class, 'ActionButton')]"))

    fun awaitShown() = waitFor(Duration.ofSeconds(60), Duration.ofMillis(500), "the Scene View toolbar is shown") {
        frame.findAll<ComponentFixture>(byXpath("//div[@accessiblename='Scene toolbar']")).isNotEmpty()
    }

    /** The texts of the toolbar's action buttons (their tooltips for icon buttons), left to right. */
    fun buttonTooltips(): List<String> = buttons()
        .sortedBy { it.callJs<Int>("component.getLocationOnScreen().x", runInEdt = true) }
        .map { it.callJs<String>("component.getPresentation().getText()", runInEdt = true) }

    fun click(tooltip: String) = button(tooltip).click()

    fun isPressed(tooltip: String): Boolean = button(tooltip).callJs<Boolean>("component.isSelected()", runInEdt = true)

    fun isEnabled(tooltip: String): Boolean = button(tooltip).callJs<Boolean>("component.isEnabled()", runInEdt = true)

    /** The tool status line under the viewport, such as `Move · World`. */
    fun status(): String = frame.find<ComponentFixture>(byXpath("//div[@accessiblename='Scene tool status']"), SHORT)
        .callJs<String>("component.getText()", runInEdt = true)

    /** Chooses [item] in the drop-down whose current text is [current] (Local-World, shading). */
    fun choose(current: String, item: String) {
        component.find<ComponentFixture>(byXpath("//div[@class='ComboBoxButton' and @visible_text='$current']"), SHORT).click()
        frame.find<JListFixture>(byXpath("//div[@class='HeavyWeightWindow']//div[@class='MyList']"), SHORT).clickItem(item, fullMatch = true)
    }
}

fun IdeFrame.sceneToolbar() = SceneViewToolbar(this)
