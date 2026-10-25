/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.ui.ComboBox
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.FOLIAGE_BRUSH
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.foliage.FoliageBrushOptions
import net.nevinsky.abyssus.plugin.foliage.FoliagePaintSession
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton

/**
 * The foliage brush of one Scene view: the Paint toggle with its layer, radius, strength and erase strip, and the paint
 * session of the selected foliage entity ([paintSession] opens one and calls back with it when ready, null when there is
 * none). While painting, the brush is the view's [net.nevinsky.abyssus.lib.core.editor.pick.SceneViewState.brush] of kind
 * [FOLIAGE_BRUSH]; [onChanged] tells the view to bring its toolbar in line. A [BrushStrip]. AWT thread.
 */
internal class FoliagePaintControls(
    renderer: SceneRenderer,
    interaction: SceneInteraction,
    play: PlayState,
    kinds: BrushKinds,
    private val paintSession: ((String, () -> BrushMode?, (FoliagePaintSession?) -> Unit) -> Unit)?,
    onChanged: () -> Unit,
) : BrushStrip<FoliagePaintSession>(
    renderer, interaction, play, kinds, FOLIAGE_BRUSH, onChanged, "foliage-brush-radius", "foliage-brush-strength", "foliage-brush-strip",
) {
    private val button = JToggleButton(AbyssusBundle.message("paintFoliageTitle")).apply { name = "paint-foliage" }
    private val layers = ComboBox<Int>().apply { name = "foliage-brush-layer" }
    private val erase = JCheckBox(AbyssusBundle.message("foliageBrushErase")).apply { name = "foliage-brush-erase" }
    private var updatingLayers = false

    /** Whether a brush is on the view now. */
    val painting: Boolean get() = active

    override val wanted: Boolean get() = painting

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
    override fun addTo(toolbar: JPanel) {
        if (paintSession != null) { toolbar.add(button); toolbar.add(strip) }
    }

    /** Brings the Paint toggle and strip in line with the brush. */
    override fun sync() {
        button.isEnabled = !play.active && loaded?.layers?.isNotEmpty() == true
        button.isSelected = painting
        strip.isVisible = painting
    }

    override fun openSession(selected: String, mode: () -> BrushMode?, ready: (FoliagePaintSession?) -> Unit) {
        paintSession?.invoke(selected, mode, ready)
    }

    override fun disposeSession(session: FoliagePaintSession) = session.dispose()

    override fun strokeOf(session: FoliagePaintSession?): BrushStroke? = session?.paint

    override fun sessionLoaded(session: FoliagePaintSession?, wasWanted: Boolean) {
        updatingLayers = true
        layers.model = DefaultComboBoxModel(session?.layers.orEmpty().toTypedArray())
        updatingLayers = false
        if (wasWanted && session?.layers?.isNotEmpty() == true) {
            button.isSelected = true
            update()
        }
    }

    override fun canBrush(): Boolean = button.isSelected && loaded != null

    override fun modeFor(selected: String, shift: Boolean): BrushMode? {
        val layer = layers.selectedItem as? Int ?: return null
        return BrushMode(FOLIAGE_BRUSH, selected, (radius.value as Number).toFloat(),
            (strength.value as Number).toFloat(), erase.isSelected || shift, FoliageBrushOptions(layer))
    }

    override fun end(notify: Boolean) {
        super.end(notify)
        button.isSelected = false
    }
}
