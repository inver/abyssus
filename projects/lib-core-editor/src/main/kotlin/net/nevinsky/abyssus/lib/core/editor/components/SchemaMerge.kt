/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.components

import net.nevinsky.abyssus.lib.core.assets.runCatchingKeepingCancellation
import net.nevinsky.abyssus.lib.core.editor.EditorMessages
import net.nevinsky.abyssus.lib.core.editor.document.documentDisplayMessage
import net.nevinsky.abyssus.lib.runtime.schema.BUILT_IN_COMPONENTS
import net.nevinsky.abyssus.lib.runtime.schema.ComponentSchema
import net.nevinsky.abyssus.lib.runtime.schema.SchemaFile

/** A project's schema file: its [path] (for messages) and [text], null when it cannot be read ([error] says why). */
data class ProjectSchemaText(val path: String, val text: String?, val error: String? = null)

/** A schema a plugin contributes: the [plugin]'s name and the [text] of its resource, null when missing. */
data class ContributedSchemaText(val plugin: String, val resource: String, val text: String?)

/** The component schemas in force for one project's scenes, with a message for each problem met reading them. */
class SchemaSnapshot(
    val components: List<ComponentSchema>,
    val problems: List<String>,
    private val messages: EditorMessages
) {
    /** The editor for scenes under these schemas. */
    val editor: ComponentEditor by lazy { ComponentEditor(messages, components) }
}

/**
 * Merges a project's schema with the contributed ones. A pure function of its inputs: the project's declaration wins
 * per short name (with one message naming the component and the plugin), the first contribution wins between plugins,
 * and a file that cannot be read declares nothing and gives one message naming it.
 */
class SchemaMerge(private val messages: EditorMessages, private val file: SchemaFile = SchemaFile()) {
    fun merge(project: ProjectSchemaText?, contributed: List<ContributedSchemaText>): SchemaSnapshot {
        val problems = ArrayList<String>()
        val byName = LinkedHashMap<String, ComponentSchema>()
        val owners = HashMap<String, String?>() // null: the project

        if (project != null) {
            val name = project.path
            for (c in parse(project.text, project.error) {
                problems += messages.message(
                    "schemaFileProblem",
                    name,
                    it
                )
            }) {
                when (c.name) {
                    in BUILT_IN_COMPONENTS -> problems += messages.message("schemaBuiltIn", c.name, name)
                    in byName -> problems += messages.message("schemaDuplicate", c.name, name)
                    else -> {
                        byName[c.name] = c; owners[c.name] = null
                    }
                }
            }
        }
        for (contribution in contributed) {
            val report =
                { message: String -> problems += messages.message("schemaPluginProblem", contribution.plugin, message) }
            val error = if (contribution.text == null) "${contribution.resource} is missing" else null
            for (c in parse(contribution.text, error, report)) {
                when {
                    c.name in BUILT_IN_COMPONENTS -> problems += messages.message(
                        "schemaBuiltIn",
                        c.name,
                        contribution.plugin
                    )

                    c.name !in byName -> {
                        byName[c.name] = c; owners[c.name] = contribution.plugin
                    }

                    owners[c.name] == null -> problems += messages.message(
                        "schemaConflict",
                        c.name,
                        contribution.plugin
                    )

                    else -> problems += messages.message("schemaDuplicate", c.name, contribution.plugin)
                }
            }
        }
        return SchemaSnapshot(byName.values.toList(), problems, messages)
    }

    private fun parse(text: String?, error: String?, report: (String) -> Unit): List<ComponentSchema> {
        if (text == null) {
            error?.let(report)
            return emptyList()
        }
        val parsed = runCatchingKeepingCancellation { file.parse(text) }
            .getOrElse { report(it.documentDisplayMessage(messages)); return emptyList() }
        parsed.problems.forEach(report)
        return parsed.components
    }
}
