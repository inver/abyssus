/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.foliage

import net.nevinsky.abyssus.lib.core.editor.util.numberText
import net.nevinsky.abyssus.lib.core.editor.util.NumberTextStyle

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageCollider
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageColliderShape
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerKind
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageLayerMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.FoliageModelMeta
import net.nevinsky.abyssus.lib.core.assets.foliage.layerKind
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_ALIGN_TO_NORMAL
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_CANDIDATE_LIMIT
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_COPY_LIMIT
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_DENSITY
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_DRAW_DISTANCE
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MAX_SLOPE
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_SCALE
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_SEED
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_HEIGHT
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_MODELS
import net.nevinsky.abyssus.lib.core.editor.foliage.FOLIAGE_WEIGHT
import net.nevinsky.abyssus.lib.core.editor.foliage.ScatterRefusal
import net.nevinsky.abyssus.plugin.AbyssusBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The controls of one foliage asset: the terrain it stands on, the layer list with its models and settings, the copy
 * counts with the limit notice, the stale notice with Re-bake, and Apply and Cancel. Editors are named
 * `foliage-<layer id>-<field>`, the reasons beside them `foliage-error-<layer id>-<field>`, the layer controls
 * `foliage-kind-<id>`, `foliage-up-<id>`, `foliage-down-<id>` and `foliage-remove-<id>`, the model rows
 * `foliage-model-<id>-<index>` and `foliage-weight-<id>-<index>`, the buttons `foliage-add-layer`,
 * `foliage-add-model-<id>`, `foliage-apply`, `foliage-cancel` and `foliage-rebake`, and the notices
 * `foliage-terrain`, `foliage-stale`, `foliage-limit`, `foliage-total`, `foliage-count-<id>` and `foliage-message`,
 * which is how tests reach them. The view holds no state of its own: it is rebuilt from the [controller] whenever
 * that changes, and every edit it makes goes through it, so an invalid value is refused with a reason and nothing is
 * previewed or written.
 */
internal class FoliageSection(
    private val controller: FoliageController,
    private val undoRow: JComponent? = null,
) : JPanel(BorderLayout()) {
    init {
        val box = JPanel(VerticalLayout(JBUI.scale(6)))
        box.border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(12, 16, 16, 16),
        )
        box.add(JBLabel(AbyssusBundle.message("foliageSection").uppercase()).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
        })
        box.add(JBLabel(AbyssusBundle.message("foliageTerrain", controller.terrainName)).apply { name = "foliage-terrain" })
        if (controller.stale) box.add(help("<html>${AbyssusBundle.message("foliageStale")}</html>").apply { name = "foliage-stale" })
        for (layer in controller.settings.layers) box.add(layerBlock(layer))
        box.add(button("foliage-add-layer", "foliageAddLayer", true) { controller.addLayer() })
        box.add(counts())
        controller.message?.let { box.add(help("<html>$it</html>").apply { name = "foliage-message" }) }
        if (controller.colliderOnly) box.add(help(AbyssusBundle.message("foliageColliderBakeKept")).apply { name = "foliage-collider-note" })
        box.add(actions())
        add(box, BorderLayout.NORTH)
    }

    private fun layerBlock(layer: FoliageLayerMeta): JComponent {
        val box = JPanel(VerticalLayout(JBUI.scale(4))).apply { name = "foliage-layer-${layer.id}" }
        box.add(layerHeader(layer))
        val modelsError = JBLabel("").apply { foreground = JBColor.RED; name = "foliage-error-${layer.id}-models" }
        controller.modelsProblemOf(layer.id)?.let { modelsError.text = it.reason }
        for ((index, model) in layer.models.withIndex()) box.add(modelRow(layer.id, index, model, modelsError))
        box.add(addModelRow(layer, modelsError))
        for (spec in FOLIAGE_FIELDS) if (spec.appliesTo(layer)) box.add(fieldRow(layer, spec))
        if (layer.layerKind() == FoliageLayerKind.OBJECT) box.add(colliderBlock(layer))
        box.add(
            JBLabel(AbyssusBundle.message("foliageLayerCount", controller.countOf(layer.id)?.toString() ?: "?")).apply {
                name = "foliage-count-${layer.id}"
                foreground = UIUtil.getContextHelpForeground()
            })
        return box
    }

    private fun layerHeader(layer: FoliageLayerMeta): JComponent {
        val index = controller.settings.layers.indexOfFirst { it.id == layer.id }
        val kinds = FoliageLayerKind.entries
        val kind = ComboBox(kinds.map { AbyssusBundle.message("foliageKind${it.name}") }.toTypedArray()).apply {
            name = "foliage-kind-${layer.id}"
            selectedIndex = kinds.indexOfFirst { it.name == layer.kind }
            addActionListener { kinds.getOrNull(selectedIndex)?.let { chosen ->
                editLayer(layer.id, null) { it.copy(kind = chosen.name) }
            } }
        }
        val controls = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply { isOpaque = false }
        controls.add(kind)
        controls.add(button("foliage-up-${layer.id}", "foliageMoveUp", index > 0) { controller.moveLayer(layer.id, -1) })
        controls.add(button("foliage-down-${layer.id}", "foliageMoveDown", index < controller.settings.layers.lastIndex) {
            controller.moveLayer(layer.id, 1)
        })
        controls.add(button("foliage-remove-${layer.id}", "foliageRemoveLayer", true) { controller.removeLayer(layer.id) })
        return JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(JBLabel(AbyssusBundle.message("foliageLayer", layer.id.toString())).apply { font = JBFont.label().asBold() }, BorderLayout.WEST)
            add(controls, BorderLayout.EAST)
        }
    }

    private fun modelRow(layerId: Int, index: Int, model: FoliageModelMeta, error: JBLabel): JComponent {
        val choices = LinkedHashSet(controller.modelChoices).apply { add(model.asset) }
        val combo = ComboBox(choices.toTypedArray()).apply {
            name = "foliage-model-$layerId-$index"
            selectedItem = model.asset
            addActionListener {
                (selectedItem as? String)?.let { picked ->
                    editModels(layerId, error) { models -> models.mapIndexed { i, m -> if (i == index) m.copy(asset = picked) else m } }
                }
            }
        }
        val weight = JBTextField(model.weight.editText()).apply {
            name = "foliage-weight-$layerId-$index"
            font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
            val save = {
                val parsed = text.trim().toFloatOrNull()
                if (parsed == null) {
                    error.text = AbyssusBundle.message("foliageNotANumber")
                    text = model.weight.editText()
                } else {
                    editModels(layerId, error) { models ->
                        models.mapIndexed { i, m -> if (i == index) m.copy(weight = parsed) else m }
                    }
                }
            }
            addActionListener { save() }
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) {
                    save()
                }
            })
        }
        return row(
            combo,
            JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                isOpaque = false
                add(JBLabel(AbyssusBundle.message("foliageWeight")).apply { foreground = UIUtil.getContextHelpForeground() })
                add(weight)
                add(button("foliage-model-remove-$layerId-$index", "foliageRemoveModel", true) {
                    editModels(layerId, error) { models -> models.filterIndexed { i, _ -> i != index } }
                })
            },
            error,
        )
    }

    private fun addModelRow(layer: FoliageLayerMeta, error: JBLabel): JComponent {
        val left = controller.modelChoices.filter { asset -> layer.models.none { it.asset == asset } }
        if (left.isEmpty()) return JBLabel(AbyssusBundle.message("foliageNoModels")).apply { foreground = UIUtil.getContextHelpForeground() }
        val pick = ComboBox(left.toTypedArray()).apply { name = "foliage-model-new-${layer.id}" }
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(pick)
            add(button("foliage-add-model-${layer.id}", "foliageAddModel", true) {
                (pick.selectedItem as? String)?.let { asset ->
                    editModels(layer.id, error) { it + FoliageModelMeta(asset = asset) }
                }
            })
        }
    }

    private fun fieldRow(layer: FoliageLayerMeta, spec: FoliageField): JComponent {
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "foliage-error-${layer.id}-${spec.key}" }
        controller.problemOf(layer.id, spec.problemField)?.let { error.text = it.reason }
        val editor = JBTextField(spec.textOf(layer)).apply {
            name = "foliage-${layer.id}-${spec.key}"
            font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
            val save = {
                val entered = text.trim()
                val cleared = entered.isEmpty() && spec.optional
                val integer = if (spec.integer) entered.toIntOrNull() else null
                val parsed = if (cleared || spec.integer) null else entered.toFloatOrNull()?.takeIf { it.isFinite() }
                if (spec.integer && integer == null || !spec.integer && parsed == null && !cleared) {
                    error.text = AbyssusBundle.message(if (spec.integer) "foliageNotAnInteger" else "foliageNotANumber")
                    text = spec.textOf(layer)
                } else {
                    val reason = editLayer(layer.id, error) {
                        if (spec.integer) it.copy(seed = integer!!) else spec.with(it, parsed)
                    }
                    if (reason != null) text = spec.textOf(layer)
                }
            }
            addActionListener { save() }
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) {
                    save()
                }
            })
        }
        return row(JBLabel(AbyssusBundle.message(spec.label)), editor, error)
    }

    /** The collider controls of an OBJECT layer: None/Box/Sphere/Capsule, then the sizes of the chosen shape and the offset. */
    private fun colliderBlock(layer: FoliageLayerMeta): JComponent {
        val box = JPanel(VerticalLayout(JBUI.scale(2))).apply { name = "foliage-collider-block-${layer.id}" }
        val error = JBLabel("").apply { foreground = JBColor.RED; name = "foliage-error-${layer.id}-collider" }
        controller.colliderProblemOf(layer.id)?.let { error.text = it.reason }
        val current = controller.colliderOf(layer.id)
        val choices = listOf<FoliageColliderShape?>(null) + FoliageColliderShape.entries
        val shapes = ComboBox(choices.map { AbyssusBundle.message(if (it == null) "foliageColliderNone" else "foliageCollider${it.name}") }.toTypedArray()).apply {
            name = "foliage-collider-${layer.id}"
            // an unusable stored collider shows no choice, so picking None (or a shape) is a change that replaces it
            selectedIndex = if (controller.colliderProblemOf(layer.id) != null && current == null) -1 else choices.indexOf(current?.shape)
            addActionListener {
                if (selectedIndex < 0) return@addActionListener
                val chosen = choices[selectedIndex]
                val reason = controller.editCollider(layer.id) { old -> chosen?.let { (old ?: FoliageCollider()).copy(shape = it) } }
                if (reason != null) error.text = reason
            }
        }
        box.add(row(JBLabel(AbyssusBundle.message("foliageCollider")), shapes, error))
        if (current != null) {
            val specs = when (current.shape) {
                FoliageColliderShape.BOX -> listOf(HALF_X, HALF_Y, HALF_Z)
                FoliageColliderShape.SPHERE -> listOf(RADIUS)
                FoliageColliderShape.CAPSULE -> listOf(RADIUS, HALF_HEIGHT)
            } + listOf(OFFSET_X, OFFSET_Y, OFFSET_Z)
            for (spec in specs) box.add(colliderField(layer.id, current, spec, error))
        }
        return box
    }

    private fun colliderField(layerId: Int, current: FoliageCollider, spec: ColliderField, error: JBLabel): JComponent {
        val editor = JBTextField(spec.read(current).editText()).apply {
            name = "foliage-collider-$layerId-${spec.key}"
            font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
            val save = {
                val parsed = text.trim().toFloatOrNull()?.takeIf { it.isFinite() }
                if (parsed == null) {
                    error.text = AbyssusBundle.message("foliageNotANumber")
                    text = spec.read(current).editText()
                } else if (parsed != spec.read(current)) {
                    val reason = controller.editCollider(layerId) { old -> old?.let { spec.write(it, parsed) } }
                    if (reason != null) {
                        error.text = reason
                        text = spec.read(current).editText()
                    }
                }
            }
            addActionListener { save() }
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) {
                    save()
                }
            })
        }
        return row(JBLabel(AbyssusBundle.message(spec.label)), editor, JBLabel(""))
    }

    private fun counts(): JComponent {
        val box = JPanel(VerticalLayout(JBUI.scale(2)))
        box.add(JBLabel(AbyssusBundle.message("foliageTotal", controller.totalShown.toString())).apply {
            name = "foliage-total"
            foreground = UIUtil.getContextHelpForeground()
        })
        val notice = when (controller.refusal) {
            ScatterRefusal.COPY_LIMIT -> AbyssusBundle.message("foliageOverLimit", controller.totalShown.toString(), FOLIAGE_COPY_LIMIT.toString())
            ScatterRefusal.CANDIDATE_LIMIT -> AbyssusBundle.message("foliageOverCandidates", controller.totalShown.toString(), FOLIAGE_CANDIDATE_LIMIT.toString())
            null -> null
        }
        notice?.let { box.add(help("<html>$it</html>").apply { name = "foliage-limit" }) }
        return box
    }

    private fun actions(): JComponent {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply { isOpaque = false }
        bar.add(button("foliage-apply", "foliageApply", controller.canApply) { controller.apply() })
        bar.add(button("foliage-cancel", "foliageCancel", true) { controller.cancel() })
        if (controller.stale) bar.add(button("foliage-rebake", "foliageRebake", controller.canRebake) { controller.rebake() })
        return JPanel(VerticalLayout(JBUI.scale(4))).apply {
            add(bar)
            undoRow?.let { add(it) }
        }
    }

    /** Applies [block] to the layer [layerId]; the reason it was refused, or null when it was taken. */
    private fun editLayer(layerId: Int, error: JBLabel?, block: (FoliageLayerMeta) -> FoliageLayerMeta): String? {
        val reason = controller.edit { settings ->
            settings.copy(layers = settings.layers.map { if (it.id == layerId) block(it) else it })
        }
        if (reason != null && error != null) error.text = reason
        return reason
    }

    private fun editModels(layerId: Int, error: JBLabel, block: (List<FoliageModelMeta>) -> List<FoliageModelMeta>) {
        editLayer(layerId, error) { layer -> layer.copy(models = block(layer.models)) }
    }

    private fun button(name: String, key: String, enabled: Boolean, action: () -> Unit) =
        JButton(AbyssusBundle.message(key)).apply {
            this.name = name
            isEnabled = enabled
            addActionListener { action() }
        }

    private fun help(text: String) = JBLabel(text).apply { foreground = UIUtil.getContextHelpForeground() }

    private fun row(name: JComponent, value: JComponent, error: JComponent): JPanel = JPanel(GridBagLayout()).apply {
        add(
            name,
            GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST; ipadx = 0 }
                .also { name.preferredSize = Dimension(JBUI.scale(ROW_WIDTH), name.preferredSize.height) })
        add(
            value,
            GridBagConstraints().apply {
                gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
            })
        add(error, GridBagConstraints().apply { gridx = 2; insets = JBUI.insetsLeft(8) })
    }

    private companion object {
        const val ROW_WIDTH = 240
    }
}

/** One editable collider number: its key, label and how it reads and writes the [FoliageCollider]. */
private class ColliderField(
    val key: String,
    val label: String,
    val read: (FoliageCollider) -> Float,
    val write: (FoliageCollider, Float) -> FoliageCollider,
)

private val HALF_X = ColliderField("halfX", "foliageColliderHalfX", { it.halfX }, { c, v -> c.copy(halfX = v) })
private val HALF_Y = ColliderField("halfY", "foliageColliderHalfY", { it.halfY }, { c, v -> c.copy(halfY = v) })
private val HALF_Z = ColliderField("halfZ", "foliageColliderHalfZ", { it.halfZ }, { c, v -> c.copy(halfZ = v) })
private val RADIUS = ColliderField("radius", "foliageColliderRadius", { it.radius }, { c, v -> c.copy(radius = v) })
private val HALF_HEIGHT = ColliderField("halfHeight", "foliageColliderHalfHeight", { it.halfHeight }, { c, v -> c.copy(halfHeight = v) })
private val OFFSET_X = ColliderField("offsetX", "foliageColliderOffsetX", { it.offsetX }, { c, v -> c.copy(offsetX = v) })
private val OFFSET_Y = ColliderField("offsetY", "foliageColliderOffsetY", { it.offsetY }, { c, v -> c.copy(offsetY = v) })
private val OFFSET_Z = ColliderField("offsetZ", "foliageColliderOffsetZ", { it.offsetZ }, { c, v -> c.copy(offsetZ = v) })

/** One editable layer setting: its key, label and file member, and how the editor turns text into a value. */
private class FoliageField(
    val key: String,
    val label: String,
    val problemField: String,
    val integer: Boolean = false,
    val optional: Boolean = false,
    val appliesTo: (FoliageLayerMeta) -> Boolean = { true },
    val textOf: (FoliageLayerMeta) -> String,
    val with: (FoliageLayerMeta, Float?) -> FoliageLayerMeta,
)

private val FOLIAGE_FIELDS = listOf(
    FoliageField("density", "foliageDensity", FOLIAGE_DENSITY, textOf = { it.density.editText() }, with = { l, v -> l.copy(density = v ?: l.density) }),
    FoliageField("scaleMin", "foliageScaleMin", FOLIAGE_SCALE, textOf = { it.scale.min.editText() }, with = { l, v -> l.copy(scale = l.scale.copy(min = v ?: l.scale.min)) }),
    FoliageField("scaleMax", "foliageScaleMax", FOLIAGE_SCALE, textOf = { it.scale.max.editText() }, with = { l, v -> l.copy(scale = l.scale.copy(max = v ?: l.scale.max)) }),
    FoliageField("alignToNormal", "foliageAlign", FOLIAGE_ALIGN_TO_NORMAL, textOf = { it.alignToNormal.editText() }, with = { l, v -> l.copy(alignToNormal = v ?: l.alignToNormal) }),
    FoliageField("minHeight", "foliageMinHeight", FOLIAGE_HEIGHT, optional = true, textOf = { it.minHeight?.editText() ?: "" }, with = { l, v -> l.copy(minHeight = v) }),
    FoliageField("maxHeight", "foliageMaxHeight", FOLIAGE_HEIGHT, optional = true, textOf = { it.maxHeight?.editText() ?: "" }, with = { l, v -> l.copy(maxHeight = v) }),
    FoliageField("maxSlope", "foliageMaxSlope", FOLIAGE_MAX_SLOPE, optional = true, textOf = { it.maxSlope?.editText() ?: "" }, with = { l, v -> l.copy(maxSlope = v) }),
    FoliageField(
        "drawDistance", "foliageDrawDistance", FOLIAGE_DRAW_DISTANCE,
        appliesTo = { it.layerKind() == FoliageLayerKind.DETAIL },
        textOf = { it.drawDistance.editText() },
        with = { l, v -> l.copy(drawDistance = v ?: l.drawDistance) },
    ),
    FoliageField("seed", "foliageSeed", FOLIAGE_SEED, integer = true, textOf = { it.seed.toString() }, with = { l, v -> l.copy(seed = (v ?: l.seed.toFloat()).toInt()) }),
)

/** A float as its file spells it: plain text rather than the exponent form small values would otherwise take. */
private fun Float.editText(): String = numberText(this, NumberTextStyle.FOLIAGE)

/** Shown instead of the controls when the foliage asset cannot be edited. */
internal fun foliageUnusableNote(reason: String): JComponent = JPanel(BorderLayout()).apply {
    border = BorderFactory.createCompoundBorder(
        JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
        JBUI.Borders.empty(12, 16, 16, 16)
    )
    add(
        JBLabel("<html>${AbyssusBundle.message("foliageDataInvalid", reason)}</html>").apply {
            name = "foliage-unusable"
            foreground = JBColor.RED
        },
        BorderLayout.NORTH,
    )
}
