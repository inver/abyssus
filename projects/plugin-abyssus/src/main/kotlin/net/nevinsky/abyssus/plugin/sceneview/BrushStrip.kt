/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * What the terrain brushes of a Scene view share: the radius and strength spinners and the strip that holds them, the
 * session of the selected entity (opened through [openSession] and replaced when the selection changes, with a token so
 * a late answer is dropped), the stroke it registers with the interaction, and the one brush mode the view holds.
 *
 * A strip puts its mode on the view only as its own [kind]; it takes the view's mode off only while that mode is its own;
 * and when another kind's mode has replaced its own (or the view is playing) its next [beforeSync] ends it. The kinds
 * add their controls, their session and the mode they build. AWT thread.
 */
internal abstract class BrushStrip<S : Any>(
    protected val renderer: SceneRenderer,
    protected val interaction: SceneInteraction,
    protected val play: PlayState,
    protected val kinds: BrushKinds,
    private val kind: String,
    private val onChanged: () -> Unit,
    radiusName: String,
    strengthName: String,
    stripName: String,
) {
    protected val radius = spinner(radiusName, 10.0, 0.01, 100000.0, 1.0)
    protected val strength = spinner(strengthName, 1.0, 0.0, 1.0, 0.05)
    protected val strip = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply { name = stripName; isVisible = false }

    /** The session of the selected entity, or null while none is loaded (or the entity has none). */
    protected var loaded: S? = null
    protected var closed = false
    private var entity: String? = null
    private var load = 0

    /** Whether this strip put its mode on the view and has not taken it off. */
    private var onView = false

    /** The mode on the view when it is this kind's, else null. */
    protected val ownBrush: BrushMode? get() = renderer.state.brush?.takeIf { it.kind == kind }

    /** Whether this kind's brush is on the view now. */
    protected val active: Boolean get() = renderer.state.brush?.kind == kind

    /** Whether this kind's brush is on the view now: what starting another kind ends. */
    val holdsView: Boolean get() = active

    /** Adds the strip (and its toggle, when the toolbar holds it) to [toolbar] when the host can brush at all. */
    abstract fun addTo(toolbar: JPanel)

    /** Brings the toggle and strip in line with the brush. */
    abstract fun sync()

    /** Whether the user asked for this brush, so a reloaded session puts it back (the toggle is on). */
    protected abstract val wanted: Boolean

    /** Opens the session of [selected] (the mode is read by its stroke) and calls [ready] with it, null when there is none. */
    protected abstract fun openSession(selected: String, mode: () -> BrushMode?, ready: (S?) -> Unit)

    protected abstract fun disposeSession(session: S)

    protected abstract fun strokeOf(session: S?): BrushStroke?

    /** A session just arrived and its stroke is registered; [wasWanted] says the brush was on for this entity before the reload. */
    protected abstract fun sessionLoaded(session: S?, wasWanted: Boolean)

    /** Whether [update] may put the brush on the view now. */
    protected abstract fun canBrush(): Boolean

    /** The mode for [selected] from the strip's values ([shift] is held), or null when the strip cannot build one. */
    protected abstract fun modeFor(selected: String, shift: Boolean): BrushMode?

    /** The loaded session was dropped for a reload. */
    protected open fun sessionCleared() {}

    /** Before the toolbar is synced: leaves the brush while playing or when another kind took the view, follows the selection. */
    fun beforeSync() {
        val replaced = renderer.state.brush?.let { it.kind != kind } == true
        if (onView && (play.active || replaced)) end()
        refreshSession()
    }

    /** Opens the session of the selected entity, replacing the last one (always when [force]). */
    fun refreshSession(force: Boolean = false) {
        val selected = interaction.selectedId
        if (!force && selected == entity) return
        val wasWanted = wanted && selected == entity
        end(notify = false)
        loaded?.let(::disposeSession)
        loaded = null
        sessionCleared()
        entity = selected
        val token = ++load
        if (closed || selected == null) return
        openSession(selected, { ownBrush }) { session ->
            if (closed || token != load || interaction.selectedId != selected) {
                session?.let(::disposeSession)
                return@openSession
            }
            loaded = session
            interaction.setBrushStroke(kind, strokeOf(session))
            sessionLoaded(session, wasWanted)
            onChanged()
        }
    }

    /** Puts the brush on the view from the strip's values ([shift] flips it); leaves it while playing. */
    fun update(shift: Boolean = false) {
        if (play.active) { end(); return }
        if (!canBrush()) return
        val selected = interaction.selectedId ?: return
        val mode = modeFor(selected, shift) ?: return
        kinds.endOthers(this)
        renderer.state.brush = mode
        onView = true
        strip.isVisible = true
    }

    /** Takes the brush off the view when it is this kind's; [notify] is for kinds that tell their host (not when the session is only reloaded). */
    open fun end(notify: Boolean = true) {
        interaction.brushStroke(kind)?.cancelled()
        if (active) renderer.state.brush = null
        onView = false
        renderer.brushCursor = null
        strip.isVisible = false
    }

    /** Releases the session when the view is disposed. */
    open fun close() {
        closed = true
        load++
        end()
        loaded?.let(::disposeSession)
    }

    protected fun spinner(name: String, value: Double, min: Double, max: Double, step: Double) =
        JSpinner(SpinnerNumberModel(value, min, max, step)).apply { this.name = name }
}

/**
 * The terrain brushes of one Scene view, in toolbar order. Starting one ends the others, since the view holds one brush
 * mode at a time; a brush added later registers here and is built into the toolbar, synced and closed with the rest.
 */
internal class BrushKinds {
    private val all = ArrayList<BrushStrip<*>>()

    /** The registered brushes, in toolbar order. */
    val strips: List<BrushStrip<*>> get() = all

    fun register(strip: BrushStrip<*>) {
        all += strip
    }

    /** Whether any brush has its mode on the view. */
    val brushing: Boolean get() = all.any { it.holdsView }

    /** Ends every brush but [starting] that has its mode on the view. */
    fun endOthers(starting: BrushStrip<*>) {
        for (strip in all) if (strip !== starting && strip.holdsView) strip.end()
    }
}
