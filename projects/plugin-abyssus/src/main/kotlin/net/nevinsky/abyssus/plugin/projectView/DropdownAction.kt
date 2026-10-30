package net.nevinsky.abyssus.plugin.projectView

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import java.awt.event.MouseEvent

/**
 * A row kind whose right-click menu holds only [actionIds], not the platform's project-view actions.
 */
interface DropdownAction {
    /** The action ids offered on the row, in order. */
    val actionIds: List<String>

    /** Whether [node] is a row of this kind. */
    fun appliesTo(node: Any?): Boolean

    /** Handles the popup-trigger [e] on [row] when [node] is a row of this kind; true when it did (the event is consumed). */
    fun openDropdown(invoker: EyeTree, e: MouseEvent, row: Int, node: Any?): Boolean {
        if (!appliesTo(node)) {
            return false
        }
        if (e.id == MouseEvent.MOUSE_PRESSED || e.id == MouseEvent.MOUSE_RELEASED) {
            invoker.requestFocusInWindow()
            invoker.setSelectionRow(row)
            showMenu(invoker, e.x, e.y)
        }
        e.consume()
        return true
    }

    fun showMenu(invoker: EyeTree, x: Int, y: Int) {
        val actions = ActionManager.getInstance()
        val group = DefaultActionGroup(actionIds.mapNotNull { actions.getAction(it) })
        actions.createActionPopupMenu(ActionPlaces.PROJECT_VIEW_POPUP, group).component.show(invoker, x, y)
    }
}
