package net.nevinsky.abyssus.app.game.controlline

import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldSceneModelsTest {
    @Test
    fun theFieldSceneNamesTheModelsAndTerrainsItDraws() {
        val field = FieldScene(loadField())
        assertTrue("no model entity was found, so nothing would be drawn", field.models.isNotEmpty())
        assertTrue("no terrain entity was found", field.terrains.isNotEmpty())
    }
}
