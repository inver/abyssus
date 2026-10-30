/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.ui.ComboBox
import net.nevinsky.abyssus.lib.core.editor.pick.FoliagePaintMode
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.foliage.FoliagePaintSession
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JToggleButton
import javax.swing.SpinnerNumberModel

/**
 * The foliage brush of one Scene view: the Paint toggle with its layer, radius, strength and erase strip, and the paint
 * session of the selected foliage entity ([paintSession] opens one and calls back with it when ready, null when there is
 * none). While painting, the brush is [SceneRenderState.paint]; [onChanged] tells the view to bring its toolbar in line.
 * AWT thread.
 */
internal class FoliagePaintControls(
    private val renderer: SceneRenderer,
    private val interaction: SceneInteraction,
    private val play: PlayState,
    private val paintSession: ((String, () -> FoliagePaintMode?, (FoliagePaintSession?) -> Unit) -> Unit)?,
    private val onChanged: () -> Unit,
) {
    private val button = JToggleButton(AbyssusBundle.message("paintFoliageTitle")).apply { name = "paint-foliage" }
    private val layers = ComboBox<Int>().apply { name = "foliage-brush-layer" }
    private val radius = JSpinner(SpinnerNumberModel(10.0, 0.01, 100000.0, 1.0)).apply { name = "foliage-brush-radius" }
    private val strength = JSpinner(SpinnerNumberModel(1.0, 0.0, 1.0, 0.05)).apply { name = "foliage-brush-strength" }
    private val erase = JCheckBox(AbyssusBundle.message("foliageBrushErase")).apply { name = "foliage-brush-erase" }
    private val strip = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply { name = "foliage-brush-strip"; isVisible = false }
    private var loaded: FoliagePaintSession? = null
    private var entity: String? = null
    private var load = 0
    private var closed = false
    private var updatingLayers = false

    /** Whether a brush is on the view now. */
    val painting: Boolean get() = renderer.state.paint != null

    init {
        button.addActionListener {
            if (button.isSelected) update() else end()
            onChanged()
        }
        layers.addActionListener { if (!updatingLayers) update() }
        radius.addChangeListener { update() }
        strength.addChangeListener { update() }
        erase.addActionListener { update() }
        strip.add(JLabel(AbyssusBundle.message("foliageBrushLayer")))
        strip.add(layers)
        strip.add(JLabel(AbyssusBundle.message("foliageBrushRadius")))
        strip.add(radius)
        strip.add(JLabel(AbyssusBundle.message("foliageBrushStrength")))
        strip.add(strength)
        strip.add(erase)
    }

    /** Adds the Paint toggle and its strip to [toolbar] when the host can paint at all. */
    fun addTo(toolbar: JPanel) {
        if (paintSession != null) { toolbar.add(button); toolbar.add(strip) }
    }

    /** Before the toolbar is synced: leaves the brush while playing and follows the selection. */
    fun beforeSync() {
        if (play.active && painting) end()
        refreshSession()
    }

    /** Brings the Paint toggle and strip in line with the brush. */
    fun sync() {
        button.isEnabled = !play.active && loaded?.layers?.isNotEmpty() == true
        button.isSelected = painting
        strip.isVisible = painting
    }

    /** Opens the paint session of the selected entity, replacing the last one (always when [force]). */
    fun refreshSession(force: Boolean = false) {
        val selected = interaction.selectedId
        if (!force && selected == entity) return
        val wasPainting = painting && selected == entity
        end()
        loaded?.dispose()
        loaded = null
        entity = selected
        val token = ++load
        if (closed || selected == null) return
        paintSession?.invoke(selected, { renderer.state.paint }) { session ->
            if (closed || token != load || interaction.selectedId != selected) { session?.dispose(); return@invoke }
            loaded = session
            updatingLayers = true
            layers.model = DefaultComboBoxModel(session?.layers.orEmpty().toTypedArray())
            updatingLayers = false
            interaction.foliagePaint = session?.paint
            if (wasPainting && session?.layers?.isNotEmpty() == true) {
                button.isSelected = true
                update()
            }
            onChanged()
        }
    }

    /** Puts the brush on the view from the strip's values ([shift] erases); leaves it while playing. */
    fun update(shift: Boolean = false) {
        if (play.active) { end(); return }
        if (!button.isSelected || loaded == null) return
        val selected = interaction.selectedId ?: return
        val layer = layers.selectedItem as? Int ?: return
        renderer.state.paint = FoliagePaintMode(selected, layer, (radius.value as Number).toFloat(),
            (strength.value as Number).toFloat(), erase.isSelected || shift)
        strip.isVisible = true
    }

    fun end() {
        interaction.foliagePaint?.cancelled()
        renderer.state.paint = null
        renderer.brushCursor = null
        button.isSelected = false
        strip.isVisible = false
    }

    /** Releases the session when the view is disposed. */
    fun close() {
        closed = true
        load++
        end()
        loaded?.dispose()
    }
}
