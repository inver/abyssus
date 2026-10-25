/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.WrapLayout
import com.badlogic.gdx.math.Vector3
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoMode
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoSnap
import net.nevinsky.abyssus.lib.core.editor.pick.GizmoSpace
import net.nevinsky.abyssus.lib.core.editor.pick.SceneInteraction
import net.nevinsky.abyssus.lib.core.editor.scene.SceneRenderParams
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.ui.showActionGroup
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton

/** What [SceneViewToolbar] reads from its view that is not the interaction, the renderer or a control strip. */
internal class SceneViewToolbarSources(
    val lightMenu: PlacementMenu<() -> Vector3>?,
    val assetMenu: PlacementMenu<() -> Vector3>?,
    val waterMenu: PlacementMenu<() -> Vector3>?,
    val pathMenu: PlacementMenu<(net.nevinsky.abyssus.lib.core.editor.content.RenderAsset) -> Unit>?,
    val foliageActions: ((String) -> DefaultActionGroup?)?,
    val overlays: SceneOverlayHost?,
    val lightChoices: () -> DefaultActionGroup?,
    val assetChoices: () -> DefaultActionGroup?,
    val waterChoices: () -> DefaultActionGroup?,
    val pathChoices: () -> DefaultActionGroup?,
    val onRegenerate: () -> ((String) -> Unit)?,
    val unavailable: () -> Boolean,
)

/**
 * The Scene view's toolbar: the tool groups, the camera selector, the Add buttons, the foliage, play and cloud strips and
 * the ray experiment toggle. [sync] brings every control in line with the interaction state; the view calls it after
 * each change. Everything runs on the EDT.
 */
internal class SceneViewToolbar(
    private val owner: JComponent,
    private val renderer: SceneRenderer,
    private val interaction: SceneInteraction,
    private val tools: SceneViewToolsModel,
    private val play: PlayState,
    private val paint: FoliagePaintControls,
    private val playStrip: PlayControlsStrip,
    private val clouds: CloudControls,
    private val experimentButton: JToggleButton?,
    private val onExperimentToggled: () -> Unit,
    private val sources: SceneViewToolbarSources,
    frameSelection: () -> Unit,
) {
    private val experimenting: Boolean get() = experimentButton?.isSelected == true
    private var lastToolbar = toolbarState(GizmoMode.MOVE, experimenting = false, playing = false, canDrop = false, canAddLight = false, canAddAsset = false)
    private val toolsToolbar = SceneToolsToolbar(interaction, tools, { lastToolbar }, frameSelection, owner)
    private val dropButton = JButton(AbyssusBundle.message("sceneViewDrop"))
    private val addLightButton = JButton(AbyssusBundle.message("addLightTitle")).apply { name = "add-light" }
    private val addAssetButton = JButton(AbyssusBundle.message("addAssetTitle")).apply { name = "add-asset" }
    private val addWaterButton = JButton(AbyssusBundle.message("addWaterTitle")).apply { name = "add-water" }
    private val addPathButton = JButton(AbyssusBundle.message("addPathArrangementTitle")).apply { name = "add-path-arrangement" }
    private val regenerateButton = JButton(AbyssusBundle.message("regenerateArrangementTitle")).apply { name = "regenerate-arrangement"; isVisible = false }
    private val drawingHint = JLabel(AbyssusBundle.message("pathDrawingHint")).apply { name = "path-drawing-hint"; isVisible = false }
    private val addFoliageButton = JButton(AbyssusBundle.message("addFoliageTitle")).apply { name = "add-foliage" }
    private val cameraCombo = ComboBox<CameraChoice>()
    private var choices: List<CameraChoice> = emptyList()
    private var updatingControls = false

    /** The tool, space and snap, such as `Move · Local · Snap 0.5`; the stats strip shows it. */
    var statusText: String = ""
        private set

    /** The toolbar panel, named `scene-toolbar`. */
    val component: JPanel by lazy { build() }

    private fun build(): JPanel {
        dropButton.isFocusable = false
        dropButton.toolTipText = AbyssusBundle.message("sceneViewDropTooltip")
        dropButton.addActionListener { interaction.drop() }
        addChoiceButton(addLightButton, "addLightTooltip", "addLightTitle", sources.lightChoices)
        addChoiceButton(addAssetButton, "addAssetTooltip", "addAssetTitle", sources.assetChoices)
        addChoiceButton(addWaterButton, "addWaterTooltip", "addWaterTitle", sources.waterChoices)
        addChoiceButton(addPathButton, "addPathArrangementTooltip", "addPathArrangementTitle", sources.pathChoices)
        regenerateButton.isFocusable = false
        regenerateButton.toolTipText = AbyssusBundle.message("regenerateArrangementTooltip")
        regenerateButton.addActionListener { interaction.selectedId?.let { sources.onRegenerate()?.invoke(it) } }
        cameraCombo.isFocusable = false
        addFoliageButton.isFocusable = false
        addFoliageButton.addActionListener {
            val id = interaction.selectedId ?: return@addActionListener
            val actions = sources.foliageActions?.invoke(id) ?: return@addActionListener
            showChoices(actions, "addFoliageTitle", addFoliageButton)
        }
        cameraCombo.toolTipText = AbyssusBundle.message("sceneViewCameraTooltip")
        cameraCombo.addActionListener {
            if (updatingControls) return@addActionListener
            val choice = cameraCombo.selectedItem as? CameraChoice ?: return@addActionListener
            interaction.viewCamera = choice.id
        }
        return JPanel(WrapLayout(FlowLayout.LEFT, 4, 2)).apply {
            name = "scene-toolbar"
            accessibleContext.accessibleName = AbyssusBundle.message("sceneViewToolbarName")
            val groups = toolsToolbar.components()
            groups.forEach { add(it) }
            add(toolsToolbar.divider())
            add(cameraCombo)
            add(toolsToolbar.frameComponent())
            add(toolsToolbar.divider())
            add(dropButton)
            add(addLightButton)
            if (sources.assetMenu != null) add(addAssetButton)
            if (sources.waterMenu != null) add(addWaterButton)
            if (sources.pathMenu != null) add(addPathButton)
            add(regenerateButton)
            add(drawingHint)
            if (sources.foliageActions != null) add(addFoliageButton)
            paint.addTo(this)
            add(clouds.combo)
            add(clouds.note)
            playStrip.addTo(this)
            overlayToolbar()?.let { add(it) }
            experimentButton?.let { button ->
                button.isFocusable = false
                button.addActionListener {
                    onExperimentToggled()
                    sync()
                }
                add(button)
            }
        }
    }

    private fun addChoiceButton(button: JButton, tooltip: String, title: String, choices: () -> DefaultActionGroup?) {
        button.isFocusable = false
        button.toolTipText = AbyssusBundle.message(tooltip)
        button.addActionListener {
            val actions = choices() ?: return@addActionListener
            showChoices(actions, title, button)
        }
    }

    private fun showChoices(actions: DefaultActionGroup, titleKey: String, button: JButton) =
        showActionGroup(DataManager.getInstance().getDataContext(owner), actions, AbyssusBundle.message(titleKey), button)

    /** Each overlay's actions (such as Show Physics), in one small action toolbar; null when there are none. */
    private fun overlayToolbar(): JComponent? {
        val overlays = sources.overlays ?: return null
        val group = object : DefaultActionGroup() {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> =
                overlays.overlays.flatMap { it.overlay.actions() }.toTypedArray()
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("AbyssusSceneOverlays", group, true)
        toolbar.targetComponent = owner
        return toolbar.component
    }

    /** Brings the toolbar's buttons and selector in line with the interaction state. */
    fun sync() {
        renderer.state.gizmoSpace = tools.space
        renderer.display = tools.display
        renderer.state.snap = if (tools.snapOn) GizmoSnap(tools.snapStep, SNAP_ANGLE_DEGREES) else null
        statusText = toolStatus(
            AbyssusBundle.message(when (interaction.mode) {
                GizmoMode.SELECT -> "sceneViewToolSelect"
                GizmoMode.MOVE -> "sceneViewToolMove"
                GizmoMode.ROTATE -> "sceneViewToolRotate"
            }),
            AbyssusBundle.message(if (tools.space == GizmoSpace.WORLD) "sceneViewSpaceWorld" else "sceneViewSpaceLocal"),
            if (tools.snapOn) AbyssusBundle.message("sceneViewSnap", formatStep(tools.snapStep)) else null,
        )
        paint.beforeSync()
        updatingControls = true
        try {
            val toolbar = toolbarState(
                interaction.mode, experimenting, play.active, interaction.canDrop,
                sources.lightMenu?.available?.invoke() == true, sources.assetMenu?.available?.invoke() == true,
                tools.display, renderer.content.lights.isNotEmpty(), renderer.params.fog != null, interaction.viewCamera != null,
                sources.waterMenu?.available?.invoke() == true,
            )
            lastToolbar = when {
                sources.unavailable() -> toolbar.disabled()
                paint.painting -> toolbar.copy(gizmoEnabled = false, spaceEnabled = false, snapEnabled = false)
                else -> toolbar
            }
            cameraCombo.isEnabled = toolbar.cameraEnabled
            dropButton.isEnabled = toolbar.dropEnabled
            addLightButton.isEnabled = toolbar.addLightEnabled
            addAssetButton.isEnabled = toolbar.addAssetEnabled
            addWaterButton.isEnabled = toolbar.addWaterEnabled
            addPathButton.isEnabled = !play.active && !experimenting && sources.pathMenu?.available?.invoke() == true
            val arrangementSelected = renderer.content.arrangements.any { it.entityId == interaction.selectedId }
            regenerateButton.isVisible = arrangementSelected
            regenerateButton.isEnabled = arrangementSelected && !play.active && sources.onRegenerate() != null
            drawingHint.isVisible = interaction.drawing != null
            val foliage = interaction.selectedId?.let { sources.foliageActions?.invoke(it) }
            addFoliageButton.isVisible = foliage != null
            val hasChoices = foliage != null && foliage.childrenCount > 0
            addFoliageButton.isEnabled = !play.active && hasChoices
            addFoliageButton.toolTipText = if (foliage != null && !hasChoices) AbyssusBundle.message("addFoliageNone") else null
            paint.sync()
            if (paint.painting) dropButton.isEnabled = false
            playStrip.sync()
            cameraCombo.selectedItem = choices.firstOrNull { it.id == interaction.viewCamera } ?: choices.firstOrNull()
        } finally {
            updatingControls = false
        }
        toolsToolbar.update()
        clouds.sync()
        if (sources.unavailable()) disableComponents()
    }

    /** Every plain Swing control of the toolbar off (the action toolbars read [lastToolbar]); the view cannot render. */
    private fun disableComponents() {
        fun off(c: Component) {
            if (c is AbstractButton || c is JComboBox<*>) c.isEnabled = false
            (c as? Container)?.components?.forEach(::off)
        }
        off(component)
    }

    /** Refills the camera selector when the scene's cameras changed, then syncs. */
    fun refreshCameraChoices(params: SceneRenderParams) {
        val fresh = cameraChoices(params.content, AbyssusBundle.message("sceneViewFreeCamera"))
        if (fresh != choices) {
            choices = fresh
            updatingControls = true
            try {
                cameraCombo.removeAllItems()
                fresh.forEach(cameraCombo::addItem)
            } finally {
                updatingControls = false
            }
        }
        sync()
    }
}
