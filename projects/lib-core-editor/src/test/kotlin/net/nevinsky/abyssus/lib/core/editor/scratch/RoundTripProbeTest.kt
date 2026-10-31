package net.nevinsky.abyssus.lib.core.editor.scratch

import net.nevinsky.abyssus.lib.core.assets.AssetMetaParser
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Test
import org.slf4j.helpers.NOPLogger
import java.io.File

class RoundTripProbeTest {
    @Test fun probe() {
        val json = JsonProcessor(NOPLogger.NOP_LOGGER)
        val parser = AssetMetaParser(json)
        val out = StringBuilder()
        File("../plugin-abyssus/src/test/testData/project").walkTopDown().filter { it.name == "meta.json" }.sortedBy { it.path }.forEach { f ->
            val text = f.readText()
            val written = runCatching { parser.write(parser.read(f.parentFile.name, text), pretty = false) }.getOrElse { "ERR $it" }
            val a = json.readObject(text); val b = runCatching { json.readObject(written) }.getOrNull()
            out.append("== ${f.parentFile.parentFile.parentFile.name}/${f.parentFile.name}\n")
            if (b == null) out.append("  unreadable $written\n") else {
                fun keys(n: com.fasterxml.jackson.databind.JsonNode, p: String, acc: MutableSet<String>) { if (n.isObject) n.properties().forEach { (k, v) -> acc += "$p$k"; keys(v, "$p$k.", acc) } }
                val ka = mutableSetOf<String>(); val kb = mutableSetOf<String>(); keys(a, "", ka); keys(b, "", kb)
                out.append("  lost: ${ka - kb}\n  added: ${kb - ka}\n")
            }
        }
        File("build/probe.txt").writeText(out.toString())
    }
}
