/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.ui.render

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.HdpiUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.FitViewport
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutDiagnostic
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutEngine
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutResult
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.state.*
import net.nevinsky.abyssus.lib.core.ui.style.UiResolvedStyle
import net.nevinsky.abyssus.lib.core.ui.style.UiStyleResolver
import net.nevinsky.abyssus.lib.core.ui.style.UiVisualState

private val CLICKABLE = setOf(UiWidgetType.PANEL, UiWidgetType.ROW, UiWidgetType.COLUMN)

private val INTERACTIVE =
    setOf(UiWidgetType.BUTTON, UiWidgetType.SLIDER, UiWidgetType.CHECKBOX, UiWidgetType.TEXT_FIELD)

/**
 * Presents one UI collection: layers (screen and overlays) laid out with [UiLayoutEngine], drawn through one Scene2D
 * stage per layer, with pointer and keyboard input turned into binding writes and navigation. The editor preview and
 * standalone games both use it, so layout, styling and input behave the same. Construction and [dispose] need a GL
 * context (a batch and a pixel texture); host input and [render] run on the GL thread. It owns neither [resources]
 * nor the session callbacks.
 */
class UiPresenter(
    assetName: String,
    private val collection: UiCollection,
    source: UiCollectionSource,
    private val session: UiBindingSession,
    actions: UiActionSink,
    override val resources: UiRenderResources,
    json: JsonProcessor,
    showEntry: Boolean = true,
    kit: UiGlKit? = null,
) : UiPaintContext {
    val navigator = UiNavigator(assetName, collection, source, actions, showEntry).also { it.rootProvider = { ref -> expanded[ref] ?: it.screenOf(ref)?.root } }
    private val styles = UiStyleResolver(json)
    private val text = UiText { this }
    private val painter = UiPainter({ this }, text)
    private val engine = UiLayoutEngine(UiWidgetMeasurer({ this }, text))
    private val ownsKit = kit == null
    private val glKit = kit ?: UiGlKit()
    private val batch = glKit.batch
    override val pixel: Texture = glKit.pixel

    /** What an editor draws on top of the screen (selection, hover, a drag's drop target); null for a plain presentation. */
    var overlay: UiEditOverlay? = null

    private var published: UiSnapshot? = null
    private var forcePublish = false
    private val expanded = HashMap<UiScreenRef, UiWidget>()
    private var focusFirst = showEntry
    private var freshFocus: String? = null

    /** Problems found while expanding lists (a row limit hit), for the host's diagnostics. */
    var listDiagnostics: List<UiBindingDiagnostic> = emptyList()
        private set

    private var width = 0
    private var height = 0
    private var dirty = true
    private val layouts = HashMap<UiScreenRef, UiLayoutResult>()
    private val stages = HashMap<UiScreenRef, Stage>()
    private val actors = HashMap<UiScreenRef, Map<String, UiWidgetActor>>()
    private val hidden = HashSet<String>()
    private val widgets = HashMap<UiScreenRef, Map<String, UiWidget>>()

    private var hovered: String? = null
    private var pressed: String? = null
    private val dragValues = HashMap<String, Double>()
    private val buffers = HashMap<String, String>()
    private val rejections = HashMap<String, String>()

    /** The last navigation problem (missing target, unhandled action), for the host's diagnostics. */
    var lastNavigationFailure: UiNavDiagnostic? = null
        private set

    override val view: UiBindingView get() = session.view()

    // --- paint context ---

    override fun style(widget: UiWidget): UiResolvedStyle = styles.resolve(collection, widget, states(widget), view.styleName(widget))

    override fun states(widget: UiWidget): Set<UiVisualState> = buildSet {
        if (!session.view().enabled(widget)) add(UiVisualState.DISABLED)
        if (navigator.focus == widget.id) add(UiVisualState.FOCUSED)
        if (hovered == widget.id) add(UiVisualState.HOVER)
        if (pressed == widget.id) add(UiVisualState.PRESSED)
    }

    override fun fieldText(widget: UiWidget): String = buffers[widget.id] ?: view.text(widget)

    override fun sliderValue(widget: UiWidget): Double = dragValues[widget.id] ?: view.number(widget, "value", 0.0)

    override fun rejected(widget: UiWidget): Boolean = widget.id in rejections

    override fun shown(widget: UiWidget): Boolean = widget.id !in hidden

    fun rejectionReason(widgetId: String): String? = rejections[widgetId]

    // --- lifecycle and layout ---

    fun resize(width: Int, height: Int) {
        if (width == this.width && height == this.height) return
        this.width = width; this.height = height
        invalidate()
    }

    /** Layout must be recomputed: a new snapshot (text may change size), a resize or a changed style. */
    fun invalidate() {
        dirty = true
    }

    /** Takes the host's values for this update; nothing is laid out again when they are the ones already shown. */
    fun publish(snapshot: UiSnapshot) {
        if (snapshot == published && !forcePublish) return
        published = snapshot
        forcePublish = false
        session.publish(snapshot)
        invalidate()
    }

    /** The resolved layout of [ref] for the current viewport; null before the first [resize] or for an unknown screen. */
    fun layout(ref: UiScreenRef): UiLayoutResult? {
        relayout(); return layouts[ref]
    }

    fun layoutDiagnostics(): List<UiLayoutDiagnostic> {
        relayout(); return layouts.values.flatMap { it.diagnostics }
    }

    fun diagnostics(): List<UiBindingDiagnostic> = session.view().diagnostics

    /** The Scene2D bounds (y-up) of a widget's actor, for tests and tooling. */
    fun actorBounds(ref: UiScreenRef, widgetId: String): Rectangle? {
        relayout(); return actors[ref]?.get(widgetId)?.let { Rectangle(it.x, it.y, it.width, it.height) }
    }

    /** The viewport rectangle (bottom-left origin) the layer's stage draws into. */
    fun viewportBounds(ref: UiScreenRef): Rectangle? {
        relayout()
        return stages[ref]?.viewport?.let {
            Rectangle(
                it.screenX.toFloat(),
                it.screenY.toFloat(),
                it.screenWidth.toFloat(),
                it.screenHeight.toFloat()
            )
        }
    }

    private fun relayout() {
        if (!dirty || width <= 0 || height <= 0) return
        dirty = false
        val live = navigator.layers().map { it.ref }.toSet()
        (stages.keys - live).forEach {
            stages.remove(it)?.dispose(); actors.remove(it); layouts.remove(it); widgets.remove(it); expanded.remove(it)
        }
        listDiagnostics = emptyList()
        for (ref in live) {
            val authored = collection.screenFor(ref, navigator) ?: continue
            val expansion = expandLists(authored.root, view)
            listDiagnostics = (listDiagnostics + expansion.diagnostics).distinct()
            expanded[ref] = expansion.root
            val screen = authored.copy(root = expansion.root)
            val result = engine.resolve(screen, width.toFloat(), height.toFloat())
            layouts[ref] = result
            widgets[ref] = index(screen.root)
            val stage =
                stages.getOrPut(ref) { Stage(FitViewport(screen.referenceWidth, screen.referenceHeight), batch) }
            stage.viewport.update(width, height, true)
            val byId = actors[ref]?.takeIf { it.keys == result.nodes.keys }?.also { known -> known.forEach { (id, actor) -> actor.widget = widgets.getValue(ref).getValue(id) } } ?: run {
                stage.clear()
                result.order.associateWith { id ->
                    UiWidgetActor(
                        widgets.getValue(ref).getValue(id),
                        painter
                    ) { this }.also { actor ->
                        actor.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.disabled; stage.addActor(actor)
                    }
                }
                    .also { actors[ref] = it }
            }
            result.nodes.forEach { (id, node) -> byId.getValue(id).update(node, screen.referenceHeight) }
        }
    }

    private fun index(root: UiWidget): Map<String, UiWidget> {
        val map = LinkedHashMap<String, UiWidget>()
        fun walk(w: UiWidget) {
            map[w.id] = w; w.children.forEach(::walk)
        }
        walk(root)
        return map
    }

    /** Whether the top layer's screen moves focus with Up and Down. */
    private fun arrows(): Boolean = navigator.layers().firstOrNull()?.let { navigator.screenOf(it.ref)?.navigation == UiNavigation.ARROWS } == true

    private fun refreshHidden() {
        hidden.clear()
        fun walk(w: UiWidget, parentHidden: Boolean) {
            val hide = parentHidden || !view.visible(w)
            if (hide) hidden += w.id
            w.children.forEach { walk(it, hide) }
        }
        navigator.layers().forEach { layer -> expanded[layer.ref]?.let { walk(it, false) } }
    }

    /** Draws all layers bottom to top. GL thread, on screen. */
    fun render(delta: Float = 0f) {
        relayout()
        refreshHidden()
        settleFocus()
        for (layer in navigator.layers().asReversed()) {
            val stage = stages[layer.ref] ?: continue
            stage.viewport.update(width, height, true)
            fillBars(layer.ref, stage)
            stage.act(delta)
            stage.draw()
            overlay?.let { drawOverlay(it, layer.ref, stage) }
        }
    }

    /**
     * Extends a full-screen root's background over the letterbox bars the fit leaves, so a backdrop or scrim covers the
     * whole window rather than only the reference rectangle. Leaves the stage's viewport applied.
     */
    private fun fillBars(ref: UiScreenRef, stage: Stage) {
        val root = widgets[ref]?.values ?: return
        val layout = layouts[ref] ?: return
        val fit = layout.fit ?: return
        // the first widget, parent first, that covers the whole reference rectangle and paints a background
        val rgba = root.firstNotNullOfOrNull { widget ->
            val bounds = layout.nodes[widget.id]?.bounds ?: return@firstNotNullOfOrNull null
            val covers = bounds.x <= 0f && bounds.y <= 0f && bounds.right >= fit.referenceWidth && bounds.bottom >= fit.referenceHeight
            if (covers && widget.type != UiWidgetType.SLIDER && widget.type != UiWidgetType.PROGRESS_BAR) style(widget).color("background")?.takeIf { it and 0xff != 0 } else null
        } ?: return
        val barX = stage.viewport.screenX.toFloat()
        val barY = stage.viewport.screenY.toFloat()
        val fittedW = stage.viewport.screenWidth.toFloat()
        val fittedH = stage.viewport.screenHeight.toFloat()
        if (barX <= 0f && barY <= 0f) return
        HdpiUtils.glViewport(0, 0, width, height)
        batch.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, width.toFloat(), height.toFloat())
        val color = Color(); Color.rgba8888ToColor(color, rgba); batch.setColor(color)
        batch.begin()
        if (barX > 0f) {
            batch.draw(pixel, 0f, 0f, barX, height.toFloat())
            batch.draw(pixel, barX + fittedW, 0f, width - barX - fittedW, height.toFloat())
        }
        if (barY > 0f) {
            batch.draw(pixel, 0f, 0f, width.toFloat(), barY)
            batch.draw(pixel, 0f, barY + fittedH, width.toFloat(), height - barY - fittedH)
        }
        batch.end()
        batch.setColor(Color.WHITE)
        stage.viewport.apply(true)
    }

    private fun drawOverlay(overlay: UiEditOverlay, ref: UiScreenRef, stage: Stage) {
        val layout = layouts[ref] ?: return
        val referenceHeight = stage.viewport.worldHeight
        batch.projectionMatrix = stage.camera.combined
        batch.begin()
        fun frame(rect: com.badlogic.gdx.math.Rectangle, rgba: Int, thickness: Float) {
            val c = Color(); Color.rgba8888ToColor(c, rgba); batch.setColor(c)
            batch.draw(pixel, rect.x, rect.y, rect.width, thickness)
            batch.draw(pixel, rect.x, rect.y + rect.height - thickness, rect.width, thickness)
            batch.draw(pixel, rect.x, rect.y, thickness, rect.height)
            batch.draw(pixel, rect.x + rect.width - thickness, rect.y, thickness, rect.height)
        }

        fun rectOf(r: net.nevinsky.abyssus.lib.core.ui.layout.UiRect) =
            com.badlogic.gdx.math.Rectangle(r.x, referenceHeight - r.bottom, r.width, r.height)
        if (overlay.outlines) layout.nodes.values.forEach { frame(rectOf(it.visible), 0xffffff30.toInt(), 1f) }
        // a widget inside a list's row has one copy per row; the frame goes on every copy
        overlay.hoverId?.let { id -> layout.nodes.filterKeys { uiBaseId(it) == id }.values.forEach { frame(rectOf(it.visible), 0x2d8cff90.toInt(), 1f) } }
        overlay.selectedId?.let { id -> layout.nodes.filterKeys { uiBaseId(it) == id }.values.forEach { frame(rectOf(it.bounds), 0x2d8cffff.toInt(), 2f) } }
        overlay.ghost?.let { frame(rectOf(it), 0xffcc33ff.toInt(), 2f) }
        batch.setColor(Color.WHITE)
        batch.end()
    }

    /** Shows [screenId] alone (see [UiNavigator.display]); false for an unknown screen. */
    fun display(screenId: String, assetName: String): Boolean {
        val shown = navigator.display(UiScreenRef(assetName, screenId))
        if (shown) {
            clearTransient(); invalidate(); focusFirst = true
        } else lastNavigationFailure = UiNavDiagnostic(UiNavIssue.MISSING_TARGET, "$assetName:$screenId")
        return shown
    }

    /** Releases the stages; the batch and pixel too unless they came from a shared [UiGlKit], whose owner disposes them. */
    fun dispose() {
        stages.values.forEach(Stage::dispose)
        stages.clear(); actors.clear()
        if (ownsKit) glKit.dispose()
    }

    // --- pointer input (screen coordinates, top-left origin) ---

    private fun interactiveAt(layer: UiLayer, x: Float, y: Float): String? {
        val layout = layouts[layer.ref] ?: return null
        val fit = layout.fit ?: return null
        val lx = fit.toLogicalX(x);
        val ly = fit.toLogicalY(y)
        val ids = widgets[layer.ref] ?: return null
        for (id in layout.order.asReversed()) {
            if (!layout.nodes.getValue(id).visible.contains(lx, ly)) continue
            val widget = ids.getValue(id)
            if (interactive(widget) && id !in hidden && view.enabled(widget)) return id
        }
        return null
    }

    private fun prepare() {
        relayout(); refreshHidden(); settleFocus()
    }

    /** An arrow-key screen starts with its first usable control focused. */
    private fun settleFocus() {
        if (!focusFirst || width <= 0) return
        focusFirst = false
        if (arrows()) { navigator.moveFocus(true, { view.visible(it) }, { view.enabled(it) }); freshFocus = navigator.focus }
    }

    fun pointerMoved(x: Float, y: Float) {
        prepare()
        hovered = (navigator.route { interactiveAt(it, x, y) } as? UiRoute.Handled)?.widgetId
    }

    /** True when the UI consumed the press; false hands it to the game. */
    fun touchDown(x: Float, y: Float): Boolean {
        prepare()
        val route = navigator.route { interactiveAt(it, x, y) }
        val id = (route as? UiRoute.Handled)?.widgetId
        if (id != navigator.focus) commitFocused(UiEditGesture.FOCUS_LOST)
        if (route !is UiRoute.Handled) {
            navigator.focusOn(null); return route == UiRoute.Blocked
        }
        val widget = widget(route.layer.ref, id!!) ?: return true
        navigator.focusOn(id)
        freshFocus = id
        pressed = id
        when (widget.type) {
            UiWidgetType.SLIDER -> dragSlider(route.layer.ref, widget, x)
            UiWidgetType.TEXT_FIELD -> buffers.getOrPut(id) { view.text(widget) }
            else -> Unit
        }
        return true
    }

    fun touchDragged(x: Float, y: Float) {
        val id = pressed ?: return
        val layer = navigator.layers().firstOrNull { widgets[it.ref]?.containsKey(id) == true } ?: return
        widget(layer.ref, id)?.takeIf { it.type == UiWidgetType.SLIDER }?.let { dragSlider(layer.ref, it, x) }
    }

    fun touchUp(x: Float, y: Float): Boolean {
        val id = pressed ?: return false
        pressed = null
        prepare()
        val route = navigator.route { interactiveAt(it, x, y) } as? UiRoute.Handled
        val ref = route?.layer?.ref ?: navigator.layers().firstOrNull { widgets[it.ref]?.containsKey(id) == true }?.ref
        ?: return true
        val widget = widget(ref, id) ?: return true
        val over = route?.widgetId == id
        when (widget.type) {
            UiWidgetType.BUTTON, UiWidgetType.PANEL, UiWidgetType.ROW, UiWidgetType.COLUMN -> if (over) widget.actions["click"]?.let(::activate)
            UiWidgetType.CHECKBOX -> if (over) commit(
                widget,
                UiValue.Bool(!view.flag(widget, "checked", false)),
                UiEditGesture.TOGGLE
            )

            UiWidgetType.SLIDER -> dragValues.remove(id)?.let { commit(widget, UiValue.Num(it), UiEditGesture.RELEASE) }
            else -> Unit
        }
        return true
    }

    private fun dragSlider(ref: UiScreenRef, widget: UiWidget, x: Float) {
        val layout = layouts[ref] ?: return
        val fit = layout.fit ?: return
        val bounds = layout.nodes[widget.id]?.bounds ?: return
        val range = view.range(widget)
        val fraction = ((fit.toLogicalX(x) - bounds.x) / bounds.width).coerceIn(0f, 1f)
        dragValues[widget.id] = range.start + (range.endInclusive - range.start) * fraction
    }

    // --- keyboard input ---

    /** True when the UI used the key. Escape is offered to the UI last, so a host that wants Escape first handles it before calling. */
    fun keyDown(keycode: Int, shift: Boolean = false): Boolean {
        prepare()
        return when (keycode) {
            Input.Keys.TAB -> {
                commitFocused(UiEditGesture.FOCUS_LOST)
                (navigator.moveFocus(!shift, { view.visible(it) }, { view.enabled(it) }) != null).also { freshFocus = navigator.focus }
            }

            Input.Keys.UP, Input.Keys.DOWN -> arrows() && run {
                commitFocused(UiEditGesture.FOCUS_LOST)
                (navigator.moveFocus(keycode == Input.Keys.DOWN, { view.visible(it) }, { view.enabled(it) }) != null).also { freshFocus = navigator.focus }
            }

            Input.Keys.ESCAPE -> navigator.escape().also {
                if (it == UiNavOutcome.Navigated) {
                    invalidate(); clearTransient()
                }
            } != UiNavOutcome.NoOp

            Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> focusedWidget()?.let { w ->
                when (w.type) {
                    UiWidgetType.BUTTON -> {
                        record(navigator.activateFocused()); true
                    }

                    UiWidgetType.TEXT_FIELD -> {
                        commitFocused(UiEditGesture.ENTER); true
                    }

                    else -> false
                }
            } ?: false

            Input.Keys.SPACE -> focusedWidget()?.let { w ->
                when (w.type) {
                    UiWidgetType.BUTTON -> {
                        record(navigator.activateFocused()); true
                    }

                    UiWidgetType.CHECKBOX -> {
                        commit(w, UiValue.Bool(!view.flag(w, "checked", false)), UiEditGesture.TOGGLE); true
                    }

                    else -> false
                }
            } ?: false

            Input.Keys.LEFT, Input.Keys.RIGHT -> focusedWidget()?.takeIf { it.type == UiWidgetType.SLIDER }?.let { w ->
                val range = view.range(w)
                val step =
                    w.bindings["value"]?.let(collection::binding)?.step ?: ((range.endInclusive - range.start) / 20.0)
                commit(
                    w,
                    UiValue.Num(
                        view.number(
                            w,
                            "value",
                            range.start
                        ) + if (keycode == Input.Keys.RIGHT) step else -step
                    ),
                    UiEditGesture.KEY_STEP
                )
                true
            } ?: false

            Input.Keys.BACKSPACE -> editFocused { it.dropLast(1) }
            else -> false
        }
    }

    /** Character typed into the focused text field; control characters are ignored. */
    fun keyTyped(char: Char): Boolean = if (char.isISOControl()) false else editFocused { it + char }

    /** Controls, and panels / rows / columns that carry a click action (a whole list row can be one target). */
    private fun interactive(widget: UiWidget): Boolean =
        widget.type in INTERACTIVE || (widget.type in CLICKABLE && widget.actions.containsKey("click"))

    private fun editFocused(change: (String) -> String): Boolean {
        val w = focusedWidget()?.takeIf {
            it.type == UiWidgetType.TEXT_FIELD && session.plan.writeTarget(
                collection,
                it
            ) != null
        } ?: return false
        // a field that selects its text on focus is replaced by the first edit after it gained focus
        val fresh = w.id == freshFocus && w.properties["selectOnFocus"]?.asBoolean() == true
        buffers[w.id] = change(if (fresh) "" else buffers[w.id] ?: view.text(w))
        freshFocus = null
        return true
    }

    // --- internals ---

    private fun widget(ref: UiScreenRef, id: String): UiWidget? = widgets[ref]?.get(id)

    private fun focusedWidget(): UiWidget? {
        val id = navigator.focus ?: return null
        return navigator.layers().firstNotNullOfOrNull { widgets[it.ref]?.get(id) }
    }

    private fun clearTransient() {
        hovered = null; pressed = null; dragValues.clear(); buffers.clear()
    }

    private fun activate(action: UiAction) {
        record(navigator.activate(action))
        if (navigator.layers().map { it.ref }.toSet() != layouts.keys) {
            invalidate(); clearTransient()
        }
    }

    private fun record(outcome: UiNavOutcome) {
        lastNavigationFailure = (outcome as? UiNavOutcome.Failed)?.diagnostic
            ?: lastNavigationFailure.takeIf { outcome == UiNavOutcome.NoOp }
        if (outcome == UiNavOutcome.Navigated) {
            invalidate(); clearTransient()
        }
    }

    private fun commitFocused(gesture: UiEditGesture) {
        val w = focusedWidget() ?: return
        if (w.type != UiWidgetType.TEXT_FIELD) return
        val typed = buffers[w.id] ?: return
        if (commitsOn(UiWidgetType.TEXT_FIELD, gesture)) commit(w, UiValue.Text(typed), gesture)
        if (gesture == UiEditGesture.FOCUS_LOST) buffers.remove(w.id)
    }

    private fun commit(widget: UiWidget, value: UiValue, gesture: UiEditGesture) {
        if (!commitsOn(widget.type ?: return, gesture)) return
        when (val outcome = session.commit(widget, value)) {
            is UiCommitOutcome.Applied -> {
                rejections.remove(widget.id); buffers.remove(widget.id); invalidate()
                forcePublish = true // the host's next snapshot stays authoritative over what this write showed
            }

            is UiCommitOutcome.Rejected -> {
                rejections[widget.id] = outcome.reason; buffers.remove(widget.id)
            }

            is UiCommitOutcome.Refused -> buffers.remove(widget.id)
        }
    }
}

private fun UiCollection.screenFor(ref: UiScreenRef, navigator: UiNavigator): UiScreen? =
    if (navigator.layers().any { it.ref == ref }) navigator.screenOf(ref) else null
