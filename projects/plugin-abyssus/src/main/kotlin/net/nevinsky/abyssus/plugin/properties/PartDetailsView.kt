/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.properties

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import net.nevinsky.abyssus.lib.core.assets.model.PartHinge
import net.nevinsky.abyssus.lib.core.assets.model.PartPivot
import net.nevinsky.abyssus.lib.core.assets.model.PartQuat
import net.nevinsky.abyssus.lib.core.assets.model.PartVec3
import net.nevinsky.abyssus.lib.core.editor.pick.HingePreview
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.EditorBundle
import net.nevinsky.abyssus.plugin.modelparts.HingePreviews
import net.nevinsky.abyssus.plugin.modelparts.ModelPartWriter

/**
 * The part section of Properties (OpenSpec `object-properties-panel`, "Part properties"): the breadcrumb, the model
 * asset, the pivot as numbers with Reset (or why none can be applied), and the hinge with its limits, Remove and the
 * view-only Preview slider. Edits go to the model's `meta.json` through [ModelPartWriter]; fields are named
 * `part-pivot-<member>`, `part-hinge-min`, `part-hinge-max` and the buttons `part-pivot-reset`, `part-hinge-add`,
 * `part-hinge-remove`, which is how tests reach them.
 */
internal class PartDetailsView(
    private val project: Project,
    private val state: PanelState.PartDetails,
    private val writer: ModelPartWriter = ModelPartWriter(project),
) : JPanel(VerticalLayout(JBUI.scale(6))) {
    private val target = state.target
    private var preview: JSlider? = null

    init {
        name = "part-details"
        border = JBUI.Borders.empty(8)
        add(JBLabel(state.breadcrumb.joinToString("  >  ")).apply {
            name = "part-breadcrumb"
            font = JBFont.label().asBold()
        })
        add(row(AbyssusBundle.message("propertiesPartAsset"), JBLabel(target.asset).apply { name = "part-asset" }))
        when {
            state.info == null -> add(note(AbyssusBundle.message("propertiesPartUnreadable")))
            state.part == null -> add(note(AbyssusBundle.message("propertiesPartGone", target.node)))
            else -> {
                add(pivotSection(state.part.pivotRefusal))
                add(hingeSection())
            }
        }
    }

    private fun pivotSection(refusal: net.nevinsky.abyssus.lib.core.assets.model.PivotRefusalReason?): JComponent {
        val panel = JPanel(VerticalLayout(JBUI.scale(4))).apply { name = "part-pivot" }
        panel.add(JBLabel(AbyssusBundle.message("propertiesPartPivot")).apply { font = JBFont.label().asBold() })
        if (refusal != null) {
            panel.add(
                note(
                    AbyssusBundle.message("propertiesPartPivotRefused", EditorBundle.message("pivotRefusal.${refusal.name}"))
                ).apply { name = "part-pivot-refused" }
            )
            return panel
        }
        val pivot = state.pivot
        val position = pivot?.position ?: PartVec3()
        val rotation = pivot?.rotation ?: PartQuat()
        val fields = linkedMapOf(
            "position-x" to field("part-pivot-position-x", position.x),
            "position-y" to field("part-pivot-position-y", position.y),
            "position-z" to field("part-pivot-position-z", position.z),
            "rotation-x" to field("part-pivot-rotation-x", rotation.x),
            "rotation-y" to field("part-pivot-rotation-y", rotation.y),
            "rotation-z" to field("part-pivot-rotation-z", rotation.z),
            "rotation-w" to field("part-pivot-rotation-w", rotation.w),
        )
        val error = JBLabel(AbyssusBundle.message("propertiesPartNotNumber")).apply {
            name = "part-pivot-error"
            foreground = JBColor.RED
            isVisible = false
        }
        fun commit() {
            val values = fields.mapValues { it.value.text.trim().toFloatOrNull()?.takeIf(Float::isFinite) }
            if (values.values.any { it == null }) {
                error.isVisible = true
                return
            }
            error.isVisible = false
            fun v(key: String) = checkNotNull(values[key])
            val next = PartPivot(
                PartVec3(v("position-x"), v("position-y"), v("position-z")),
                PartQuat(v("rotation-x"), v("rotation-y"), v("rotation-z"), v("rotation-w")),
            )
            if (next != pivot && !(pivot == null && next == PartPivot(PartVec3(), PartQuat()))) {
                setPivot(next)
            }
        }
        fields.values.forEach { f ->
            f.addActionListener { commit() }
            f.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) = commit()
            })
        }
        panel.add(row(AbyssusBundle.message("propertiesPartPivotPosition"), line(fields.values.take(3))))
        panel.add(row(AbyssusBundle.message("propertiesPartPivotRotation"), line(fields.values.drop(3))))
        panel.add(error)
        if (pivot == null) {
            panel.add(note(AbyssusBundle.message("propertiesPartPivotNone")))
        } else {
            panel.add(JButton(AbyssusBundle.message("propertiesPartPivotReset")).apply {
                name = "part-pivot-reset"
                addActionListener { setPivot(null) }
            })
        }
        return panel
    }

    private fun hingeSection(): JComponent {
        val panel = JPanel(VerticalLayout(JBUI.scale(4))).apply { name = "part-hinge" }
        panel.add(JBLabel(AbyssusBundle.message("propertiesPartHinge")).apply { font = JBFont.label().asBold() })
        val hinge = state.hinge
        if (hinge == null) {
            panel.add(JButton(AbyssusBundle.message("propertiesPartAddHinge")).apply {
                name = "part-hinge-add"
                addActionListener { writeHinge(PartHinge(-DEFAULT_HINGE, DEFAULT_HINGE), adding = true) }
            })
            return panel
        }
        val min = field("part-hinge-min", hinge.min)
        val max = field("part-hinge-max", hinge.max)
        val error = JBLabel("").apply {
            name = "part-hinge-error"
            foreground = JBColor.RED
            isVisible = false
        }
        fun commit() {
            val lo = min.text.trim().toFloatOrNull()
            val hi = max.text.trim().toFloatOrNull()
            if (lo == null || hi == null) {
                error.text = AbyssusBundle.message("propertiesPartNotNumber")
                error.isVisible = true
                return
            }
            val problem = writer.hingeProblem(lo, hi)
            if (problem != null) {
                error.text = problem
                error.isVisible = true
                return
            }
            error.isVisible = false
            if (PartHinge(lo, hi) != hinge) {
                writeHinge(PartHinge(lo, hi), adding = false)
            }
        }
        listOf(min, max).forEach { f ->
            f.addActionListener { commit() }
            f.addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) = commit()
            })
        }
        panel.add(row(AbyssusBundle.message("propertiesPartHingeMin"), min))
        panel.add(row(AbyssusBundle.message("propertiesPartHingeMax"), max))
        panel.add(error)
        panel.add(row(AbyssusBundle.message("propertiesPartPreview"), previewSlider(hinge)))
        panel.add(JButton(AbyssusBundle.message("propertiesPartRemoveHinge")).apply {
            name = "part-hinge-remove"
            addActionListener { removeHinge() }
        })
        return panel
    }

    /** A slider from the hinge's min to its max, at 0; moving it previews the deflection in the Scene views only. */
    private fun previewSlider(hinge: PartHinge): JSlider {
        val scale = PREVIEW_STEPS_PER_DEGREE
        return JSlider((hinge.min * scale).toInt(), (hinge.max * scale).toInt(), 0).apply {
            name = "part-hinge-preview"
            toolTipText = AbyssusBundle.message("propertiesPartPreviewTooltip")
            addChangeListener {
                val degrees = value.toFloat() / scale
                HingePreviews.of(project).set(
                    target.file,
                    degrees.takeIf { it != 0f }?.let { HingePreview(target.entityId, target.node, it) },
                )
            }
            preview = this
        }
    }

    private fun setPivot(pivot: PartPivot?) {
        val dir = state.projectDir ?: return
        val info = state.info ?: return
        writer.setPivot(dir, target.asset, target.node, pivot, info, target.file)
    }

    private fun writeHinge(hinge: PartHinge, adding: Boolean) {
        val dir = state.projectDir ?: return
        endPreview()
        writer.setHinge(dir, target.asset, target.node, hinge, adding)
    }

    private fun removeHinge() {
        val dir = state.projectDir ?: return
        endPreview()
        writer.clearHinge(dir, target.asset, target.node)
    }

    /** The preview ends when the hinge changes: the view draws the rest pose again. */
    private fun endPreview() {
        preview?.value = 0
        HingePreviews.of(project).set(target.file, null)
    }

    /** Ends the preview when the section goes away (another selection). */
    override fun removeNotify() {
        super.removeNotify()
        if (preview != null) {
            HingePreviews.of(project).set(target.file, null)
        }
    }

    private fun field(fieldName: String, value: Float) = JBTextField(numberText(value), 7).apply { name = fieldName }

    private fun numberText(value: Float): String =
        if (value == value.toInt().toFloat() && kotlin.math.abs(value) < 1e6f) value.toInt().toString() else value.toString()

    private fun line(fields: List<JBTextField>): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply { fields.forEach { add(it) } }

    private fun row(label: String, component: JComponent): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            add(JBLabel(label))
            add(component)
        }

    private fun note(text: String) = JBLabel(text).apply { foreground = JBColor.GRAY }

    private companion object {
        const val DEFAULT_HINGE = 30f
        const val PREVIEW_STEPS_PER_DEGREE = 10
    }
}
