/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBusConnection
import net.nevinsky.abyssus.lib.core.editor.meta.AssetChoice
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.pick.BrushMode
import net.nevinsky.abyssus.lib.core.editor.pick.BrushStroke
import net.nevinsky.abyssus.lib.core.editor.pick.SPLAT_BRUSH
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SPLAT_MAX_SIDE
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SPLAT_MIN_SIDE
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatLayer
import net.nevinsky.abyssus.lib.core.editor.terrain.splat.SplatState
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.splat.SplatBrushOptions
import net.nevinsky.abyssus.plugin.splat.SplatMode
import net.nevinsky.abyssus.plugin.splat.SplatSession
import net.nevinsky.abyssus.plugin.splat.SplatSnapshot
import net.nevinsky.abyssus.plugin.splat.SplatThumbnails
import net.nevinsky.abyssus.plugin.splat.offersCreate
import net.nevinsky.abyssus.plugin.splat.reason
import java.awt.FlowLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JToggleButton
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The Paint Texture brush of one Scene view: the strip with the five layer slots (name and thumbnail of the assigned
 * texture; Assign and Clear for the chosen one), the radius, strength, mode and Exclusive options, and, when the splat map
 * cannot be painted as it is, the reason, Create splat map, or the shared-map warning with Make unique copy. The toggle is
 * in the Properties panel. [splatSession] opens the session of the selected entity and calls back with it, null when
 * there is no terrain. While painting, the brush is the view's
 * [net.nevinsky.abyssus.lib.core.editor.pick.SceneViewState.brush] of kind [SPLAT_BRUSH]. A [BrushStrip]. AWT thread.
 */
internal class PaintTextureControls(
    renderer: SceneRenderer,
    interaction: SceneInteraction,
    play: PlayState,
    kinds: BrushKinds,
    private val splatSession: ((String, () -> BrushMode?, (SplatSession?) -> Unit) -> Unit)?,
    private val onChanged: () -> Unit,
    private val onEnded: () -> Unit,
    private val report: (String) -> Unit,
    private val thumbnails: SplatThumbnails = SplatThumbnails(),
) : BrushStrip<SplatSession>(
    renderer, interaction, play, kinds, SPLAT_BRUSH, onChanged, "paint-texture-radius", "paint-texture-strength", "paint-texture-strip",
) {
    private val slotButtons = (0 until 5).map { slot ->
        JToggleButton(slotText(slot, null)).apply { name = "paint-texture-slot-$slot"; isFocusable = false }
    }
    private val slotGroup = ButtonGroup().also { g -> slotButtons.forEach(g::add) }
    private val assign = JButton(AbyssusBundle.message("splatAssign")).apply { name = "paint-texture-assign"; isFocusable = false }
    private val clear = JButton(AbyssusBundle.message("splatClear")).apply { name = "paint-texture-clear"; isFocusable = false }
    private val mode = ComboBox(DefaultComboBoxModel(SplatMode.entries.toTypedArray())).apply {
        name = "paint-texture-mode"
        this.renderer = javax.swing.ListCellRenderer { list, value, index, selected, focus ->
            javax.swing.DefaultListCellRenderer().getListCellRendererComponent(
                list, value?.let { AbyssusBundle.message("splatMode.${it.name}") }, index, selected, focus,
            )
        }
    }
    private val exclusive = JCheckBox(AbyssusBundle.message("splatExclusive"), true).apply {
        name = "paint-texture-exclusive"
        toolTipText = AbyssusBundle.message("splatExclusiveTip")
    }
    private val reason = JLabel("").apply { name = "paint-texture-reason"; isVisible = false }
    private val resolution = JSpinner(SpinnerNumberModel(1024, SPLAT_MIN_SIDE, SPLAT_MAX_SIDE, 64)).apply { name = "paint-texture-create-resolution" }
    private val create = JButton(AbyssusBundle.message("splatCreate")).apply { name = "paint-texture-create"; isFocusable = false }
    private val createPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        name = "paint-texture-create-panel"
        isVisible = false
        add(JLabel(AbyssusBundle.message("splatCreateResolution")))
        add(resolution)
        add(create)
    }
    private val shared = JLabel("").apply { name = "paint-texture-shared"; isVisible = false }
    private val makeUnique = JButton(AbyssusBundle.message("splatMakeUnique")).apply { name = "paint-texture-make-unique"; isFocusable = false; isVisible = false }

    private var requested = false
    private var opened = false
    private var snapshot: SplatSnapshot? = null
    private var reading = 0
    private var bus: MessageBusConnection? = null
    private val debounce = Timer(300) { reload() }.apply { isRepeats = false }

    /** Whether a Paint Texture brush is on the view now. */
    val painting: Boolean get() = active

    override val wanted: Boolean get() = requested

    /** The strip panel, for tests that look its controls up by name. */
    internal fun stripForTest(): JPanel = strip

    /** The layer slot chosen to paint: 0 is Base, 1 to 4 are R, G, B and A. */
    val slot: Int get() = slotButtons.indexOfFirst { it.isSelected }.coerceAtLeast(1)

    init {
        slotButtons[1].isSelected = true
        for (b in slotButtons) b.addActionListener { update(); syncAssignButtons() }
        for (s in listOf(radius, strength)) s.addChangeListener { update() }
        mode.addActionListener { update() }
        exclusive.addActionListener { update() }
        assign.addActionListener { chooseTexture() }
        clear.addActionListener { assignTexture(null) }
        create.addActionListener { createMap() }
        makeUnique.addActionListener { makeUniqueCopy() }
        strip.add(JLabel(AbyssusBundle.message("splatLayer")))
        slotButtons.forEach(strip::add)
        strip.add(assign)
        strip.add(clear)
        strip.add(JLabel(AbyssusBundle.message("splatMode")))
        strip.add(mode)
        strip.add(JLabel(AbyssusBundle.message("splatRadius")))
        strip.add(radius)
        strip.add(JLabel(AbyssusBundle.message("splatStrength")))
        strip.add(strength)
        strip.add(exclusive)
        strip.add(JLabel(AbyssusBundle.message("splatShiftHint")))
        strip.add(reason)
        strip.add(createPanel)
        strip.add(shared)
        strip.add(makeUnique)
    }

    /** Adds the strip to [toolbar] when the host can paint at all; the toggle is in the Properties panel. */
    override fun addTo(toolbar: JPanel) {
        if (splatSession != null) toolbar.add(strip)
    }

    /** Puts the brush on [entityId], the selected terrain (the Properties panel's toggle); ends the painting when it cannot be. */
    fun begin(entityId: String) {
        if (play.active || entityId != interaction.selectedId) { end(); return }
        kinds.endOthers(this)
        requested = true
        when {
            loaded != null -> { update(); onChanged() }
            opened -> end()
        }
    }

    override fun sync() {
        strip.isVisible = painting
    }

    override fun openSession(selected: String, mode: () -> BrushMode?, ready: (SplatSession?) -> Unit) {
        splatSession?.invoke(selected, mode, ready)
    }

    override fun disposeSession(session: SplatSession) {
        debounce.stop()
        bus?.disconnect()
        bus = null
        reading++
        snapshot = null
        session.dispose()
    }

    override fun strokeOf(session: SplatSession?): BrushStroke? = session?.stroke

    override fun sessionCleared() {
        opened = false
    }

    override fun sessionLoaded(session: SplatSession?, wasWanted: Boolean) {
        opened = true
        requested = requested || wasWanted
        if (session != null) {
            watch(session)
            reload()
        }
        when {
            requested && session != null -> update()
            requested -> end()
        }
    }

    override fun canBrush(): Boolean = requested && loaded != null

    override fun modeFor(selected: String, shift: Boolean): BrushMode {
        val chosen = mode.selectedItem as SplatMode
        return BrushMode(
            SPLAT_BRUSH, selected, num(radius), num(strength).coerceIn(0f, 1f),
            reversed = shift && chosen == SplatMode.PAINT,
            options = SplatBrushOptions(slot, chosen, exclusive.isSelected),
        )
    }

    /** Takes the brush off the view; [notify] tells the host the painting is over (not when the session is only reloaded). */
    override fun end(notify: Boolean) {
        val was = requested
        super.end(notify)
        requested = false
        if (notify && was) onEnded()
    }

    override fun close() {
        debounce.stop()
        bus?.disconnect()
        bus = null
        reading++
        super.close()
    }

    // -- the splat map and layers the strip shows ------------------------------------------------------------------

    /** Reads the session's splat state and layers off the AWT thread and brings the strip in line. */
    fun reload() {
        val session = loaded ?: return
        val token = ++reading
        AppExecutorUtil.getAppExecutorService().execute {
            val read = runCatchingKeepingCancellation { session.snapshot() }.getOrNull()
            SwingUtilities.invokeLater {
                if (closed || token != reading || loaded !== session || read == null) return@invokeLater
                show(read)
            }
        }
    }

    /** The strip for [read]; the AWT thread. */
    internal fun show(read: SplatSnapshot) {
        snapshot = read
        read.layers.forEachIndexed { i, layer ->
            val button = slotButtons[i]
            button.text = slotText(i, layer)
            button.icon = layer.image?.let { thumbnails.cached(it) }
            layer.image?.let { file ->
                thumbnails.load(file) { icon -> if (snapshot === read) slotButtons[i].icon = icon }
            }
        }
        val state = read.state
        val why = state.reason()
        reason.text = why.orEmpty()
        reason.isVisible = why != null
        createPanel.isVisible = state.offersCreate
        if (state is SplatState.None) {
            reason.text = state.unresolved?.let { AbyssusBundle.message("splatCreateUnresolved", it) } ?: AbyssusBundle.message("splatCreateNote")
            reason.isVisible = true
        }
        val users = (state as? SplatState.Ready)?.users.orEmpty()
        shared.text = if (users.isEmpty()) "" else AbyssusBundle.message("splatSharedWarning", users.joinToString(", "))
        shared.isVisible = users.isNotEmpty()
        makeUnique.isVisible = users.isNotEmpty()
        syncAssignButtons()
        strip.revalidate()
        strip.repaint()
    }

    private fun syncAssignButtons() {
        val has = snapshot?.layers?.getOrNull(slot)?.uuid != null
        clear.isEnabled = has
        assign.isEnabled = snapshot?.layers?.isNotEmpty() == true
    }

    private fun slotText(slot: Int, layer: SplatLayer?): String {
        val label = AbyssusBundle.message("splatSlot.$slot")
        return when {
            layer == null || layer.uuid == null -> "$label  ${AbyssusBundle.message("splatSlotEmpty")}"
            !layer.resolved -> "$label  ${layer.name} (${AbyssusBundle.message("splatSlotUnresolved")})"
            else -> "$label  ${layer.name}"
        }
    }

    // -- the operations --------------------------------------------------------------------------------------------

    private fun chooseTexture() {
        val session = loaded ?: return
        val layer = snapshot?.layers?.getOrNull(slot) ?: return
        AppExecutorUtil.getAppExecutorService().execute {
            val choices = session.choices(layer.uuid)
            SwingUtilities.invokeLater {
                if (closed || loaded !== session) return@invokeLater
                JBPopupFactory.getInstance().createPopupChooserBuilder(choices)
                    .setRenderer(javax.swing.ListCellRenderer { list, value: AssetChoice?, index, selected, focus ->
                        val text = when {
                            value == null -> ""
                            value.value == null -> AbyssusBundle.message("splatSlotEmpty")
                            !value.resolved -> "${value.label} (${AbyssusBundle.message("splatSlotUnresolved")})"
                            else -> value.label
                        }
                        javax.swing.DefaultListCellRenderer().getListCellRendererComponent(list, text, index, selected, focus)
                    })
                    .setItemChosenCallback { choice -> assignTexture(choice.value) }
                    .createPopup()
                    .showUnderneathOf(assign)
            }
        }
    }

    /** Stores [uuid] (null clears) in the chosen layer slot as one undoable step on the terrain's `meta.json`. */
    internal fun assignTexture(uuid: String?) {
        val session = loaded ?: return
        val layer = snapshot?.layers?.getOrNull(slot) ?: return
        session.assign(layer, uuid)
        reload()
    }

    internal fun createMap() {
        val session = loaded ?: return
        session.create((resolution.value as Number).toInt(), report) { reload(); update() }
        reload()
    }

    internal fun makeUniqueCopy() {
        val session = loaded ?: return
        session.makeUnique(report) { reload(); update() }
        reload()
    }

    /** Follows changes under the project's assets (an Undo of Create, a replaced image) while the session is open. */
    private fun watch(session: SplatSession) {
        bus?.disconnect()
        val root = session.assetsDir.absolutePath
        bus = ApplicationManager.getApplication().messageBus.connect().also {
            it.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { e -> e.path.startsWith(root) }) SwingUtilities.invokeLater { if (!closed) debounce.restart() }
                }
            })
        }
    }

    private fun num(s: JSpinner): Float = (s.value as Number).toFloat()
}
