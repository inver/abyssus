/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.ui.ComboBox
import com.intellij.util.concurrency.AppExecutorUtil
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.pick.SCULPT_BRUSH
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptTool
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.StampBlend
import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.StampImage
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.terrain.SculptSession
import net.nevinsky.abyssus.plugin.terrain.SculptToolFactory
import net.nevinsky.abyssus.plugin.terrain.SculptToolKind
import net.nevinsky.abyssus.plugin.terrain.SculptToolValues
import net.nevinsky.abyssus.plugin.terrain.StampImageChoice
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities

/**
 * The Sculpt Terrain brush of one Scene view: the toggle, and the strip with the tool, radius, strength and the options of
 * the chosen tool. [sculptSession] opens the session of the selected entity (why it cannot be sculpted, and the stroke) and
 * calls back with it, null when there is no terrain. While sculpting, the brush is the view's
 * [net.nevinsky.abyssus.lib.core.editor.pick.SceneViewState.brush] of kind [SCULPT_BRUSH]; starting it ends the other
 * brushes of [kinds], since the view holds one brush at a time; [onChanged] tells the view to bring its toolbar in line.
 * A [BrushStrip]. AWT thread.
 */
internal class SculptTerrainControls(
    renderer: SceneRenderer,
    interaction: SceneInteraction,
    play: PlayState,
    kinds: BrushKinds,
    private val sculptSession: ((String, () -> BrushMode?, () -> SculptTool?, (SculptSession?) -> Unit) -> Unit)?,
    private val tools: SculptToolFactory,
    private val onChanged: () -> Unit,
    private val onEnded: () -> Unit,
    private val report: (String) -> Unit,
) : BrushStrip<SculptSession>(
    renderer, interaction, play, kinds, SCULPT_BRUSH, onChanged, "sculpt-radius", "sculpt-strength", "sculpt-strip",
) {
    private val toolBox = ComboBox(DefaultComboBoxModel(SculptToolKind.entries.toTypedArray())).apply {
        name = "sculpt-tool"
        this.renderer = javax.swing.ListCellRenderer { list, value, index, selected, focus ->
            javax.swing.DefaultListCellRenderer().getListCellRendererComponent(
                list, value?.let { AbyssusBundle.message("sculptTool.${it.name}") }, index, selected, focus,
            )
        }
    }
    private val targetHeight = spinner("sculpt-height", 0.0, -100000.0, 100000.0, 1.0)
    private val amplitude = spinner("sculpt-amplitude", 5.0, 0.0, 100000.0, 0.5)
    private val featureSize = spinner("sculpt-feature-size", 40.0, 0.01, 100000.0, 1.0)
    private val seed = spinner("sculpt-seed", 1.0, -2147483648.0, 2147483647.0, 1.0)
    private val maxAngle = spinner("sculpt-angle", 35.0, 1.0, 89.0, 1.0)
    private val talus = spinner("sculpt-talus", 30.0, 1.0, 89.0, 1.0)
    private val iterations = spinner("sculpt-iterations", 8.0, 1.0, 64.0, 1.0)
    private val stampSize = spinner("sculpt-stamp-size", 100.0, 0.01, 100000.0, 1.0)
    private val stampHeight = spinner("sculpt-stamp-height", 20.0, -100000.0, 100000.0, 1.0)
    private val stampRotation = spinner("sculpt-stamp-rotation", 0.0, -360.0, 360.0, 5.0)
    private val stampBlend = ComboBox(DefaultComboBoxModel(StampBlend.entries.toTypedArray())).apply {
        name = "sculpt-stamp-blend"
        this.renderer = javax.swing.ListCellRenderer { list, value, index, selected, focus ->
            javax.swing.DefaultListCellRenderer().getListCellRendererComponent(
                list, value?.let { AbyssusBundle.message("sculptBlend.${it.name}") }, index, selected, focus,
            )
        }
    }
    private val stampChoice = ComboBox<StampImageChoice>().apply { name = "sculpt-stamp-image" }
    private val stampNote = JLabel("").apply { name = "sculpt-stamp-note" }
    private val options = JPanel(CardLayout()).apply { name = "sculpt-options" }
    private var requested = false
    private var opened = false
    private var stampImage: StampImage? = null
    private var stampDecode = 0
    private var updatingStamp = false

    /** Whether a sculpt brush is on the view now. */
    val sculpting: Boolean get() = active

    override val wanted: Boolean get() = requested

    init {
        toolBox.addActionListener { showOptions(); update() }
        for (s in listOf(radius, strength, targetHeight, amplitude, featureSize, seed, maxAngle, talus, iterations, stampSize, stampHeight, stampRotation)) {
            s.addChangeListener { update() }
        }
        stampBlend.addActionListener { update() }
        stampChoice.addActionListener { if (!updatingStamp) chooseStamp() }
        strip.add(toolBox)
        strip.add(JLabel(AbyssusBundle.message("sculptRadius")))
        strip.add(radius)
        strip.add(JLabel(AbyssusBundle.message("sculptStrength")))
        strip.add(strength)
        SculptToolKind.entries.forEach { options.add(optionsOf(it), it.name) }
        strip.add(options)
        strip.add(JLabel(AbyssusBundle.message("sculptShiftHint")))
        showOptions()
    }

    private fun optionsOf(kind: SculptToolKind): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        fun field(key: String, c: java.awt.Component) { add(JLabel(AbyssusBundle.message(key))); add(c) }
        when (kind) {
            SculptToolKind.SET_HEIGHT -> field("sculptHeight", targetHeight)
            SculptToolKind.NOISE -> { field("sculptAmplitude", amplitude); field("sculptFeatureSize", featureSize); field("sculptSeed", seed) }
            SculptToolKind.SLOPE_LIMIT -> field("sculptAngle", maxAngle)
            SculptToolKind.EROSION -> { field("sculptTalus", talus); field("sculptIterations", iterations) }
            SculptToolKind.STAMP -> {
                field("sculptStampImage", stampChoice); field("sculptStampSize", stampSize); field("sculptStampHeight", stampHeight)
                field("sculptStampRotation", stampRotation); field("sculptStampBlend", stampBlend); add(stampNote)
            }
            else -> Unit
        }
    }

    private fun showOptions() {
        (options.layout as CardLayout).show(options, (toolBox.selectedItem as SculptToolKind).name)
    }

    private fun values() = SculptToolValues(
        height = num(targetHeight), amplitude = num(amplitude), featureSize = num(featureSize), seed = num(seed).toInt(),
        maxAngle = num(maxAngle), talus = num(talus), iterations = num(iterations).toInt(), stampSize = num(stampSize),
        stampHeight = num(stampHeight), stampRotation = num(stampRotation),
        stampBlend = stampBlend.selectedItem as StampBlend, stampImage = stampImage,
    )

    /** The tool the strip stands for now; null for a Stamp without a readable image. */
    fun tool(): SculptTool? = tools.create(toolBox.selectedItem as SculptToolKind, values())

    /** Adds the strip to [toolbar] when the host can sculpt at all; the toggle is in the Properties panel. */
    override fun addTo(toolbar: JPanel) {
        if (sculptSession != null) toolbar.add(strip)
    }

    /** Puts the brush on [entityId], the selected terrain (the Properties panel's toggle); ends the sculpting when it cannot be. */
    fun begin(entityId: String) {
        if (play.active || entityId != interaction.selectedId) { end(); return }
        kinds.endOthers(this)
        requested = true
        val session = loaded
        when {
            session?.stroke != null -> { update(); onChanged() }
            session?.reason != null -> { end(); report(session.reason) }
            opened -> end()
        }
    }

    /** Brings the strip in line with the brush. */
    override fun sync() {
        strip.isVisible = sculpting
    }

    override fun openSession(selected: String, mode: () -> BrushMode?, ready: (SculptSession?) -> Unit) {
        sculptSession?.invoke(selected, mode, ::tool, ready)
    }

    override fun disposeSession(session: SculptSession) = session.dispose()

    override fun strokeOf(session: SculptSession?): BrushStroke? = session?.stroke

    override fun sessionCleared() {
        opened = false
    }

    override fun sessionLoaded(session: SculptSession?, wasWanted: Boolean) {
        opened = true
        listStampImages(session)
        // the toggle may have been pressed while the session loaded: [begin] has set [requested] since
        requested = requested || wasWanted
        when {
            requested && session?.stroke != null -> update()
            requested -> { end(); session?.reason?.let(report) }
        }
    }

    override fun canBrush(): Boolean = requested && loaded?.stroke != null

    override fun modeFor(selected: String, shift: Boolean): BrushMode =
        BrushMode(SCULPT_BRUSH, selected, num(radius), num(strength).coerceIn(0f, 1f), shift)

    /** Takes the brush off the view; [notify] tells the host the sculpting is over (not when the session is only reloaded). */
    override fun end(notify: Boolean) {
        val was = requested
        super.end(notify)
        requested = false
        if (notify && was) onEnded()
    }

    /** Releases the session when the view is disposed. */
    override fun close() {
        stampDecode++
        super.close()
    }

    private fun listStampImages(session: SculptSession?) {
        val dir = session?.assetsDir
        if (session == null) return
        val token = ++stampDecode
        AppExecutorUtil.getAppExecutorService().execute {
            val found = dir?.let { session.images.list(it) }.orEmpty()
            SwingUtilities.invokeLater {
                if (closed || token != stampDecode) return@invokeLater
                updatingStamp = true
                stampChoice.model = DefaultComboBoxModel(found.toTypedArray())
                stampChoice.selectedItem = null
                updatingStamp = false
                stampImage = null
                stampNote.text = AbyssusBundle.message("sculptStampNone")
            }
        }
    }

    private fun chooseStamp() {
        val choice = stampChoice.selectedItem as? StampImageChoice ?: return
        val reader = loaded?.images ?: return
        val token = ++stampDecode
        stampImage = null
        AppExecutorUtil.getAppExecutorService().execute {
            val decoded = reader.decode(choice.file)
            SwingUtilities.invokeLater {
                if (closed || token != stampDecode) return@invokeLater
                stampImage = decoded
                stampNote.text = if (decoded == null) AbyssusBundle.message("sculptStampMissing") else ""
                update()
            }
        }
    }

    private fun num(s: JSpinner): Float = (s.value as Number).toFloat()
}
