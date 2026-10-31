package net.nevinsky.abyssus.uitesting.headless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The maintained UI coverage inventory in `docs/ai/testing.md` must map every scenario ID of the
 * `plugin-ui-regression-coverage` spec and every implemented entry point, with an honest status (COV-03): a scenario
 * without a row, a `passed` row without an existing test and an `unverified` row without a reason all fail here.
 */
class CoverageInventoryTest {
    private val root: Path = generateSequence(Paths.get(System.getProperty("abyssus.ui.fixtureSource")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }

    private val inventory: String = Files.readString(root.resolve("docs/ai/testing.md")).let { text ->
        val begin = text.indexOf("<!-- ui-coverage:begin -->")
        val end = text.indexOf("<!-- ui-coverage:end -->")
        assertTrue("docs/ai/testing.md has no ui-coverage inventory markers", begin >= 0 && end > begin)
        text.substring(begin, end)
    }

    /** The spec of this change while it is open, else the archived main spec. */
    private fun specText(): String = listOf(
        "openspec/specs/plugin-ui-regression-coverage/spec.md",
        "openspec/changes/add-plugin-full-cycle-ui-tests/specs/plugin-ui-regression-coverage/spec.md",
    ).map(root::resolve).first(Files::exists).let(Files::readString)

    private data class Row(val cells: List<String>)

    private val idPattern = Regex("^[A-Z]+-\\d+$")

    private val scenarioRows: List<Row> = inventory.lines().filter { it.startsWith("| ") }
        .map { line -> Row(line.trim().trim('|').split("|").map(String::trim)) }
        .filter { idPattern.matches(it.cells.first()) }

    private val specIds: List<String> = Regex("#### Scenario: ([A-Z]+-\\d+) ").findAll(specText()).map { it.groupValues[1] }.toList()

    @Test
    fun everySpecScenarioHasExactlyOneRowAndNoRowIsUnknown() {
        val rowIds = scenarioRows.map { it.cells.first() }
        assertEquals("duplicate rows: ${rowIds.groupBy { it }.filterValues { it.size > 1 }.keys}", rowIds.size, rowIds.toSet().size)
        assertTrue("spec has no scenario IDs", specIds.isNotEmpty())
        assertEquals("unmapped scenario IDs", emptyList<String>(), specIds - rowIds.toSet())
        assertEquals("rows for scenario IDs the spec does not have", emptyList<String>(), rowIds - specIds.toSet())
    }

    @Test
    fun statusesAreHonest() {
        for (row in scenarioRows) {
            val (id, _, test, status, reason) = row.cells.let { it + List(5 - it.size) { "" } }
            when (status) {
                "passed" -> assertTrue("$id is passed but names no existing test: '$test'", test.split(",").map(String::trim).any(::testExists))
                "failed" -> Unit
                "unverified" -> assertTrue("$id is unverified without a reason", reason.length >= 8)
                else -> throw AssertionError("$id has status '$status', expected passed, failed or unverified")
            }
        }
    }

    /** `Class.method` where `fun method` exists in a source file named `Class.kt` under the plugin or library tests. */
    private fun testExists(name: String): Boolean {
        val (cls, method) = name.removeSurrounding("`").split(".", limit = 2).let { it[0] to it.getOrNull(1) }
        if (method == null) return false
        val files = Files.walk(root.resolve("projects")).use { walk -> walk.filter { it.fileName.toString() == "$cls.kt" }.toList() }
        return files.any { Files.readString(it).contains(Regex("fun\\s+`?${Regex.escape(method)}`?\\s*\\(")) }
    }

    @Test
    fun everyPluginActionIsListed() {
        val xml = Files.readString(root.resolve("projects/plugin-abyssus/src/main/resources/META-INF/plugin.xml"))
        val actions = Regex("<action id=\"(Abyssus\\.[^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList()
        assertTrue(actions.isNotEmpty())
        assertEquals("plugin actions missing from the inventory", emptyList<String>(), actions.filter { "`$it`" !in inventory })
    }

    @Test
    fun everyFieldEditorTypeAndWidgetKindIsListed() {
        val fieldKinds = Regex("enum class FieldKind \\{([^}]*)}").find(
            Files.readString(root.resolve("projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/components/ComponentCodec.kt")),
        )!!.groupValues[1].split(",").map(String::trim).filter(String::isNotEmpty)
        assertEquals("field editor types missing", emptyList<String>(), fieldKinds.filter { "`$it`" !in inventory })

        val ui = Files.readString(root.resolve("projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/ui/model/UiModel.kt"))
        val widgets = Regex("enum class UiWidgetType\\([^)]*\\) \\{(.*?)\\n}", RegexOption.DOT_MATCHES_ALL).find(ui)!!.groupValues[1]
            .let { Regex("\\b([A-Z][A-Z_]+)\\(\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
        assertTrue("no widget kinds parsed", widgets.size >= 10)
        assertEquals("widget kinds missing", emptyList<String>(), widgets.filter { "`$it`" !in inventory })
    }

    @Test
    fun everyModeledComponentKindIsListed() {
        val builtIn = Regex("kind<([A-Za-z]+)>").findAll(
            Files.readString(root.resolve("projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/components/BuiltInComponentKinds.kt")),
        ).map { it.groupValues[1] }.toList()
        val physics = Regex("kind\\((\\w+)::class").findAll(
            Files.readString(root.resolve("projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/physics/PhysicsComponentKinds.kt")),
        ).map { it.groupValues[1] }.toList()
        assertTrue(builtIn.size >= 7 && physics.size >= 3)
        assertEquals("component kinds missing", emptyList<String>(), (builtIn + physics).filter { "`$it`" !in inventory })
    }
}
