/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.state

import net.nevinsky.abyssus.lib.core.ui.model.*

/** A screen of a registered UI asset. */
data class UiScreenRef(val assetName: String, val screenId: String)

/** One input layer: the standalone screen (always blocking) or an overlay (blocking only when [modal]). */
data class UiLayer(val ref: UiScreenRef, val modal: Boolean, val overlay: Boolean)

/** Finds the collection of a registered UI asset by name; the host owns loading. */
fun interface UiCollectionSource {
    fun collection(assetName: String): UiCollection?
}

/** Receives named game actions. Returns false when nothing handled [name]. */
fun interface UiActionSink {
    fun emit(name: String): Boolean
}

enum class UiNavIssue { MISSING_TARGET, WRONG_KIND, UNKNOWN_ACTION, UNHANDLED_ACTION, NO_ACTION }

data class UiNavDiagnostic(val code: UiNavIssue, val target: String? = null)

sealed interface UiNavOutcome {
    /** The visible screens or overlays changed. */
    data object Navigated : UiNavOutcome

    /** Nothing to do (back at the root, no overlay to close). */
    data object NoOp : UiNavOutcome

    /** A named action reached the sink exactly once. */
    data class Emitted(val name: String) : UiNavOutcome

    /** Reported and ignored; the current UI stays usable. */
    data class Failed(val diagnostic: UiNavDiagnostic) : UiNavOutcome
}

/** Where a pointer event goes after the layers had a look. */
sealed interface UiRoute {
    data class Handled(val layer: UiLayer, val widgetId: String) : UiRoute

    /** A modal layer or the screen swallowed it. */
    data object Blocked : UiRoute

    /** No interactive widget wanted it: the game gets it. */
    data object Passthrough : UiRoute
}

private val FOCUSABLE = setOf(UiWidgetType.BUTTON, UiWidgetType.SLIDER, UiWidgetType.CHECKBOX, UiWidgetType.TEXT_FIELD)

/**
 * Screen history, the overlay stack, action dispatch and focus of one UI instance. Pure and single-threaded; the host
 * calls it on its UI thread. UI never pauses gameplay: it only reports what is open. [assetName] names [own] among the
 * assets [source] can resolve.
 */
class UiNavigator(
    private val assetName: String,
    own: UiCollection,
    private val source: UiCollectionSource,
    private val sink: UiActionSink,
    showEntry: Boolean = true,
    private val maxHistory: Int = 16,
) {
    private val ownCollection = own

    /** The widget tree to walk for focus and Enter on [ref]; a host that expands lists supplies the expanded tree. */
    var rootProvider: (UiScreenRef) -> UiWidget? = { screenOf(it)?.root }
    private class Entry(val ref: UiScreenRef, val focus: String?)

    private var current: UiScreenRef? = null
    private val history = ArrayDeque<Entry>()
    private val overlays = mutableListOf<Entry>()

    var focus: String? = null
        private set

    init {
        if (showEntry) (own.entryScreen ?: own.screens.firstOrNull { it.kind == UiScreenKind.SCREEN }?.id)
            ?.let { current = UiScreenRef(assetName, it) }
    }

    fun currentScreen(): UiScreenRef? = current

    /**
     * Shows exactly [ref] and nothing else, forgetting history and open overlays: an editor's way of looking at one
     * screen or overlay. A `screen` becomes the current screen; an `overlay` is shown alone, with no screen below it.
     * False when [ref] names no screen.
     */
    fun display(ref: UiScreenRef): Boolean {
        val screen = screenOf(ref) ?: return false
        history.clear(); overlays.clear(); focus = null
        if (screen.kind == UiScreenKind.SCREEN) current = ref
        else { current = null; overlays += Entry(ref, null) }
        return true
    }

    fun openOverlays(): List<UiScreenRef> = overlays.map { it.ref }

    fun canGoBack(): Boolean = history.isNotEmpty()

    private fun collectionOf(asset: String): UiCollection? = if (asset == assetName) ownCollection else source.collection(asset)

    /** The screen definition [ref] names, in this navigator's own or a source asset. */
    fun screenOf(ref: UiScreenRef): UiScreen? = collectionOf(ref.assetName)?.screen(ref.screenId)

    /** Input layers, topmost first. */
    fun layers(): List<UiLayer> {
        val result = overlays.asReversed().map { UiLayer(it.ref, screenOf(it.ref)?.modal ?: false, true) }
        return result + listOfNotNull(current?.let { UiLayer(it, true, false) })
    }

    fun activate(action: UiAction): UiNavOutcome = when (action.type) {
        UiActionType.OPEN_SCREEN -> target(action, UiScreenKind.SCREEN)?.let(::open) ?: fail(action)
        UiActionType.SHOW_OVERLAY -> target(action, UiScreenKind.OVERLAY)?.let(::showOverlay) ?: fail(action)
        UiActionType.BACK -> back()
        UiActionType.CLOSE_OVERLAY -> closeOverlay(action.screenId?.let { UiScreenRef(action.assetName ?: assetName, it) })
        UiActionType.EMIT_ACTION -> action.name?.let(::emit) ?: UiNavOutcome.Failed(UiNavDiagnostic(UiNavIssue.NO_ACTION))
        null -> UiNavOutcome.Failed(UiNavDiagnostic(UiNavIssue.UNKNOWN_ACTION, action.rawType))
    }

    private var lastFailure: UiNavDiagnostic? = null

    private fun fail(action: UiAction): UiNavOutcome = UiNavOutcome.Failed(lastFailure ?: UiNavDiagnostic(UiNavIssue.MISSING_TARGET, action.screenId))

    /** The referenced screen when it exists and has [kind]; otherwise records why in [lastFailure]. */
    private fun target(action: UiAction, kind: UiScreenKind): UiScreenRef? {
        lastFailure = null
        val asset = action.assetName ?: assetName
        val collection = collectionOf(asset)
        val id = action.screenId ?: collection?.entryScreen.takeIf { kind == UiScreenKind.SCREEN }
        val screen = id?.let { collection?.screen(it) }
        if (screen == null) { lastFailure = UiNavDiagnostic(UiNavIssue.MISSING_TARGET, listOfNotNull(asset, id).joinToString(":")); return null }
        if (screen.kind != kind) { lastFailure = UiNavDiagnostic(UiNavIssue.WRONG_KIND, "$asset:${screen.id}"); return null }
        return UiScreenRef(asset, screen.id)
    }

    private fun open(ref: UiScreenRef): UiNavOutcome {
        current?.let {
            history.addLast(Entry(it, focus))
            while (history.size > maxHistory) history.removeFirst()
        }
        overlays.clear()
        current = ref
        focus = null
        return UiNavOutcome.Navigated
    }

    fun back(): UiNavOutcome {
        val entry = history.removeLastOrNull() ?: return UiNavOutcome.NoOp
        overlays.clear()
        current = entry.ref
        focus = entry.focus
        return UiNavOutcome.Navigated
    }

    private fun showOverlay(ref: UiScreenRef): UiNavOutcome {
        overlays.removeAll { it.ref == ref }
        overlays += Entry(ref, focus)
        focus = null
        return UiNavOutcome.Navigated
    }

    /** Closes [ref], or the topmost overlay when null, and restores the focus it covered. */
    fun closeOverlay(ref: UiScreenRef? = null): UiNavOutcome {
        val index = if (ref == null) overlays.lastIndex else overlays.indexOfLast { it.ref == ref }
        if (index < 0) return UiNavOutcome.NoOp
        val closed = overlays.removeAt(index)
        if (index == overlays.size) focus = closed.focus
        return UiNavOutcome.Navigated
    }

    /** Escape: closes the topmost overlay, otherwise goes back; nothing at the root. */
    fun escape(): UiNavOutcome = if (overlays.isNotEmpty()) closeOverlay() else back()

    private fun emit(name: String): UiNavOutcome =
        if (sink.emit(name)) UiNavOutcome.Emitted(name) else UiNavOutcome.Failed(UiNavDiagnostic(UiNavIssue.UNHANDLED_ACTION, name))

    /**
     * Routes a pointer event: [hit] returns the interactive widget under it in a layer, or null. Layers are asked top
     * to bottom; a modal layer or the screen swallows an event it does not handle, a nonmodal overlay lets it through.
     */
    fun route(hit: (UiLayer) -> String?): UiRoute {
        for (layer in layers()) {
            hit(layer)?.let { return UiRoute.Handled(layer, it) }
            if (layer.modal) return UiRoute.Blocked
        }
        return UiRoute.Passthrough
    }

    /**
     * Moves focus along the topmost layer's visible, enabled, focusable widgets in tree order (wrapping), and returns
     * it; null when there is none. [visible] and [enabled] come from the binding view; a hidden container hides its subtree.
     */
    fun moveFocus(forward: Boolean, visible: (UiWidget) -> Boolean, enabled: (UiWidget) -> Boolean): String? {
        val order = focusOrder(visible, enabled)
        if (order.isEmpty()) { focus = null; return null }
        val at = order.indexOf(focus)
        focus = when {
            at < 0 -> if (forward) order.first() else order.last()
            forward -> order[(at + 1) % order.size]
            else -> order[(at - 1 + order.size) % order.size]
        }
        return focus
    }

    fun focusOrder(visible: (UiWidget) -> Boolean, enabled: (UiWidget) -> Boolean): List<String> {
        val layer = layers().firstOrNull() ?: return emptyList()
        val root = rootProvider(layer.ref) ?: return emptyList()
        val order = mutableListOf<String>()
        fun walk(widget: UiWidget) {
            if (!visible(widget)) return
            if (widget.type in FOCUSABLE && enabled(widget)) order += widget.id
            widget.children.forEach(::walk)
        }
        walk(root)
        return order
    }

    /** Enter / Space on the focused widget: a focused button runs its `click` action. */
    fun activateFocused(): UiNavOutcome {
        val id = focus ?: return UiNavOutcome.NoOp
        val root = layers().firstOrNull()?.let { rootProvider(it.ref) } ?: return UiNavOutcome.NoOp
        fun find(widget: UiWidget): UiWidget? = if (widget.id == id) widget else widget.children.firstNotNullOfOrNull(::find)
        val widget = find(root)?.takeIf { it.type == UiWidgetType.BUTTON } ?: return UiNavOutcome.NoOp
        return widget.actions["click"]?.let(::activate) ?: UiNavOutcome.NoOp
    }

    /** Sets focus by pointer; the id must be a focusable widget of the topmost layer is the caller's check. */
    fun focusOn(widgetId: String?) { focus = widgetId }
}
