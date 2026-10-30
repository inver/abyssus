package net.nevinsky.abyssus.lib.core.ui

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.ui.layout.UiLayoutEngine
import net.nevinsky.abyssus.lib.core.ui.layout.UiMeasurer
import net.nevinsky.abyssus.lib.core.ui.layout.UiSizeF
import net.nevinsky.abyssus.lib.core.ui.model.*
import net.nevinsky.abyssus.lib.core.ui.state.*
import net.nevinsky.abyssus.lib.core.ui.validation.UiCollectionDecoder
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Keeps the host example of `projects/lib-core/README.md` compiling, running and identical to the documented text. */
class UiHostContractTest {
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)

    private fun repoFile(path: String) = File(checkNotNull(System.getProperty("abyssus.testData")), "../../../../../$path").canonicalFile

    private val meta = """{"format":"abyssus","formatVersion":1,"type":"UI","additional":{"ui":{"schemaVersion":1,
        "bindings":[{"name":"volume","type":"number","access":"write","sample":0.5,"min":0,"max":1}],
        "screens":[{"id":"main","root":{"id":"root","type":"column","children":[
          {"id":"slider","type":"slider","bindings":{"value":"volume"}},
          {"id":"start","type":"button","actions":{"click":{"type":"emitAction","name":"startGame"}}}]}}]}}}"""

    // example:begin
    class HostSettings(var volume: Double = 0.5)

    fun runHost(metaTree: com.fasterxml.jackson.databind.JsonNode, settings: HostSettings, started: MutableList<String>): UiBindingSession {
        // 1. Admit the asset: nothing runs from a refused collection.
        val admission = UiCollectionDecoder().decode(metaTree) as UiAdmission.Admitted
        val collection = admission.collection

        // 2. The host owns the callbacks. Writes are synchronous; schedule slow work yourself and answer now.
        val session = UiBindingSession(collection) { write ->
            if (write.binding == "volume" && write.value is UiValue.Num) {
                settings.volume = write.value.value
                UiWriteResult.Accepted(UiValue.Num(settings.volume))
            } else UiWriteResult.Rejected("read-only")
        }
        val navigator = UiNavigator("menus", collection, { null }, { name -> started += name; true })

        // 3. Once per update, publish an immutable snapshot. Publishing never writes back.
        session.publish(UiSnapshot(mapOf("volume" to UiValue.Num(settings.volume))))

        // 4. Lay out for the real viewport; the same engine runs in the editor preview.
        val screen = collection.screen(navigator.currentScreen()!!.screenId)!!
        val layout = UiLayoutEngine(UiMeasurer { UiSizeF(100f, 24f) }).resolve(screen, 1280f, 720f)
        check(layout.diagnostics.isEmpty())

        // 5. Report input: a slider commits once on release, a button activates its action.
        val slider = screen.root.children[0]
        if (commitsOn(UiWidgetType.SLIDER, UiEditGesture.RELEASE)) session.commit(slider, UiValue.Num(0.8))
        navigator.activate(screen.root.children[1].actions.getValue("click"))
        return session
    }
    // example:end

    @Test fun theExampleRunsAsDocumented() {
        val settings = HostSettings()
        val started = mutableListOf<String>()
        val session = runHost(json.readObject(meta), settings, started)
        assertEquals(0.8, settings.volume, 0.0)
        assertEquals(listOf("startGame"), started)
        assertEquals(0.8, (session.view().value("volume") as UiValue.Num).value, 0.0)
    }

    @Test fun theReadmeShowsTheCompiledExample() {
        val source = repoFile("projects/lib-core/src/test/kotlin/net/nevinsky/abyssus/lib/core/ui/UiHostContractTest.kt").readText()
        val compiled = source.substringAfter("// example:begin\n").substringBefore("    // example:end")
        val readme = repoFile("projects/lib-core/README.md").readText()
        val documented = readme.substringAfter("<!-- ui-host-example -->\n```kotlin\n").substringBefore("```")
        fun normalize(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(normalize(compiled), normalize(documented))
    }
}
