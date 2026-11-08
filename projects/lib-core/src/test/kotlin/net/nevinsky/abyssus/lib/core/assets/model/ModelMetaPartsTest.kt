package net.nevinsky.abyssus.lib.core.assets.model

import java.io.File
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test
import org.slf4j.helpers.NOPLogger

class ModelMetaPartsTest {
    private val json = JsonProcessor(NOPLogger.NOP_LOGGER)

    @Test
    fun partsBindWithPivotAndHinge() {
        val meta =
            json.parse(
                """{"file":"model.glb","format":"GLTF","binary":true,"parts":{
                "LeftFlap":{"pivot":{"position":{"x":0,"y":-0.002,"z":0}},"hinge":{"min":-35,"max":35}},
                "RightFlap":{"pivot":{"rotation":{"x":0,"y":0,"z":0.7071,"w":0.7071}}}}}""",
                ModelMeta::class.java,
            )
        val left = checkNotNull(meta.parts).getValue("LeftFlap")
        assertEquals(-0.002f, left.pivot!!.position!!.y, 0f)
        assertNull(left.pivot!!.rotation)
        assertEquals(PartHinge(-35f, 35f), left.hinge)
        assertEquals(0.7071f, meta.parts!!.getValue("RightFlap").pivot!!.rotation!!.w, 0f)
        assertNull(meta.parts!!.getValue("RightFlap").hinge)
    }

    @Test
    fun aMetaWithoutPartsPrintsNoPartsKey() {
        val text = json.toString(ModelMeta(file = "model.glb", format = ModelMeta.Format.GLTF, binary = true))
        assertFalse(text, text.contains("parts"))
    }

    @Test
    fun partsRoundTrip() {
        val meta = ModelMeta(file = "m.glb", parts = mapOf("A" to ModelPartMeta(hinge = PartHinge(-1f, 2f))))
        assertEquals(meta, json.parse(json.toString(meta), ModelMeta::class.java))
    }

    @Test
    fun untitledPiperHasNoParts() {
        val text = File(testProject("Untitled"), "assets/model_900f6f61-6384-434a-be81-56ce303fbb56/meta.json").readText()
        val additional = json.readObject(text).get("additional")
        assertNull(json.bind(additional, ModelMeta::class.java).parts)
    }
}
