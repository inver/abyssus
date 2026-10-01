package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.Disposable
import javax.swing.JComponent

/** A live view of a scene. The editor talks to this so the GL-backed [SceneViewPanel] can be replaced in tests. */
interface SceneView : Disposable {
    val view: JComponent

    /** Called on the AWT thread when rendering fails and the view has stopped. */
    var onFailure: ((Throwable) -> Unit)?

    fun setParams(params: SceneRenderParams)
}
