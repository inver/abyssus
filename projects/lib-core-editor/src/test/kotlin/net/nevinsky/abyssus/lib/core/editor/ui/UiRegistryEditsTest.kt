package net.nevinsky.abyssus.lib.core.editor.ui

import net.nevinsky.abyssus.lib.core.editor.document.DocumentKind
import net.nevinsky.abyssus.lib.core.editor.document.DocumentTextEditor
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import net.nevinsky.abyssus.lib.core.editor.document.TextEditOutcome
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class UiRegistryEditsTest {
    private val edits = UiRegistryEdits()
    private val project = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"name":"P","mainCamera":{"near":0.100},"uiAssetNames":["menus"]}""")!!

    private fun edit(text: String, mutate: (com.fasterxml.jackson.databind.JsonNode) -> Boolean) =
        DocumentTextEditor().edit(text, DocumentKind.PROJECT, mutate)

    @Test fun registeringAppendsOnceAndKeepsEverythingElse() {
        val added = edit(project) { edits.register(it, "hud") } as TextEditOutcome.Edited
        assertEquals(listOf("menus", "hud"), SceneJson().parse(added.text)["uiAssetNames"].map { it.asText() })
        assertTrue(added.text.contains("0.100"))
        assertEquals(TextEditOutcome.Unchanged, edit(added.text) { edits.register(it, "hud") })
        assertEquals(TextEditOutcome.Unchanged, edit(project) { edits.register(it, " ") })
    }

    @Test fun aMissingListIsCreatedAndAnEmptiedOneIsRemoved() {
        val bare = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"name":"P"}""")!!
        val with = edit(bare) { edits.register(it, "menus") } as TextEditOutcome.Edited
        assertTrue(with.text.contains("\"uiAssetNames\""))
        val without = edit(with.text) { edits.unregister(it, "menus") } as TextEditOutcome.Edited
        assertEquals("unregistering the last entry restores the original text", bare, without.text)
        assertEquals(TextEditOutcome.Unchanged, edit(bare) { edits.unregister(it, "menus") })
    }

    @Test fun malformedListsAndOddEntriesAreLeftAlone() {
        val odd = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"uiAssetNames":["a",7,null]}""")!!
        val r = edit(odd) { edits.register(it, "b") } as TextEditOutcome.Edited
        assertEquals("""["a",7,null,"b"]""", SceneJson().parse(r.text)["uiAssetNames"].toString())
        val wrong = SceneJson().pretty("""{"format":"abyssus","formatVersion":1,"uiAssetNames":"menus"}""")!!
        assertEquals(TextEditOutcome.Unchanged, edit(wrong) { edits.register(it, "b") })
        assertEquals(TextEditOutcome.Unchanged, edit(wrong) { edits.unregister(it, "menus") })
    }

    @Test fun theNewAssetTemplateIsAdmittedAndEditable() {
        val uuid = UUID.fromString("0b1c0f5e-7a2e-4d0b-9a0a-3a1f6d6e2b11")
        val text = UiAssetTemplate().create(uuid, 1234L)
        val admitted = UiCollectionDecoder().decode(SceneJson().parse(text)) as UiAdmission.Admitted
        assertEquals("main", admitted.collection.entryScreen)
        assertEquals(listOf("root", "title"), listOf(admitted.collection.screens[0].root.id, admitted.collection.screens[0].root.children[0].id))
        assertTrue(admitted.warnings.isEmpty())
        val edited = UiTextEditor().apply(text) { e, m -> e.addWidget(m, "root", "button") }
        assertTrue(edited is UiTextResult.Edited)
        // the template is deterministic for the same inputs
        assertEquals(text, UiAssetTemplate().create(uuid, 1234L))
    }

    @Test fun transformAppliesTheSameAdmissionAsApply() {
        val text = UiAssetTemplate().create(UUID.randomUUID(), 1L)
        val root = SceneJson().parse(text)
        val editor = UiTextEditor()
        assertTrue(editor.transform(root) { e, m -> e.addWidget(m, "root", "slider") } is UiTransform.Changed)
        assertTrue(editor.transform(SceneJson().parse(text)) { e, m -> e.addWidget(m, "title", "slider") } is UiTransform.Rejected)
        val future = SceneJson().parse(text.replace("\"schemaVersion\": 1", "\"schemaVersion\": 3"))
        assertEquals(UiEditError.NOT_EDITABLE, (editor.transform(future) { e, m -> e.addWidget(m, "root", "slider") } as UiTransform.Rejected).error)
    }
}
