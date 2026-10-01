package net.nevinsky.abyssus.sceneview

import org.junit.Assert.assertFalse
import org.junit.Test
import javax.swing.JPanel

class RenderLoopTest {
    @Test
    fun componentNotOnScreenIsNotRendered() {
        assertFalse(canRender(JPanel()))
    }
}
