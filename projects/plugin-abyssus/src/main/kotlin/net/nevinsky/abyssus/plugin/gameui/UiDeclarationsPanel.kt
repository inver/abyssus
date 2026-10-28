/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.gameui

import net.nevinsky.abyssus.lib.core.ui.model.displayAt
import net.nevinsky.abyssus.lib.core.editor.ui.*
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.assets.ui.UiPreparedResource
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourcePreparer
import net.nevinsky.abyssus.lib.core.assets.ui.UiResourceResolver
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingAccess
import net.nevinsky.abyssus.lib.core.ui.model.UiBindingType
import net.nevinsky.abyssus.lib.core.ui.model.UiResourceKind
import net.nevinsky.abyssus.lib.core.ui.model.UiStyle
import net.nevinsky.abyssus.plugin.AbyssusBundle
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.ui.documentDisplayMessage
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.*

/** One input of a [UiFormDialog]: a text box, or a drop-down when [options] is given. */
class FormInput(val key: String, val labelKey: String, val initial: String, val options: List<String>? = null)

/**
 * A small modal form. [validate] turns the entered strings into the errors to show (empty: valid, OK enabled). The
 * dialog never touches a document; the caller applies the result.
 */
class UiFormDialog(project: Project?, titleKey: String, private val inputs: List<FormInput>, private val validate: (Map<String, String>) -> List<FormError>) : DialogWrapper(project) {
    private val controls = LinkedHashMap<String, JComponent>()
    private val errorLabel = JBLabel(" ")

    init {
        title = AbyssusBundle.message(titleKey)
        init()
        revalidateForm()
    }

    fun values(): Map<String, String> = controls.mapValues { (_, c) -> if (c is JComboBox<*>) c.selectedItem as? String ?: "" else (c as JTextField).text }

    private fun revalidateForm() {
        val errors = validate(values())
        errorLabel.text = errors.firstOrNull()?.let { "${AbyssusBundle.message(inputs.first { i -> i.key == it.field }.labelKey)}: ${AbyssusBundle.message("uiFormError.${it.message}")}" } ?: " "
        isOKActionEnabled = errors.isEmpty()
    }

    override fun createCenterPanel(): JComponent = JPanel(GridBagLayout()).also { panel ->
        inputs.forEachIndexed { row, input ->
            val c: JComponent = if (input.options != null) JComboBox(input.options.toTypedArray()).also { it.selectedItem = input.initial; it.addActionListener { revalidateForm() } }
            else JTextField(input.initial, 18).also { field -> field.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = revalidateForm()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = revalidateForm()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = revalidateForm()
            }) }
            c.name = "form-${input.key}"
            controls[input.key] = c
            panel.add(JBLabel(AbyssusBundle.message(input.labelKey)), GridBagConstraints().apply { gridx = 0; gridy = row; anchor = GridBagConstraints.WEST; insets = Insets(2, 2, 2, 8) })
            panel.add(c, GridBagConstraints().apply { gridx = 1; gridy = row; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; insets = Insets(2, 2, 2, 2) })
        }
        panel.add(errorLabel, GridBagConstraints().apply { gridx = 0; gridy = inputs.size; gridwidth = 2; anchor = GridBagConstraints.WEST })
    }
}

/**
 * Declarations of the UI asset: bindings (name, type, access, sample, constraints), named styles and image / bitmap-font
 * resources. Entries are listed with their problems (a resource that is missing or too large says so); adding,
 * editing, removing and importing each make one document edit through the controller.
 */
class UiDeclarationsPanel(private val project: Project?, private val controller: UiEditorController) : JPanel(BorderLayout()) {
    private val bindingsModel = DefaultListModel<String>()
    private val stylesModel = DefaultListModel<String>()
    private val resourcesModel = DefaultListModel<String>()
    internal val bindings = JBList(bindingsModel)
    internal val styles = JBList(stylesModel)
    internal val resources = JBList(resourcesModel)
    private var bindingNames = emptyList<String>()
    private var styleNames = emptyList<String>()
    private var resourceIds = emptyList<String>()

    init {
        val tabs = JTabbedPane()
        tabs.addTab(AbyssusBundle.message("uiBindingsTab"), tab(bindings, listOf(
            button("add-binding", "uiAdd") { editBinding(null) }, button("edit-binding", "uiEdit") { bindingNames.getOrNull(bindings.selectedIndex)?.let(::editBinding) },
            button("remove-binding", "uiRemove") { bindingNames.getOrNull(bindings.selectedIndex)?.let { n -> controller.edit(AbyssusBundle.message("uiCommandEditBinding")) { e, m -> e.deleteBinding(m, n) } } })))
        tabs.addTab(AbyssusBundle.message("uiStylesTab"), tab(styles, listOf(
            button("add-style", "uiAdd") { editStyle(null) }, button("edit-style", "uiEdit") { styleNames.getOrNull(styles.selectedIndex)?.let(::editStyle) },
            button("remove-style", "uiRemove") { styleNames.getOrNull(styles.selectedIndex)?.let { n -> controller.edit(AbyssusBundle.message("uiCommandEditStyle")) { e, m -> e.deleteStyle(m, n) } } })))
        tabs.addTab(AbyssusBundle.message("uiResourcesTab"), tab(resources, listOf(
            button("import-image", "uiImportImage") { importFile(font = false) }, button("import-font", "uiImportFont") { importFile(font = true) },
            button("remove-resource", "uiRemove") { resourceIds.getOrNull(resources.selectedIndex)?.let { id -> controller.edit(AbyssusBundle.message("uiCommandEditResource")) { e, m -> e.deleteResource(m, id) } } })))
        add(tabs, BorderLayout.CENTER)
        controller.addListener(::refresh)
        refresh()
    }

    private fun tab(list: JBList<String>, buttons: List<JButton>) = JPanel(BorderLayout()).apply {
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply { buttons.forEach(::add) }, BorderLayout.SOUTH)
    }

    private fun button(name: String, key: String, action: () -> Unit) = JButton(AbyssusBundle.message(key)).apply { this.name = name; addActionListener { action() } }

    internal fun refresh() {
        val c = controller.collection
        bindingNames = c?.bindings?.map { it.name }.orEmpty()
        styleNames = c?.styles?.map { it.name }.orEmpty()
        resourceIds = c?.resources?.map { it.id }.orEmpty()
        bindingsModel.clear(); stylesModel.clear(); resourcesModel.clear()
        c?.bindings?.forEach { bindingsModel.addElement("${it.name} : ${it.type.json}${if (it.access == UiBindingAccess.WRITE) " (write)" else ""}") }
        c?.styles?.forEach { stylesModel.addElement(it.name) }
        if (c != null) {
            val dir = File(controller.file.parent.path)
            val prepared = runCatchingKeepingCancellation { UiResourcePreparer(UiResourceResolver(dir)).prepare(c) }.getOrNull()
            c.resources.forEach { r ->
                val problem = (prepared?.get(r.id) as? UiPreparedResource.Failed)?.let { "  — ${AbyssusBundle.message("uiResourceIssue.${it.problem.name}")}" }.orEmpty()
                resourcesModel.addElement("${r.id} (${if (r.kind == UiResourceKind.IMAGE) "image" else "font"})  ${r.path}$problem")
            }
        }
    }

    // ---- bindings ----

    private fun editBinding(name: String?) {
        val existing = name?.let { controller.collection?.binding(it) }
        val start = existing?.let(::bindingFormOf) ?: BindingForm()
        val dialog = UiFormDialog(project, "uiBindingDialog", listOf(
            FormInput("name", "uiFormName", start.name),
            FormInput("type", "uiFormType", start.type, UiBindingType.entries.map { it.json }),
            FormInput("access", "uiFormAccess", start.access, UiBindingAccess.entries.map { it.json }),
            FormInput("sample", "uiFormSample", start.sample), FormInput("min", "uiFormMin", start.min), FormInput("max", "uiFormMax", start.max),
            FormInput("step", "uiFormStep", start.step), FormInput("maxLength", "uiFormMaxLength", start.maxLength),
            FormInput("fields", "uiFormFields", start.fields),
        )) { v -> (bindingMembers(formOf(v)) as? FormResult.Invalid)?.errors.orEmpty() }
        if (!dialog.showAndGet()) return
        val form = formOf(dialog.values())
        val members = (bindingMembers(form) as? FormResult.Valid)?.value ?: return
        controller.edit(AbyssusBundle.message("uiCommandEditBinding")) { e, m -> e.upsertBinding(m, form.name.trim(), form.type, members) }
    }

    private fun formOf(v: Map<String, String>) = BindingForm(v["name"].orEmpty(), v["type"].orEmpty(), v["access"].orEmpty(), v["sample"].orEmpty(),
        v["min"].orEmpty(), v["max"].orEmpty(), v["step"].orEmpty(), v["maxLength"].orEmpty(), v["fields"].orEmpty())

    // ---- styles ----

    private fun editStyle(name: String?) {
        val existing: UiStyle? = name?.let { n -> controller.collection?.styles?.firstOrNull { it.name == n } }
        fun at(vararg path: String): String = existing?.displayAt(*path).orEmpty()
        val fonts = listOf("") + controller.collection?.resources?.filter { it.kind == UiResourceKind.BITMAP_FONT }?.map { it.id }.orEmpty()
        val dialog = UiFormDialog(project, "uiStyleDialog", listOf(
            FormInput("name", "uiFormName", existing?.name.orEmpty()), FormInput("fontSize", "uiFormFontSize", at("fontSize")),
            FormInput("color", "uiFormColor", at("color")), FormInput("background", "uiFormBackground", at("background")),
            FormInput("padding", "uiFormPadding", at("padding")), FormInput("font", "uiFormFont", at("font"), fonts),
            FormInput("hover", "uiFormHover", at("states", "hover", "background")), FormInput("pressed", "uiFormPressed", at("states", "pressed", "background")),
            FormInput("disabled", "uiFormDisabled", at("states", "disabled", "background")),
        )) { v -> (styleValues(styleForm(v)) as? FormResult.Invalid)?.errors.orEmpty() }
        if (!dialog.showAndGet()) return
        val form = styleForm(dialog.values())
        val values = (styleValues(form) as? FormResult.Valid)?.value ?: return
        controller.edit(AbyssusBundle.message("uiCommandEditStyle")) { e, m -> e.upsertStyle(m, form.name.trim(), values) }
    }

    private fun styleForm(v: Map<String, String>) = StyleForm(v["name"].orEmpty(), v["fontSize"].orEmpty(), v["color"].orEmpty(), v["background"].orEmpty(),
        v["padding"].orEmpty(), v["font"].orEmpty(), v["hover"].orEmpty(), v["pressed"].orEmpty(), v["disabled"].orEmpty())

    // ---- resources ----

    private fun importFile(font: Boolean) {
        val descriptor = resourceChooserDescriptor(font)
        val chosen = net.nevinsky.abyssus.plugin.ui.chooseFileOrOverride(descriptor, project) ?: return
        importResource(File(chosen.path), font)
    }

    /** Stages and runs one import; reports a refusal to the user. Public so the dialog-free path is testable. */
    internal fun importResource(source: File, font: Boolean, report: (String) -> Unit = { Messages.showErrorDialog(project, AbyssusBundle.message("uiImportFailed", it), AbyssusBundle.message("uiResourcesTab")) }): AssetCommandResult? {
        val p = project ?: return null
        val importer = UiResourceImporter()
        val staged = runCatchingKeepingCancellation { if (font) importer.stageFont(controller.file, source) else importer.stageImage(controller.file, source) }
            .getOrElse { report(it.documentDisplayMessage()); return null }
        val result = importUiResource(p, controller.file, staged)
        when (result) {
            AssetCommandResult.Done -> controller.reload()
            is AssetCommandResult.Conflict -> report(AbyssusBundle.message("uiImportConflict", File(result.path).name))
            is AssetCommandResult.Collision -> report(AbyssusBundle.message("uiImportConflict", result.path))
            is AssetCommandResult.Blocked -> report(result.reason)
            AssetCommandResult.Cancelled -> report(AbyssusBundle.message("uiAssetCancelled"))
            is AssetCommandResult.Failed -> report(result.cause.documentDisplayMessage())
        }
        return result
    }
}

/** The chooser of Import Image (png, jpg, jpeg) or Import Font (a bitmap font's `.fnt`); the first argument of the filter is its label, the rest the extensions. */
internal fun resourceChooserDescriptor(font: Boolean): com.intellij.openapi.fileChooser.FileChooserDescriptor =
    FileChooserDescriptorFactory.createSingleFileDescriptor().withExtensionFilter(
        AbyssusBundle.message(if (font) "uiImportFontFilter" else "uiImportImageFilter"),
        *(if (font) arrayOf("fnt") else arrayOf("png", "jpg", "jpeg")),
    )
