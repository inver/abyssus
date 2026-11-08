package net.nevinsky.abyssus.lib.core.assets.model

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import net.nevinsky.abyssus.lib.core.assets.ModelKey
import net.nevinsky.abyssus.lib.core.assets.testFileLoader
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.Logger

class ModelLoaderPivotTest {
    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private fun warnings(sink: MutableList<String>): Logger =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Logger::class.java)) { _, method, args ->
            if (method.name == "warn" && args != null && args[0] is String) {
                val rest = args.drop(1).flatMap { if (it is Array<*>) it.toList() else listOf(it) }
                sink += org.slf4j.helpers.MessageFormatter.arrayFormat(args[0] as String, rest.toTypedArray()).message
                null
            } else {
                method.invoke(org.slf4j.helpers.NOPLogger.NOP_LOGGER, *(args ?: emptyArray()))
            }
        } as Logger

    private fun copy(): File {
        GdxNativesLoader.load()
        val root = Files.createTempDirectory("model-pivot").toFile().also(dirs::add)
        val source = File(testProject("Untitled"), "assets/model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")
        assertTrue(source.copyRecursively(File(root, "assets/model")))
        return root
    }

    private fun nodes(list: Iterable<ModelNode>): List<ModelNode> = list.flatMap { listOf(it) + nodes(it.children.orEmpty().asIterable()) }

    private fun writeParts(root: File, partsJson: String) {
        val file = File(root, "assets/model/meta.json")
        file.writeText(file.readText().replace(Regex("\"materials\"\\s*:\\s*\\[\\s*]"), "\"materials\": [], \"parts\": $partsJson"))
        assertTrue(file.readText().contains("parts"))
    }

    private fun load(root: File, key: ModelKey, log: Logger): ModelData {
        val files = testFileLoader(root)
        val loader = ModelLoader(testMetaLoader(root, fileLoader = files), AssimpModelLoader(), files, null, false, log)
        val staged = checkNotNull(loader.prepare(key.encode())).staged
        return staged.data
    }

    @Test
    fun aPivotMovesTheNodeAndEveryVariantSharesIt() {
        val plain = copy()
        val raw = AssimpModelLoader().loadData(FileHandle(File(plain, "assets/model/model.gltf")))
        val target = nodes(raw.nodes.asIterable()).first { it.parts?.isNotEmpty() == true }
        val before = target.translation?.cpy()
        val meshId = raw.meshes.first { m -> m.parts.any { it.id == target.parts[0].meshPartId } }.id!!

        val withPivot = copy()
        writeParts(withPivot, """{"${target.id}":{"pivot":{"position":{"x":0.25,"y":0,"z":0}}}}""")
        val sink = mutableListOf<String>()
        val whole = load(withPivot, ModelKey("model"), warnings(sink))
        val moved = nodes(whole.nodes.asIterable()).first { it.id == target.id }
        assertNotEquals(before, moved.translation)
        assertTrue(sink.toString(), sink.isEmpty())

        val variant = load(withPivot, ModelKey("model", meshId), warnings(sink))
        val inVariant = nodes(variant.nodes.asIterable()).first { it.id == target.id }
        assertEquals(moved.translation, inVariant.translation)
        assertEquals(moved.rotation, inVariant.rotation)
    }

    @Test
    fun aRefusalIsLoggedOnce() {
        val root = copy()
        writeParts(root, """{"NoSuchNode":{"pivot":{"position":{"x":1,"y":0,"z":0}}},"AlsoBad":{"hinge":{"min":10,"max":20}}}""")
        val sink = mutableListOf<String>()
        load(root, ModelKey("model"), warnings(sink))
        assertEquals(sink.toString(), 2, sink.size)
        assertEquals(1, sink.count { it.contains("NoSuchNode") })
        assertEquals(1, sink.count { it.contains("AlsoBad") })
    }
}
