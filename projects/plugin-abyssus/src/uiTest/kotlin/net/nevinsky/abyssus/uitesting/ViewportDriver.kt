package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import net.nevinsky.abyssus.lib.core.ecs.component.CAMERA_FOV
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import org.junit.Assume.assumeTrue
import java.awt.Point
import java.time.Duration
import kotlin.math.tan

/** A point in the Scene View canvas, in pixels from its top-left corner. */
data class ViewPoint(val x: Int, val y: Int)

/** The orbit camera as the open view holds it, read without changing anything. */
data class ViewCamera(
    val target: Triple<Float, Float, Float>,
    val distance: Float,
    val yaw: Float,
    val pitch: Float,
    val width: Int,
    val height: Int,
    val fovDegrees: Float = CAMERA_FOV,
) {
    val eye: FloatArray
        get() {
            val cp = kotlin.math.cos(pitch)
            return floatArrayOf(
                target.first + cp * kotlin.math.sin(yaw) * distance,
                target.second + kotlin.math.sin(pitch) * distance,
                target.third + cp * kotlin.math.cos(yaw) * distance,
            )
        }

    /** Where the world point lies in the canvas, or null when it is behind the camera. A plain pinhole projection (libGDX's `lookAt` with +Y up). */
    fun project(x: Float, y: Float, z: Float): ViewPoint? {
        val e = eye
        val f = normalize(floatArrayOf(target.first - e[0], target.second - e[1], target.third - e[2]))
        val right = normalize(cross(f, floatArrayOf(0f, 1f, 0f)))
        val up = cross(right, f)
        val v = floatArrayOf(x - e[0], y - e[1], z - e[2])
        val zc = dot(v, f)
        if (zc <= 0.01f) return null
        val tanHalf = tan(Math.toRadians(fovDegrees / 2.0)).toFloat()
        val aspect = width.toFloat() / height
        val ndcX = dot(v, right) / (zc * tanHalf * aspect)
        val ndcY = dot(v, up) / (zc * tanHalf)
        return ViewPoint(Math.round((ndcX + 1f) / 2f * width), Math.round((1f - ndcY) / 2f * height))
    }

    /** What one canvas pixel is worth in world units at [origin]: the gizmo handles are drawn 90 px long at that scale. */
    fun worldPerPixel(origin: Triple<Float, Float, Float>): Float {
        val e = eye
        val d = kotlin.math.sqrt((origin.first - e[0]).let { it * it } + (origin.second - e[1]).let { it * it } + (origin.third - e[2]).let { it * it })
        return d * 2f * tan(Math.toRadians(fovDegrees / 2.0)).toFloat() / height
    }

    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: FloatArray, b: FloatArray) =
        floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun normalize(a: FloatArray): FloatArray {
        val l = kotlin.math.sqrt(dot(a, a))
        return floatArrayOf(a[0] / l, a[1] / l, a[2] / l)
    }
}

/**
 * Real mouse and keyboard input on the Scene View canvas. The camera is read from the open view (a read-only query);
 * every gesture is `java.awt.Robot` pointer input at coordinates computed from that state, never an edit callback.
 */
class ViewportDriver(private val frame: IdeFrame, private val ide: IdeQueries) {
    private val canvasXpath = "//div[contains(@javaclass, 'SceneGlLifecycle')]"

    val canvas: ComponentFixture get() = frame.find(byXpath(canvasXpath), Duration.ofSeconds(30))

    private fun unavailableMessage(): String? =
        frame.findAll<ComponentFixture>(byXpath("//div[@name='scene-unavailable']")).firstOrNull()
            ?.callJs<String>("component.getText()", runInEdt = true)

    /**
     * Waits for the view to settle on a context. Without one the scenario is skipped as environment-blocked, never
     * reported as covered.
     */
    fun awaitContext(toolbar: SceneViewToolbar) {
        fun moveEnabled() = runCatching { toolbar.isEnabled("Move (W)") }.getOrDefault(false)
        waitFor(Duration.ofSeconds(60), Duration.ofMillis(500), "the view settles on a context or the unavailable message") {
            unavailableMessage() != null || (moveEnabled().also { if (it) Thread.sleep(3000) } && moveEnabled())
        }
        val message = unavailableMessage()
        assumeTrue("environment-blocked, no OpenGL context here: $message", message == null)
        // the view may still replace its canvas (a sample fallback): wait for one that is showing and stays
        var stable = 0
        waitFor(Duration.ofSeconds(45), Duration.ofMillis(500), "the canvas is showing and stays") {
            val showing = runCatching { canvas.callJs<Boolean>("component.isShowing() && component.getWidth() > 0", runInEdt = true) }.getOrDefault(false)
            stable = if (showing) stable + 1 else 0
            stable >= 6
        }
    }

    fun camera(): ViewCamera {
        val raw = canvas.callJs<String>(
            "var c = component; while (c != null && String(c.getClass().getSimpleName()) != 'SceneViewPanel') c = c.getParent();" +
                // `internal val orbit` has a module-mangled getter name: find it by its prefix
                "var ms = c.getClass().getMethods(); var o = null;" +
                "for (var i = 0; i < ms.length; i++) { if (String(ms[i].getName()).indexOf('getOrbit') == 0) { o = ms[i].invoke(c); break; } }" +
                "var t = o.getTarget();" +
                "[t.getX(), t.getY(), t.getZ(), o.getDistance(), o.getYaw(), o.getPitch(), component.getWidth(), component.getHeight()].join(',')",
            runInEdt = true,
        ).split(',')
        return ViewCamera(
            Triple(raw[0].toFloat(), raw[1].toFloat(), raw[2].toFloat()), raw[3].toFloat(), raw[4].toFloat(), raw[5].toFloat(),
            raw[6].toInt(), raw[7].toInt(),
        )
    }

    /** The entity id the view itself has selected (its gizmo target), or null; the view's own state, read through its private field. */
    fun viewSelection(): String? = canvas.callJs<String>(
        "var c = component; while (c != null && String(c.getClass().getSimpleName()) != 'SceneViewPanel') c = c.getParent();" +
            "var f = c.getClass().getDeclaredField('interaction'); f.setAccessible(true);" +
            "var id = f.get(c).getSelectedId(); id == null ? '' : String(id)",
        runInEdt = true,
    ).ifEmpty { null }

    /** The text of the view's stats strip (tool, space, snap, and the frame rate when enabled), from its private painter. */
    fun stats(): String = canvas.callJs<String>(
        "var c = component; while (c != null && String(c.getClass().getSimpleName()) != 'SceneViewPanel') c = c.getParent();" +
            "var f = c.getClass().getDeclaredField('statsStrip'); f.setAccessible(true);" +
            "var o = f.get(c); var ms = o.getClass().getMethods(); var t = '';" +
            "for (var i = 0; i < ms.length; i++) { if (String(ms[i].getName()).indexOf('getText') == 0) { t = String(ms[i].invoke(o)); break; } } t",
        runInEdt = true,
    )

    /** `PositionComponent.localPosition` of entity [id] in [sceneText] (0 for an omitted axis). */
    fun position(sceneText: String, id: Int): Triple<Float, Float, Float> {
        val p = entity(sceneText, id).get("components").get("PositionComponent").get("localPosition")
        fun axis(n: String) = p?.get(n)?.asDouble()?.toFloat() ?: 0f
        return Triple(axis("x"), axis("y"), axis("z"))
    }

    /** Entity [id] of [sceneText]; a scene may hold its entities directly under `ecs` or under `ecs.entities`. */
    fun entity(sceneText: String, id: Int): com.fasterxml.jackson.databind.JsonNode {
        val ecs = SceneJson().parseObject(sceneText).get("ecs")
        return ecs.get(id.toString())
    }

    fun screenOf(world: Triple<Float, Float, Float>): ViewPoint =
        camera().project(world.first, world.second, world.third) ?: error("the point $world is behind the view camera")

    fun click(point: ViewPoint) = canvas.click(Point(point.x, point.y))

    /** Press at [from], move in small steps to [to] and release: one drag with the left button. */
    fun drag(from: ViewPoint, to: ViewPoint, release: Boolean = true) {
        val steps = 12
        canvas.callJs<Boolean>(
            "var p = component.getLocationOnScreen(); var r = new java.awt.Robot(); r.setAutoDelay(15);" +
                "r.mouseMove(p.x + ${from.x}, p.y + ${from.y}); r.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);" +
                "for (var i = 1; i <= $steps; i++) { r.mouseMove(p.x + ${from.x} + Math.round((${to.x} - ${from.x}) * i / $steps), p.y + ${from.y} + Math.round((${to.y} - ${from.y}) * i / $steps)); }" +
                (if (release) "r.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);" else "") + "true",
        )
    }

    /** Turns the mouse wheel over the middle of the canvas by [notches] (positive: toward the user). */
    fun scroll(notches: Int) {
        // the view can still be swapping its canvas right after it settled: retry while the new one is not showing
        waitFor(Duration.ofSeconds(15), Duration.ofMillis(500), "the canvas is showing to take the wheel") {
            runCatching {
                canvas.callJs<Boolean>(
                    "var p = component.getLocationOnScreen(); var r = new java.awt.Robot(); r.setAutoDelay(15);" +
                        "r.mouseMove(p.x + Math.round(component.getWidth() / 2), p.y + Math.round(component.getHeight() / 2)); r.mouseWheel($notches); true",
                    runInEdt = true,
                )
            }.getOrDefault(false)
        }
    }

    fun releaseMouse() {
        canvas.callJs<Boolean>("new java.awt.Robot().mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK); true")
    }

    /**
     * The World X move handle of the entity at [origin]: where to press (just past the middle of the arrow) and the
     * unit screen direction toward its tip.
     */
    fun xHandle(origin: Triple<Float, Float, Float>, axis: Triple<Float, Float, Float> = Triple(1f, 0f, 0f)): Pair<ViewPoint, Pair<Double, Double>> {
        val camera = camera()
        val size = 90f * camera.worldPerPixel(origin)
        val from = camera.project(origin.first, origin.second, origin.third) ?: error("origin behind the camera")
        val tip = camera.project(origin.first + axis.first * size, origin.second + axis.second * size, origin.third + axis.third * size)
            ?: error("handle tip behind the camera")
        val dx = (tip.x - from.x).toDouble()
        val dy = (tip.y - from.y).toDouble()
        val length = kotlin.math.hypot(dx, dy)
        check(length > 20) { "the X handle is only $length px long on screen; reframe the camera" }
        return ViewPoint(Math.round(from.x + dx * 0.55).toInt(), Math.round(from.y + dy * 0.55).toInt()) to (dx / length to dy / length)
    }

    fun press(code: Int) = frame.keyboard { key(code) }

    /**
     * A point on the rotate ring around [axis] (a unit vector) of the entity at [origin] and the unit screen direction
     * along the ring there, at the ring position whose tangent is longest on screen.
     */
    fun ring(origin: Triple<Float, Float, Float>, axis: Triple<Float, Float, Float>): Pair<ViewPoint, Pair<Double, Double>> {
        val camera = camera()
        val radius = 90f * camera.worldPerPixel(origin)
        val a = floatArrayOf(axis.first, axis.second, axis.third)
        // any unit vector u perpendicular to the axis, and v = axis x u
        val seed = if (kotlin.math.abs(a[1]) < 0.9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
        fun cross(p: FloatArray, q: FloatArray) = floatArrayOf(p[1] * q[2] - p[2] * q[1], p[2] * q[0] - p[0] * q[2], p[0] * q[1] - p[1] * q[0])
        fun unit(p: FloatArray) = kotlin.math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]).let { floatArrayOf(p[0] / it, p[1] / it, p[2] / it) }
        val u = unit(cross(a, seed))
        val v = unit(cross(a, u))
        fun at(t: Double): ViewPoint {
            val c = kotlin.math.cos(t).toFloat()
            val s = kotlin.math.sin(t).toFloat()
            return camera.project(
                origin.first + radius * (c * u[0] + s * v[0]), origin.second + radius * (c * u[1] + s * v[1]), origin.third + radius * (c * u[2] + s * v[2]),
            ) ?: error("the rotate ring is behind the camera")
        }
        var best = 0.0
        var bestLength = -1.0
        for (i in 0 until 72) {
            val t = Math.toRadians(i * 5.0)
            val p = at(t)
            val q = at(t + Math.toRadians(2.0))
            val length = kotlin.math.hypot((q.x - p.x).toDouble(), (q.y - p.y).toDouble())
            if (length > bestLength) { bestLength = length; best = t }
        }
        val p = at(best)
        val q = at(best + Math.toRadians(2.0))
        val dx = (q.x - p.x).toDouble()
        val dy = (q.y - p.y).toDouble()
        val length = kotlin.math.hypot(dx, dy)
        return p to (dx / length to dy / length)
    }

    /** A point on the World Y rotate ring of the entity at [origin] (see [ring]). */
    fun yRing(origin: Triple<Float, Float, Float>) = ring(origin, Triple(0f, 1f, 0f))
}
