# Scene View Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "View" icon to scene rows of the Abyssus project view that opens an editor tab with a live, read-only libGDX render of the scene (grid, ambient light, fog, orbit camera).

**Architecture:** libGDX core runs on the existing lwjgl3-awt `AWTGLCanvas`. A small `GdxRuntime` installs `Gdx.app/graphics/gl/files` shims (reflection proxies) only while a frame renders, then restores the previous values. Pure classes (`SceneRenderParams`, `OrbitCamera`) hold all scene-to-render logic so it is unit-testable without GL.

**Tech Stack:** Kotlin 2.4, IntelliJ Platform 2025.2 (IC, since-build 252, Java 21), libGDX 1.13.5 (core + `gdx-backend-lwjgl3` GL classes only), lwjgl3-awt 0.2.5 / LWJGL 3.4.3, JUnit 4 / `BasePlatformTestCase`.

**Spec:** `docs/superpowers/specs/2026-10-02-scene-view-shell-design.md`

## Global Constraints

- Read-only view: nothing in this feature writes to scene/project files.
- Our editor is registered with `FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR`; double-click still opens the text editor, the View icon selects ours explicitly.
- Scene recognition is exact and case-sensitive on the extension: `.scene` only (`.SCENE`, `.scene.bak`, directories are not scenes).
- `Gdx.*` and `ShaderProgram.prepend*` are process-global: install only while rendering, restore previous values afterwards, serialize across tabs.
- Render only on the AWT thread; the loop stops when the tab is hidden (`removeNotify`) and on any GL exception (log a warning).
- GL failure shows the existing `glUnavailable` message; natives must cover macOS arm64, macOS, Windows, Linux.
- Out of scope: models, terrain, skybox, ECS entities, editing.
- The working tree has many unrelated staged/unstaged changes. **Every commit must be path-limited:** `git commit -m "..." -- <paths>` (new files must be `git add`ed first). Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Test command: `./gradlew test --tests '<fully.qualified.Class>'`.

## Review Focus

- Malformed or non-object scene JSON: tab shows a parse-error message, no exception (Task 5 test).
- Fog enabled with density `0`/`null`, or ambient/fog objects missing: no division by zero or infinite fog; sane defaults (Task 2 tests).
- Scene file outside a project (no `.abss` beside its `scenes` dir): default camera, still renders (Task 2 test).
- Panel resized to 0×0 / minimized: no NaN projection or GL error (Task 3 test of `aspectOf`).
- Exception inside a frame, or two scene tabs open: `Gdx.*` restored to prior values (Task 1 tests).

---

## File Structure

New package `net.nevinsky.abyssus.sceneview` (`src/main/kotlin/net/nevinsky/abyssus/sceneview/`):

| File | Responsibility |
|---|---|
| `GdxRuntime.kt` | Proxy stubs, `GdxFrame`, `GdxContext`, global install/restore under a lock |
| `SceneRenderParams.kt` | Pure: `Vec3`, `Rgba`, `CameraParams`, `SceneRenderParams.from(scene, camera)`, `MainCamera` reader |
| `OrbitCamera.kt` | Pure: orbit/pan/zoom math, `aspectOf` |
| `SceneRenderer.kt` | libGDX drawing (grid, environment, camera) |
| `SceneViewPanel.kt` | Swing panel: `AWTGLCanvas`, timer, mouse input |
| `SceneFileEditor.kt` | `FileEditor` + `FileEditorProvider`, reload on VFS change |
| `SceneViewActions.kt` | `openSceneView(project, file)` |

Modified: `build.gradle.kts`, `plugin.xml`, `AbyssusBundle.properties`, `filetype/AbyssusFileTypes.kt` (icon), `projectView/AbyssusNodes.kt` (`sceneFileOf`), `projectView/AbyssusProjectViewPane.kt` (`EyeTree` → `RowActionTree`). New resource `icons/view_scene.svg`.

---

### Task 1: libGDX dependencies and `GdxRuntime`

**Files:**
- Modify: `build.gradle.kts` (dependencies block)
- Create: `src/main/kotlin/net/nevinsky/abyssus/sceneview/GdxRuntime.kt`
- Test: `src/test/kotlin/net/nevinsky/abyssus/sceneview/GdxRuntimeTest.kt`

**Interfaces:**
- Produces:
  - `class GdxFrame { var width: Int; var height: Int; var deltaSeconds: Float; fun tick(width: Int, height: Int, nowNanos: Long = System.nanoTime()) }`
  - `class GdxContext(val app: Application, val graphics: Graphics, val gl20: GL20, val gl30: GL30?, val files: Files)`
  - `fun <T : Any> stubOf(type: Class<T>, handle: (Method, Array<out Any?>) -> Any? = { _, _ -> null }): T` (top-level, public)
  - `object GdxRuntime { fun newContext(frame: GdxFrame, gl20: GL20, gl30: GL30?, files: Files): GdxContext; fun <T> withContext(ctx: GdxContext, block: () -> T): T }`

- [ ] **Step 1: Add dependencies**

In `build.gradle.kts`, inside `dependencies { ... }` after the lwjgl block add:

```kotlin
    // libGDX core (g3d, math) hosted on the AWT GL canvas; only the backend's GL wrapper classes are used
    val gdxVersion = "1.13.5"
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion") { isTransitive = false }
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
```

- [ ] **Step 2: Write the failing test**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GdxRuntimeTest {
    private fun context(frame: GdxFrame = GdxFrame()) =
        GdxRuntime.newContext(frame, stubOf(GL20::class.java), null, stubOf(Files::class.java))

    @Test
    fun installsGlobalsOnlyInsideBlock() {
        val before = Gdx.graphics
        val frame = GdxFrame().apply { tick(640, 480) }
        val ctx = context(frame)
        GdxRuntime.withContext(ctx) {
            assertSame(ctx.graphics, Gdx.graphics)
            assertSame(ctx.gl20, Gdx.gl)
            assertEquals(640, Gdx.graphics.width)
            assertEquals(480, Gdx.graphics.height)
            assertNotNull(Gdx.app)
        }
        assertSame(before, Gdx.graphics)
    }

    @Test
    fun restoresGlobalsWhenBlockThrows() {
        val before = Gdx.gl20
        try {
            GdxRuntime.withContext(context()) { error("boom") }
        } catch (_: IllegalStateException) {
        }
        assertSame(before, Gdx.gl20)
    }

    @Test
    fun nestedContextsRestoreInOrder() {
        val outer = context()
        val inner = context()
        GdxRuntime.withContext(outer) {
            GdxRuntime.withContext(inner) { assertSame(inner.graphics, Gdx.graphics) }
            assertSame(outer.graphics, Gdx.graphics)
        }
        assertNull(Gdx.graphics)
    }

    @Test
    fun stubReturnsPrimitiveDefaultsAndIdentityHash() {
        val gl = stubOf(GL20::class.java)
        assertEquals(0, gl.glGetError())
        assertSame(gl, gl)
        assertEquals(System.identityHashCode(gl), gl.hashCode())
    }

    @Test
    fun frameTickComputesDelta() {
        val frame = GdxFrame()
        frame.tick(10, 10, 1_000_000_000L)
        assertEquals(0f, frame.deltaSeconds, 0f)
        frame.tick(10, 10, 1_500_000_000L)
        assertEquals(0.5f, frame.deltaSeconds, 1e-6f)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.GdxRuntimeTest'`
Expected: FAIL to compile (`GdxRuntime`, `stubOf`, `GdxFrame` unresolved). If instead dependency resolution fails, fix the version/coordinates first (this also validates that `gdx-backend-lwjgl3` and `natives-desktop` resolve).

- [ ] **Step 4: Write the implementation**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.Application
import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.GLVersion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.GdxNativesLoader
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Per-frame values reported by the `Gdx.graphics` shim. Touched only on the AWT thread. */
class GdxFrame {
    var width = 0
    var height = 0
    var deltaSeconds = 0f
    private var lastNanos = 0L

    fun tick(width: Int, height: Int, nowNanos: Long = System.nanoTime()) {
        deltaSeconds = if (lastNanos == 0L) 0f else (nowNanos - lastNanos) / 1e9f
        lastNanos = nowNanos
        this.width = width
        this.height = height
    }
}

class GdxContext(val app: Application, val graphics: Graphics, val gl20: GL20, val gl30: GL30?, val files: Files)

private fun defaultFor(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0f
    java.lang.Double.TYPE -> 0.0
    java.lang.Short.TYPE -> 0.toShort()
    java.lang.Byte.TYPE -> 0.toByte()
    else -> null
}

/**
 * A dynamic implementation of [type]: [handle] answers the methods that matter (returning `null` for
 * "not handled"); everything else returns the default for its return type (0, false, null).
 */
fun <T : Any> stubOf(type: Class<T>, handle: (Method, Array<out Any?>) -> Any? = { _, _ -> null }): T {
    val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
        val a = args ?: emptyArray()
        if (method.declaringClass == Any::class.java) {
            when (method.name) {
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === a[0]
                else -> "${type.simpleName} stub"
            }
        } else {
            handle(method, a) ?: defaultFor(method.returnType)
        }
    }
    return type.cast(proxy)
}

/**
 * Lets libGDX core run without a libGDX backend. `Gdx.*` is process-global inside the IDE, so it is
 * installed only for the duration of [withContext] and the previous values are restored afterwards.
 */
object GdxRuntime {
    private val lock = ReentrantLock()

    // Same prefixes Lwjgl3Application installs for GL3: libGDX shaders are GLSL 1.20 and the canvas is a core profile.
    private const val VERTEX_PREFIX = "#version 150\n#define attribute in\n#define varying out\n"
    private const val FRAGMENT_PREFIX = "#version 150\n#define varying in\n#define texture2D texture\n" +
        "#define textureCube texture\n#define gl_FragColor fragColor\nout vec4 fragColor;\n"

    init {
        GdxNativesLoader.load()
    }

    fun newContext(frame: GdxFrame, gl20: GL20, gl30: GL30?, files: Files): GdxContext {
        lateinit var graphics: Graphics
        val glVersion by lazy {
            GLVersion(
                Application.ApplicationType.Desktop,
                gl20.glGetString(GL20.GL_VERSION),
                gl20.glGetString(GL20.GL_VENDOR),
                gl20.glGetString(GL20.GL_RENDERER),
            )
        }
        val app = stubOf(Application::class.java) { m, _ ->
            when (m.name) {
                "getType" -> Application.ApplicationType.Desktop
                "getGraphics" -> graphics
                "getFiles" -> files
                else -> null
            }
        }
        graphics = stubOf(Graphics::class.java) { m, _ ->
            when (m.name) {
                "getWidth", "getBackBufferWidth" -> frame.width
                "getHeight", "getBackBufferHeight" -> frame.height
                "getDeltaTime" -> frame.deltaSeconds
                "getBackBufferScale", "getDensity" -> 1f
                "isGL30Available" -> gl30 != null
                "getGL20" -> gl20
                "getGL30" -> gl30
                "getGLVersion" -> glVersion
                else -> null
            }
        }
        return GdxContext(app, graphics, gl20, gl30, files)
    }

    fun <T> withContext(ctx: GdxContext, block: () -> T): T = lock.withLock {
        val app = Gdx.app
        val graphics = Gdx.graphics
        val gl = Gdx.gl
        val gl20 = Gdx.gl20
        val gl30 = Gdx.gl30
        val files = Gdx.files
        val vertexPrefix = ShaderProgram.prependVertexCode
        val fragmentPrefix = ShaderProgram.prependFragmentCode
        Gdx.app = ctx.app
        Gdx.graphics = ctx.graphics
        Gdx.gl20 = ctx.gl20
        Gdx.gl30 = ctx.gl30
        Gdx.gl = ctx.gl30 ?: ctx.gl20
        Gdx.files = ctx.files
        ShaderProgram.prependVertexCode = VERTEX_PREFIX
        ShaderProgram.prependFragmentCode = FRAGMENT_PREFIX
        try {
            block()
        } finally {
            Gdx.app = app
            Gdx.graphics = graphics
            Gdx.gl = gl
            Gdx.gl20 = gl20
            Gdx.gl30 = gl30
            Gdx.files = files
            ShaderProgram.prependVertexCode = vertexPrefix
            ShaderProgram.prependFragmentCode = fragmentPrefix
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.GdxRuntimeTest'`
Expected: PASS (5 tests). If `GdxNativesLoader.load()` fails, check `natives-desktop` is on the test runtime classpath.

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts src/main/kotlin/net/nevinsky/abyssus/sceneview/GdxRuntime.kt src/test/kotlin/net/nevinsky/abyssus/sceneview/GdxRuntimeTest.kt
git commit -m "Add libGDX dependencies and GdxRuntime shim

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -- build.gradle.kts src/main/kotlin/net/nevinsky/abyssus/sceneview/GdxRuntime.kt src/test/kotlin/net/nevinsky/abyssus/sceneview/GdxRuntimeTest.kt
```

---

### Task 2: `SceneRenderParams` and main-camera reading (pure)

**Files:**
- Create: `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderParams.kt`
- Test: `src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderParamsTest.kt`

**Interfaces:**
- Consumes: `SceneDto`, `BaseLightDto`, `FogDto`, `ColorDto` (package `net.nevinsky.abyssus.scene`), `SceneReader.parse(text)` (package `net.nevinsky.abyssus.dto`).
- Produces:
  - `data class Vec3(val x: Float, val y: Float, val z: Float)`
  - `data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float)`
  - `data class CameraParams(val position: Vec3, val direction: Vec3, val near: Float, val far: Float, val fieldOfView: Float)` with `companion val DEFAULT`
  - `data class FogParams(val color: Rgba, val near: Float, val far: Float, val exponent: Float)`
  - `data class SceneRenderParams(val clear: Rgba, val ambient: Rgba?, val fog: FogParams?, val camera: CameraParams)` with `companion { val DEFAULT_CLEAR; val DEFAULT: SceneRenderParams; fun from(scene: SceneDto, camera: CameraParams): SceneRenderParams }`
  - `object MainCamera { fun parse(abssText: String): CameraParams?; fun forScene(sceneFile: VirtualFile): CameraParams }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package net.nevinsky.abyssus.sceneview

import net.nevinsky.abyssus.dto.SceneReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SceneRenderParamsTest {
    private fun scene(json: String) = SceneReader.parse(json)

    private val full = """{"ambientLightEnabled":true,"ambientLight":{"color":{"r":1.0,"g":0.5,"b":0.0,"a":1.0},"intensity":0.5},
        "fogEnabled":true,"fog":{"color":{"r":0.2,"g":0.3,"b":0.4,"a":1.0},"density":0.01,"gradient":1.5}}"""

    @Test
    fun ambientIsColorTimesIntensity() {
        val p = SceneRenderParams.from(scene(full), CameraParams.DEFAULT)
        assertEquals(Rgba(0.5f, 0.25f, 0f, 1f), p.ambient)
    }

    @Test
    fun fogDrivesClearColorAndEquation() {
        val p = SceneRenderParams.from(scene(full), CameraParams.DEFAULT)
        assertEquals(Rgba(0.2f, 0.3f, 0.4f, 1f), p.clear)
        val fog = p.fog!!
        assertEquals(0f, fog.near, 0f)
        assertEquals(200f, fog.far, 1e-3f)
        assertEquals(1.5f, fog.exponent, 0f)
    }

    @Test
    fun disabledFeaturesAreOmitted() {
        val p = SceneRenderParams.from(scene("""{"ambientLightEnabled":false,"fogEnabled":false}"""), CameraParams.DEFAULT)
        assertNull(p.ambient)
        assertNull(p.fog)
        assertEquals(SceneRenderParams.DEFAULT_CLEAR, p.clear)
    }

    @Test
    fun missingObjectsDoNotThrow() {
        val p = SceneRenderParams.from(scene("""{"ambientLightEnabled":true,"fogEnabled":true}"""), CameraParams.DEFAULT)
        assertNull(p.ambient)
        assertNull(p.fog)
    }

    @Test
    fun zeroOrNullFogDensityMeansNoFog() {
        for (density in listOf("0", "0.0", "null", "-1")) {
            val json = """{"fogEnabled":true,"fog":{"color":{"r":1,"g":1,"b":1,"a":1},"density":$density}}"""
            val p = SceneRenderParams.from(scene(json), CameraParams.DEFAULT)
            assertNull("density $density", p.fog)
            assertEquals(SceneRenderParams.DEFAULT_CLEAR, p.clear)
        }
    }

    @Test
    fun invalidFogGradientFallsBackToOne() {
        val json = """{"fogEnabled":true,"fog":{"color":{"r":1,"g":1,"b":1,"a":1},"density":0.5,"gradient":0}}"""
        assertEquals(1f, SceneRenderParams.from(scene(json), CameraParams.DEFAULT).fog!!.exponent, 0f)
    }

    @Test
    fun missingIntensityDefaultsToOne() {
        val json = """{"ambientLightEnabled":true,"ambientLight":{"color":{"r":0.4,"g":0.4,"b":0.4,"a":1}}}"""
        assertEquals(Rgba(0.4f, 0.4f, 0.4f, 1f), SceneRenderParams.from(scene(json), CameraParams.DEFAULT).ambient)
    }

    @Test
    fun parsesMainCamera() {
        val abss = """{"mainCamera":{"viewPointPosition":{"x":-0.5,"y":0.0,"z":-0.5},"position":{"x":4.0,"y":3.0,"z":6.0},
            "far":100.0,"near":1.0,"fieldOfView":67.0},"name":"Untitled"}"""
        val cam = MainCamera.parse(abss)!!
        assertEquals(Vec3(4f, 3f, 6f), cam.position)
        assertEquals(100f, cam.far, 0f)
        assertEquals(67f, cam.fieldOfView, 0f)
        // direction is normalized
        val d = cam.direction
        assertEquals(1f, kotlin.math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z), 1e-5f)
    }

    @Test
    fun mainCameraParseIsNullForGarbageOrMissingCamera() {
        assertNull(MainCamera.parse("not json"))
        assertNull(MainCamera.parse("[]"))
        assertNull(MainCamera.parse("""{"name":"x"}"""))
        assertNull(MainCamera.parse("""{"mainCamera":{"position":{"x":1,"y":2,"z":3}}}"""))
    }

    @Test
    fun zeroLengthViewDirectionIsRejected() {
        val abss = """{"mainCamera":{"viewPointPosition":{"x":0,"y":0,"z":0},"position":{"x":1,"y":2,"z":3}}}"""
        assertNull(MainCamera.parse(abss))
    }

    @Test
    fun defaultCameraIsUsable() {
        assertNotNull(CameraParams.DEFAULT)
        assert(CameraParams.DEFAULT.far > CameraParams.DEFAULT.near)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.SceneRenderParamsTest'`
Expected: FAIL to compile (types unresolved).

- [ ] **Step 3: Implement**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.vfs.VirtualFile
import net.nevinsky.abyssus.scene.SceneDto
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float)

data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float)

/** [direction] is the unit vector the camera looks along. */
data class CameraParams(
    val position: Vec3,
    val direction: Vec3,
    val near: Float,
    val far: Float,
    val fieldOfView: Float,
) {
    companion object {
        val DEFAULT = CameraParams(Vec3(8f, 6f, 8f), normalized(Vec3(-8f, -6f, -8f))!!, 0.1f, 1000f, 67f)
    }
}

data class FogParams(val color: Rgba, val near: Float, val far: Float, val exponent: Float)

data class SceneRenderParams(val clear: Rgba, val ambient: Rgba?, val fog: FogParams?, val camera: CameraParams) {
    companion object {
        val DEFAULT_CLEAR = Rgba(0.1f, 0.1f, 0.15f, 1f)
        val DEFAULT = SceneRenderParams(DEFAULT_CLEAR, null, null, CameraParams.DEFAULT)

        fun from(scene: SceneDto, camera: CameraParams): SceneRenderParams {
            val fog = fogOf(scene)
            return SceneRenderParams(fog?.color ?: DEFAULT_CLEAR, ambientOf(scene), fog, camera)
        }

        private fun ambientOf(scene: SceneDto): Rgba? {
            if (scene.ambientLightEnabled != true) return null
            val light = scene.ambientLight ?: return null
            val c = light.color ?: return null
            val k = (light.intensity ?: 1f).coerceAtLeast(0f)
            return Rgba(c.r * k, c.g * k, c.b * k, 1f)
        }

        /** Mundus fog is `exp(-(d * density)^gradient)`; g3d gets a linear ramp reaching full fog at `2 / density`. */
        private fun fogOf(scene: SceneDto): FogParams? {
            if (scene.fogEnabled != true) return null
            val fog = scene.fog ?: return null
            val c = fog.color ?: return null
            val density = fog.density?.takeIf { it > 0f && it.isFinite() } ?: return null
            val gradient = fog.gradient?.takeIf { it > 0f && it.isFinite() } ?: 1f
            return FogParams(Rgba(c.r, c.g, c.b, 1f), 0f, 2f / density, gradient)
        }
    }
}

private fun normalized(v: Vec3): Vec3? {
    val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    return if (len > 1e-6f && len.isFinite()) Vec3(v.x / len, v.y / len, v.z / len) else null
}

/** The `mainCamera` of the `.abss` project a scene belongs to. */
object MainCamera {
    fun parse(abssText: String): CameraParams? = runCatching {
        val root = JsonParser.parseString(abssText).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val cam = root.get("mainCamera")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val position = cam.vec("position") ?: return null
        val direction = normalized(cam.vec("viewPointPosition") ?: return null) ?: return null
        val defaults = CameraParams.DEFAULT
        CameraParams(
            position,
            direction,
            cam.get("near")?.asFloat?.takeIf { it > 0f } ?: defaults.near,
            cam.get("far")?.asFloat?.takeIf { it > 0f } ?: defaults.far,
            cam.get("fieldOfView")?.asFloat?.takeIf { it > 0f && it < 180f } ?: defaults.fieldOfView,
        )
    }.getOrNull()

    /** `<project>/scenes/<scene>.scene` -> `<project>/*.abss`; the default camera when there is none or it is unreadable. */
    fun forScene(sceneFile: VirtualFile): CameraParams {
        val abss = sceneFile.parent?.parent?.children?.firstOrNull { it.extension == "abss" } ?: return CameraParams.DEFAULT
        return runCatching { parse(String(abss.contentsToByteArray(), abss.charset)) }.getOrNull() ?: CameraParams.DEFAULT
    }

    private fun JsonObject.vec(name: String): Vec3? {
        val o = get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        fun f(k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asFloat ?: 0f
        return Vec3(f("x"), f("y"), f("z"))
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.SceneRenderParamsTest'`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderParams.kt src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderParamsTest.kt
git commit -m "Add scene render params and main camera reader

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -- src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderParams.kt src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderParamsTest.kt
```

---

### Task 3: `OrbitCamera` (pure)

**Files:**
- Create: `src/main/kotlin/net/nevinsky/abyssus/sceneview/OrbitCamera.kt`
- Test: `src/test/kotlin/net/nevinsky/abyssus/sceneview/OrbitCameraTest.kt`

**Interfaces:**
- Consumes: `Vec3`, `CameraParams` (Task 2).
- Produces:
  - `class OrbitCamera(var target: Vec3, var distance: Float, var yaw: Float, var pitch: Float)` with `fun position(): Vec3`, `fun orbit(dxPixels: Float, dyPixels: Float)`, `fun pan(dxPixels: Float, dyPixels: Float)`, `fun zoom(wheelClicks: Float)`, `companion fun from(camera: CameraParams): OrbitCamera`
  - `fun aspectOf(width: Int, height: Int): Float?` (null when either side is not positive)

- [ ] **Step 1: Write the failing tests**

```kotlin
package net.nevinsky.abyssus.sceneview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitCameraTest {
    private fun assertVec(expected: Vec3, actual: Vec3, eps: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
        assertEquals(expected.z, actual.z, eps)
    }

    @Test
    fun fromReproducesCameraPosition() {
        val params = CameraParams(Vec3(4.8f, 3.3f, 6.0f), Vec3(-0.9485f, -0.1088f, -0.2975f), 1f, 100f, 67f)
        assertVec(params.position, OrbitCamera.from(params).position(), 0.05f)
    }

    @Test
    fun fromDefaultCameraLooksAtTarget() {
        val cam = OrbitCamera.from(CameraParams.DEFAULT)
        val p = cam.position()
        val d = CameraParams.DEFAULT.direction
        // target = position + direction * distance
        assertVec(Vec3(p.x + d.x * cam.distance, p.y + d.y * cam.distance, p.z + d.z * cam.distance), cam.target)
    }

    @Test
    fun pitchIsClamped() {
        val cam = OrbitCamera(Vec3(0f, 0f, 0f), 10f, 0f, 0f)
        cam.orbit(0f, 1_000_000f)
        assertTrue(cam.pitch < Math.PI.toFloat() / 2)
        cam.orbit(0f, -2_000_000f)
        assertTrue(cam.pitch > -Math.PI.toFloat() / 2)
    }

    @Test
    fun orbitKeepsDistanceToTarget() {
        val cam = OrbitCamera(Vec3(1f, 2f, 3f), 10f, 0.3f, 0.4f)
        cam.orbit(120f, -40f)
        val p = cam.position()
        val d = kotlin.math.sqrt((p.x - 1f) * (p.x - 1f) + (p.y - 2f) * (p.y - 2f) + (p.z - 3f) * (p.z - 3f))
        assertEquals(10f, d, 1e-3f)
    }

    @Test
    fun zoomStaysWithinLimits() {
        val cam = OrbitCamera(Vec3(0f, 0f, 0f), 10f, 0f, 0f)
        cam.zoom(10_000f)
        assertTrue(cam.distance > 0f)
        cam.zoom(-10_000f)
        assertTrue(cam.distance.isFinite() && cam.distance <= 5000f)
    }

    @Test
    fun panMovesTargetAndKeepsDistance() {
        val cam = OrbitCamera(Vec3(0f, 0f, 0f), 10f, 0f, 0f)
        val before = cam.target
        cam.pan(50f, 0f)
        assertTrue(cam.target != before)
        assertEquals(10f, cam.distance, 0f)
    }

    @Test
    fun aspectIsNullForNonPositiveSizes() {
        assertNull(aspectOf(0, 0))
        assertNull(aspectOf(100, 0))
        assertNull(aspectOf(0, 100))
        assertNull(aspectOf(-1, 5))
        assertEquals(2f, aspectOf(200, 100)!!, 0f)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.OrbitCameraTest'`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

```kotlin
package net.nevinsky.abyssus.sceneview

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

fun aspectOf(width: Int, height: Int): Float? = if (width > 0 && height > 0) width.toFloat() / height else null

/**
 * Orbit camera around [target]. The eye sits at `target + (cos p sin y, sin p, cos p cos y) * distance`,
 * so yaw 0 / pitch 0 looks down -Z.
 */
class OrbitCamera(var target: Vec3, var distance: Float, var yaw: Float, var pitch: Float) {

    fun position(): Vec3 {
        val cp = cos(pitch)
        return Vec3(
            target.x + cp * sin(yaw) * distance,
            target.y + sin(pitch) * distance,
            target.z + cp * cos(yaw) * distance,
        )
    }

    fun orbit(dxPixels: Float, dyPixels: Float) {
        yaw -= dxPixels * ORBIT_SPEED
        pitch = (pitch + dyPixels * ORBIT_SPEED).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    fun pan(dxPixels: Float, dyPixels: Float) {
        val k = distance * PAN_SPEED
        val right = Vec3(cos(yaw), 0f, -sin(yaw))
        val up = Vec3(-sin(pitch) * sin(yaw), cos(pitch), -sin(pitch) * cos(yaw))
        target = Vec3(
            target.x - right.x * dxPixels * k + up.x * dyPixels * k,
            target.y - right.y * dxPixels * k + up.y * dyPixels * k,
            target.z - right.z * dxPixels * k + up.z * dyPixels * k,
        )
    }

    fun zoom(wheelClicks: Float) {
        distance = (distance * ZOOM_BASE.pow(wheelClicks)).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    companion object {
        private const val ORBIT_SPEED = 0.005f
        private const val PAN_SPEED = 0.0015f
        private const val ZOOM_BASE = 0.9f
        private const val MIN_DISTANCE = 0.1f
        private const val MAX_DISTANCE = 5000f
        private const val MAX_PITCH = 1.5533f // 89 degrees
        private const val DEFAULT_DISTANCE = 10f

        fun from(camera: CameraParams): OrbitCamera {
            val d = camera.direction
            val target = Vec3(
                camera.position.x + d.x * DEFAULT_DISTANCE,
                camera.position.y + d.y * DEFAULT_DISTANCE,
                camera.position.z + d.z * DEFAULT_DISTANCE,
            )
            return OrbitCamera(
                target,
                DEFAULT_DISTANCE,
                atan2(-d.x, -d.z),
                asin((-d.y).coerceIn(-1f, 1f)).coerceIn(-MAX_PITCH, MAX_PITCH),
            )
        }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.OrbitCameraTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/nevinsky/abyssus/sceneview/OrbitCamera.kt src/test/kotlin/net/nevinsky/abyssus/sceneview/OrbitCameraTest.kt
git commit -m "Add orbit camera math

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -- src/main/kotlin/net/nevinsky/abyssus/sceneview/OrbitCamera.kt src/test/kotlin/net/nevinsky/abyssus/sceneview/OrbitCameraTest.kt
```

---

### Task 4: `SceneRenderer` and `SceneViewPanel` (GL; verified by compile here, visually in Task 5)

**Files:**
- Create: `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderer.kt`
- Create: `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneViewPanel.kt`

**Interfaces:**
- Consumes: `SceneRenderParams`, `Rgba`, `OrbitCamera`, `aspectOf` (Tasks 2–3); `GdxRuntime`, `GdxFrame`, `GdxContext` (Task 1).
- Produces:
  - `class SceneRenderer : Disposable { @Volatile var params: SceneRenderParams; fun create(); fun render(width: Int, height: Int, orbit: OrbitCamera); override fun dispose() }` — all methods must be called inside `GdxRuntime.withContext` with the GL context current.
  - `class SceneViewPanel(initial: SceneRenderParams) : JPanel, Disposable { fun setParams(p: SceneRenderParams) }`

There is no GL unit test (no GL in CI); correctness is checked by compilation here and by the manual run in Task 5.

- [ ] **Step 1: Write `SceneRenderer.kt`**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FogEquationAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.intellij.openapi.Disposable

/** Draws a scene's environment and a ground grid. Call only inside [GdxRuntime.withContext] with the GL context current. */
class SceneRenderer : Disposable {
    @Volatile
    var params: SceneRenderParams = SceneRenderParams.DEFAULT

    private var batch: ModelBatch? = null
    private var gridModel: Model? = null
    private var grid: ModelInstance? = null
    private val camera = PerspectiveCamera()
    private val environment = Environment()

    fun create() {
        batch = ModelBatch()
        gridModel = buildGrid().also { grid = ModelInstance(it) }
    }

    fun render(width: Int, height: Int, orbit: OrbitCamera) {
        val batch = batch ?: return
        val grid = grid ?: return
        val aspect = aspectOf(width, height) ?: return
        val p = params

        applyEnvironment(p)
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(p.clear.r, p.clear.g, p.clear.b, p.clear.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        val eye = orbit.position()
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.fieldOfView = p.camera.fieldOfView
        camera.near = p.camera.near
        camera.far = p.camera.far
        camera.position.set(eye.x, eye.y, eye.z)
        camera.up.set(Vector3.Y)
        camera.lookAt(orbit.target.x, orbit.target.y, orbit.target.z)
        camera.update()

        batch.begin(camera)
        batch.render(grid, environment)
        batch.end()
    }

    private fun applyEnvironment(p: SceneRenderParams) {
        val ambient = p.ambient
        if (ambient != null) {
            environment.set(ColorAttribute(ColorAttribute.AmbientLight, ambient.r, ambient.g, ambient.b, 1f))
        } else {
            environment.remove(ColorAttribute.AmbientLight)
        }
        val fog = p.fog
        if (fog != null) {
            environment.set(ColorAttribute(ColorAttribute.Fog, fog.color.r, fog.color.g, fog.color.b, 1f))
            environment.set(FogEquationAttribute(fog.near, fog.far, fog.exponent))
        } else {
            environment.remove(ColorAttribute.Fog)
            environment.remove(FogEquationAttribute.FogEquation)
        }
    }

    /** Lines need normals for the default shader to apply ambient light; emissive keeps the grid visible without it. */
    private fun buildGrid(): Model {
        val builder = ModelBuilder()
        builder.begin()
        val material = Material(ColorAttribute.createDiffuse(Color.WHITE), ColorAttribute.createEmissive(0.25f, 0.25f, 0.25f, 1f))
        val part = builder.part("grid", GL20.GL_LINES, (Usage.Position or Usage.Normal).toLong(), material)
        val info = VertexInfo()
        val n = GRID_HALF_EXTENT
        for (i in -n..n) {
            val a = part.vertex(info.set(Vector3(i.toFloat(), 0f, -n.toFloat()), Vector3.Y, null, null))
            val b = part.vertex(info.set(Vector3(i.toFloat(), 0f, n.toFloat()), Vector3.Y, null, null))
            part.line(a, b)
            val c = part.vertex(info.set(Vector3(-n.toFloat(), 0f, i.toFloat()), Vector3.Y, null, null))
            val d = part.vertex(info.set(Vector3(n.toFloat(), 0f, i.toFloat()), Vector3.Y, null, null))
            part.line(c, d)
        }
        return builder.end()
    }

    override fun dispose() {
        batch?.dispose()
        gridModel?.dispose()
        batch = null
        gridModel = null
        grid = null
    }

    private companion object {
        const val GRID_HALF_EXTENT = 50
    }
}
```

- [ ] **Step 2: Write `SceneViewPanel.kt`**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3GL20
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3GL30
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/** Swing panel hosting a core-profile GL canvas that renders a scene with libGDX. Read-only; orbit/pan/zoom with the mouse. */
class SceneViewPanel(initial: SceneRenderParams) : JPanel(BorderLayout()), Disposable {

    private val frame = GdxFrame()
    private val renderer = SceneRenderer().also { it.params = initial }
    private val orbit = OrbitCamera.from(initial.camera)
    private var context: GdxContext? = null

    private val canvas = object : AWTGLCanvas(glData()) {
        override fun initGL() {
            GL.createCapabilities()
            val ctx = GdxRuntime.newContext(frame, Lwjgl3GL20(), Lwjgl3GL30(), Lwjgl3Files())
            context = ctx
            GdxRuntime.withContext(ctx) { renderer.create() }
        }

        override fun paintGL() {
            val ctx = context ?: return
            frame.tick(framebufferWidth, framebufferHeight)
            GdxRuntime.withContext(ctx) { renderer.render(frame.width, frame.height, orbit) }
            swapBuffers()
        }
    }

    private val timer = Timer(FRAME_MILLIS) {
        try {
            canvas.render()
        } catch (e: Throwable) {
            thisLogger().warn("Scene render failed, stopping the view", e)
            timer().stop()
        }
    }

    private fun timer() = timer

    init {
        add(canvas, BorderLayout.CENTER)
        val input = object : MouseAdapter() {
            private var last: Point? = null

            override fun mousePressed(e: MouseEvent) {
                last = e.point
            }

            override fun mouseDragged(e: MouseEvent) {
                val from = last ?: return
                val dx = (e.x - from.x).toFloat()
                val dy = (e.y - from.y).toFloat()
                if (SwingUtilities.isLeftMouseButton(e)) orbit.orbit(dx, dy) else orbit.pan(dx, dy)
                last = e.point
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                orbit.zoom(e.preciseWheelRotation.toFloat())
            }
        }
        canvas.addMouseListener(input)
        canvas.addMouseMotionListener(input)
        canvas.addMouseWheelListener(input)
    }

    fun setParams(params: SceneRenderParams) {
        renderer.params = params
    }

    override fun addNotify() {
        super.addNotify()
        timer.start()
    }

    override fun removeNotify() {
        timer.stop()
        super.removeNotify()
    }

    override fun dispose() {
        timer.stop()
        val ctx = context
        if (ctx != null) {
            canvas.runInContext { GdxRuntime.withContext(ctx) { renderer.dispose() } }
        }
        canvas.disposeCanvas()
    }

    private companion object {
        const val FRAME_MILLIS = 16

        fun glData() = GLData().apply {
            majorVersion = 3
            minorVersion = 2
            profile = GLData.Profile.CORE
            forwardCompatible = true
            depthSize = 24
        }
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL. Likely fix-ups (only if the compiler says so): (a) if `Lwjgl3GL20/GL30/Files` are not accessible from the backend jar, copy the minimal needed class from the libGDX sources into `sceneview/gl/` and adjust imports; (b) if `FogEquationAttribute` has a different package/constant name in 1.13.5, use the class the compiler's import suggestions list (it lives in `com.badlogic.gdx.graphics.g3d.attributes`); (c) `timer()` indirection exists only because the lambda references `timer` during its own initialization — keep it.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderer.kt src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneViewPanel.kt
git commit -m "Add libGDX scene renderer and GL view panel

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -- src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderer.kt src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneViewPanel.kt
```

---

### Task 5: Scene editor, provider, registration, and first visual check

**Files:**
- Create: `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneFileEditor.kt`
- Create: `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneViewActions.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Modify: `src/main/resources/messages/AbyssusBundle.properties`
- Test: `src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneFileEditorTest.kt`

**Interfaces:**
- Consumes: `SceneViewPanel`, `SceneRenderParams`, `MainCamera` (Tasks 2, 4); `SceneReader.readScene(file): Result<SceneDto>` (existing, package `net.nevinsky.abyssus.dto`); `AbyssusBundle.message`.
- Produces:
  - `class SceneFileEditorProvider : FileEditorProvider, DumbAware` with `companion const val EDITOR_TYPE_ID = "abyssus-scene-view"`
  - `class SceneFileEditor(project: Project, file: VirtualFile) : UserDataHolderBase(), FileEditor` with `internal val statusText: String?` (non-null when the tab shows a message instead of a render)
  - `fun openSceneView(project: Project, file: VirtualFile)` in `SceneViewActions.kt`

- [ ] **Step 1: Add messages**

Append to `AbyssusBundle.properties`:

```properties
sceneViewParseError=Cannot read scene: {0}
sceneViewEditorName=Scene View
viewSceneTooltip=View scene
```

- [ ] **Step 2: Write the failing test**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SceneFileEditorTest : BasePlatformTestCase() {
    private val provider = SceneFileEditorProvider()

    private fun file(path: String, text: String = "{}") = myFixture.addFileToProject(path, text).virtualFile

    fun testAcceptsOnlyExactSceneExtension() {
        assertTrue(provider.accept(project, file("a/Main.scene")))
        assertFalse(provider.accept(project, file("a/Main.SCENE")))
        assertFalse(provider.accept(project, file("a/Main.scene.bak")))
        assertFalse(provider.accept(project, file("a/Main.abss")))
        assertFalse(provider.accept(project, myFixture.tempDirFixture.findOrCreateDir("dir.scene")))
    }

    fun testIsPlacedAfterTextEditor() {
        assertEquals(com.intellij.openapi.fileEditor.FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR, provider.policy)
    }

    fun testMalformedSceneShowsParseErrorInsteadOfThrowing() {
        for (text in listOf("not json", "[]", "")) {
            val editor = provider.createEditor(project, file("bad/${text.length}.scene", text)) as SceneFileEditor
            try {
                assertNotNull(editor.component)
                val status = editor.statusText
                assertNotNull("status for '$text'", status)
                assertTrue(status!!, status.startsWith("Cannot read scene"))
            } finally {
                editor.dispose()
            }
        }
    }

    fun testValidSceneEditorCreatesAndDisposesWithoutThrowing() {
        // In a headless/GL-less environment the tab shows the glUnavailable message; either way no exception.
        val editor = provider.createEditor(project, file("ok/Main.scene", """{"name":"x"}""")) as SceneFileEditor
        assertNotNull(editor.component)
        editor.dispose()
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.SceneFileEditorTest'`
Expected: FAIL to compile.

- [ ] **Step 4: Implement `SceneFileEditor.kt`**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.components.JBLabel
import net.nevinsky.abyssus.AbyssusBundle
import net.nevinsky.abyssus.dto.SceneReader
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class SceneFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = !file.isDirectory && file.extension == "scene"

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = SceneFileEditor(project, file)

    override fun getEditorTypeId() = EDITOR_TYPE_ID

    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "abyssus-scene-view"
    }
}

/** Read-only live view of a `.scene`; re-reads the scene when the file changes. */
class SceneFileEditor(project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val content = JPanel(BorderLayout())
    private var panel: SceneViewPanel? = null

    internal var statusText: String? = null
        private set

    init {
        reload()
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it is VFileContentChangeEvent && it.file == file }) {
                    ApplicationManager.getApplication().invokeLater { if (!Disposer.isDisposed(this@SceneFileEditor)) reload() }
                }
            }
        })
    }

    private fun reload() {
        val params = SceneReader.readScene(file).map { SceneRenderParams.from(it, MainCamera.forScene(file)) }
        params.onSuccess { showScene(it) }.onFailure { showStatus(AbyssusBundle.message("sceneViewParseError", it.message ?: it.javaClass.simpleName)) }
    }

    private fun showScene(params: SceneRenderParams) {
        panel?.let { it.setParams(params); return }
        val created = try {
            SceneViewPanel(params)
        } catch (e: Throwable) {
            thisLogger().warn("Failed to create OpenGL scene view", e)
            showStatus(AbyssusBundle.message("glUnavailable", e.message ?: e.javaClass.simpleName))
            return
        }
        panel = created
        statusText = null
        setContent(created)
    }

    private fun showStatus(text: String) {
        panel?.let { Disposer.dispose(it) }
        panel = null
        statusText = text
        setContent(JBLabel(text, SwingConstants.CENTER))
    }

    private fun setContent(component: JComponent) {
        content.removeAll()
        content.add(component, BorderLayout.CENTER)
        content.revalidate()
        content.repaint()
    }

    override fun getComponent(): JComponent = content
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName() = AbyssusBundle.message("sceneViewEditorName")
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) {}
    override fun isModified() = false
    override fun isValid() = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        panel?.let { Disposer.dispose(it) }
        panel = null
    }
}
```

Note: `Disposer.dispose(panel)` works because `SceneViewPanel` is a `Disposable`; it is not registered with a parent, so `SceneFileEditor.dispose` disposes it explicitly.

- [ ] **Step 5: Implement `SceneViewActions.kt`**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Opens [file] and selects the Abyssus scene view tab (the text editor stays available as the other tab). */
fun openSceneView(project: Project, file: VirtualFile) {
    val manager = FileEditorManager.getInstance(project)
    manager.openFile(file, true)
    manager.setSelectedEditor(file, SceneFileEditorProvider.EDITOR_TYPE_ID)
}
```

If `FileEditorManager.setSelectedEditor` does not compile on 2025.2.4, use `(manager as com.intellij.openapi.fileEditor.ex.FileEditorManagerEx).setSelectedEditor(file, SceneFileEditorProvider.EDITOR_TYPE_ID)`.

- [ ] **Step 6: Register in `plugin.xml`**

Inside `<extensions defaultExtensionNs="com.intellij">`, after the `projectViewPane` line:

```xml
        <fileEditorProvider implementation="net.nevinsky.abyssus.sceneview.SceneFileEditorProvider"/>
```

- [ ] **Step 7: Run tests**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.SceneFileEditorTest'`
Expected: PASS (4 tests).

- [ ] **Step 8: First visual check (temporary hook, not committed)**

Run: `./gradlew runIde -PideProject=/Users/inv3r/Development/gamedev/abyssus/src/test/testData/project/Untitled`
In the IDE: open `scenes/Main Scene.scene`, switch to the "Scene View" tab at the bottom of the editor.
Expected: a white-fog-colored background with a grid, animating without errors; left-drag orbits, right-drag pans, wheel zooms. Check `idea.log` for "Scene render failed". If a libGDX NPE appears from an unstubbed `Gdx.graphics`/`Gdx.app` method, add that method to the matching `handle` in `GdxRuntime.newContext` and re-run `GdxRuntimeTest`. If shaders fail to compile, adjust `VERTEX_PREFIX`/`FRAGMENT_PREFIX` in `GdxRuntime`.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneFileEditor.kt src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneViewActions.kt src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneFileEditorTest.kt
git commit -m "Add scene view editor and provider

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -- src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneFileEditor.kt src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneViewActions.kt src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneFileEditorTest.kt src/main/resources/META-INF/plugin.xml src/main/resources/messages/AbyssusBundle.properties
```

(`plugin.xml` and `AbyssusBundle.properties` already carry unrelated uncommitted edits; if you want to keep those out of this commit, use `git add -p` on them and commit with the staged hunks only.)

---

### Task 6: "View" icon on scene rows

**Files:**
- Create: `src/main/resources/icons/view_scene.svg`
- Modify: `src/main/kotlin/net/nevinsky/abyssus/filetype/AbyssusFileTypes.kt` (add `SceneViewIcons`)
- Modify: `src/main/kotlin/net/nevinsky/abyssus/projectView/AbyssusNodes.kt` (add `sceneFileOf`)
- Modify: `src/main/kotlin/net/nevinsky/abyssus/projectView/AbyssusProjectViewPane.kt` (`EyeTree` → `RowActionTree`)
- Test: `src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneRowActionTest.kt`

**Interfaces:**
- Consumes: `openSceneView` (Task 5); `AbyssusAssetNode`, `DtoEntryNode`, `DtoEntry`, `DtoValue.Obj.source`, existing `toggleEnabled(project, entry)`.
- Produces: `fun sceneFileOf(node: Any?): VirtualFile?` (top-level in `AbyssusNodes.kt`); `object SceneViewIcons { val VIEW: Icon }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package net.nevinsky.abyssus.sceneview

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.nevinsky.abyssus.projectView.AbyssusAssetNode
import net.nevinsky.abyssus.projectView.AbyssusRootNode
import net.nevinsky.abyssus.projectView.sceneFileOf

class SceneRowActionTest : BasePlatformTestCase() {
    override fun getTestDataPath() = "src/test/testData/project"

    private fun text(node: AbstractTreeNode<*>): String {
        node.update()
        val p: PresentationData = node.presentation
        return p.presentableText ?: p.coloredText.joinToString("") { it.text }
    }

    private fun children(node: AbstractTreeNode<*>) = node.children.map { it as AbstractTreeNode<*> }

    private fun projectFixture(): VirtualFile {
        myFixture.copyFileToProject("Untitled/Untitled.abss", "Untitled/Untitled.abss")
        myFixture.copyFileToProject("Untitled/scenes/Main Scene.scene", "Untitled/scenes/Main Scene.scene")
        return myFixture.findFileInTempDir("Untitled")
    }

    fun testStandaloneSceneNodeHasViewTarget() {
        val file = myFixture.addFileToProject("Loose/a.scene", """{"name":"a"}""").virtualFile
        assertEquals(file, sceneFileOf(AbyssusAssetNode(project, file, ViewSettings.DEFAULT)))
    }

    fun testProjectNodeAndScalarEntriesHaveNoViewTarget() {
        projectFixture()
        val projectNode = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { text(it).startsWith("Untitled") }
        assertNull(sceneFileOf(projectNode))
        val scenes = children(projectNode).single { text(it).startsWith("scenes") }
        assertNull(sceneFileOf(scenes))
        assertNull(sceneFileOf(children(projectNode).single { text(it).startsWith("name") }))
    }

    fun testSceneEntryInsideProjectHasViewTarget() {
        val dir = projectFixture()
        val projectNode = children(AbyssusRootNode(project, ViewSettings.DEFAULT)).single { text(it).startsWith("Untitled") }
        val scenes = children(projectNode).single { text(it).startsWith("scenes") }
        val sceneNode = children(scenes).single()
        assertEquals(dir.findFileByRelativePath("scenes/Main Scene.scene"), sceneFileOf(sceneNode))
    }

    fun testSceneInternalsAndNonNodesHaveNoViewTarget() {
        val file = myFixture.addFileToProject("Loose/b.scene", """{"name":"b","fogEnabled":true,"fog":{"density":0.1}}""").virtualFile
        val sceneNode = AbyssusAssetNode(project, file, ViewSettings.DEFAULT)
        for (child in children(sceneNode)) assertNull(text(child), sceneFileOf(child))
        assertNull(sceneFileOf(null))
        assertNull(sceneFileOf("x"))
    }

    fun testUppercaseExtensionIsNotAScene() {
        val file = myFixture.addFileToProject("Loose/c.SCENE", "{}").virtualFile
        assertNull(sceneFileOf(AbyssusAssetNode(project, file, ViewSettings.DEFAULT)))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.SceneRowActionTest'`
Expected: FAIL to compile (`sceneFileOf` unresolved).

- [ ] **Step 3: Add `sceneFileOf` to `AbyssusNodes.kt`**

Append after `isProjectScene`:

```kotlin
/** The `.scene` file a project-view node stands for (a standalone scene or a project's scene entry), else null. */
fun sceneFileOf(node: Any?): VirtualFile? = when (node) {
    is AbyssusAssetNode -> node.virtualFile.takeIf { it.extension == "scene" }
    is DtoEntryNode -> (node.value.value as? DtoValue.Obj)?.source?.takeIf { it.extension == "scene" }
    else -> null
}
```

(`AbyssusAssetNode`, `DtoEntryNode` and `DtoValue` are in the same file/imports already.)

- [ ] **Step 4: Add the icon**

`src/main/resources/icons/view_scene.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16"><path d="M4 2.5v11l9-5.5z" fill="#4f8ff7"/></svg>
```

In `AbyssusFileTypes.kt` add after `AbyssusProjectIcons`:

```kotlin
object SceneViewIcons {
    @JvmField
    val VIEW: Icon = IconLoader.getIcon("/icons/view_scene.svg", SceneViewIcons::class.java)
}
```

- [ ] **Step 5: Replace `EyeTree` with `RowActionTree` in `AbyssusProjectViewPane.kt`**

Change `createTree` to `= RowActionTree(treeModel, myProject)`, add imports `net.nevinsky.abyssus.filetype.SceneViewIcons`, `net.nevinsky.abyssus.sceneview.openSceneView`, `com.intellij.openapi.vfs.VirtualFile`, `java.awt.event.MouseEvent` (already), `javax.swing.ToolTipManager`, and replace the whole `EyeTree` class (from its KDoc to the end of file) with:

```kotlin
private class RowAction(val icon: Icon, val tooltip: String?, val run: () -> Unit)

/**
 * Paints one clickable icon at the right edge of rows that have an action: the eye on entries gated by an
 * `xxxEnabled` toggle, and "View" on scenes.
 */
private class RowActionTree(model: DefaultTreeModel, private val project: Project) : ProjectViewTree(model) {
    private fun actionFor(row: Int): RowAction? {
        val node = TreeUtil.getUserObject(getPathForRow(row)?.lastPathComponent)
        sceneFileOf(node)?.let { file ->
            return RowAction(SceneViewIcons.VIEW, AbyssusBundle.message("viewSceneTooltip")) { openSceneView(project, file) }
        }
        val entry = (node as? DtoEntryNode)?.value?.takeIf { it.enabled != null } ?: return null
        val icon = if (entry.enabled == true) AllIcons.Actions.Show else AllIcons.Actions.ToggleVisibility
        return RowAction(icon, null) { toggleEnabled(project, entry) }
    }

    private fun iconBounds(row: Int, icon: Icon): Rectangle {
        val bounds = getRowBounds(row)
        val x = visibleRect.let { it.x + it.width } - icon.iconWidth - ICON_GAP
        return Rectangle(x, bounds.y + (bounds.height - icon.iconHeight) / 2, icon.iconWidth, icon.iconHeight)
    }

    private fun actionAt(e: MouseEvent): RowAction? {
        val row = getClosestRowForLocation(e.x, e.y)
        if (row < 0 || getRowBounds(row)?.let { e.y in it.y until it.y + it.height } != true) return null
        val action = actionFor(row) ?: return null
        return action.takeIf { iconBounds(row, it.icon).contains(e.point) }
    }

    init {
        ToolTipManager.sharedInstance().registerComponent(this)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return
                actionAt(e)?.let { it.run(); e.consume() }
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                cursor = if (actionAt(e) != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            }
        })
    }

    override fun getToolTipText(event: MouseEvent): String? = actionAt(event)?.tooltip ?: super.getToolTipText(event)

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val clip = g.clipBounds ?: return
        val first = getClosestRowForLocation(0, clip.y)
        val last = getClosestRowForLocation(0, clip.y + clip.height)
        for (row in first..last) {
            val action = actionFor(row) ?: continue
            val r = iconBounds(row, action.icon)
            action.icon.paintIcon(this, g, r.x, r.y)
        }
    }

    private companion object {
        const val ICON_GAP = 8
    }
}
```

Remove the now-unused `VirtualFile` import if you added it speculatively.

- [ ] **Step 6: Run the new and existing view tests**

Run: `./gradlew test --tests 'net.nevinsky.abyssus.sceneview.SceneRowActionTest' --tests 'net.nevinsky.abyssus.AbyssusViewTest'`
Expected: PASS (new 5 + all existing; the eye-toggle behavior is unchanged).

- [ ] **Step 7: Manual check**

Run: `./gradlew runIde -PideProject=/Users/inv3r/Development/gamedev/abyssus/src/test/testData/project/Untitled`
Switch the project view to "Abyssus". Expected: a blue play-triangle at the right edge of the `Main Scene` row (and of any standalone `.scene`), none on `.abss`/property rows; eye icons on toggle rows still work; hover shows the "View scene" tooltip and a hand cursor; clicking opens `Main Scene.scene` with the "Scene View" tab selected and the live grid rendering; clicking View again re-selects the existing tab.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/icons/view_scene.svg src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneRowActionTest.kt
git commit -m "Add View icon to scene rows in the Abyssus project view

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>" -- src/main/resources/icons/view_scene.svg src/test/kotlin/net/nevinsky/abyssus/sceneview/SceneRowActionTest.kt src/main/kotlin/net/nevinsky/abyssus/filetype/AbyssusFileTypes.kt src/main/kotlin/net/nevinsky/abyssus/projectView/AbyssusNodes.kt src/main/kotlin/net/nevinsky/abyssus/projectView/AbyssusProjectViewPane.kt
```

(`AbyssusNodes.kt` and `AbyssusProjectViewPane.kt` contain earlier uncommitted work from the working tree; a path-limited commit will include it. If that is not wanted, stage hunks with `git add -p` and commit without the path list.)

---

### Task 7: Full verification

- [ ] **Step 1: Whole test suite**

Run: `./gradlew test`
Expected: all tests pass (existing `AbyssusViewTest`, `GltfPsiTest` plus the new ones).

- [ ] **Step 2: Plugin verification build**

Run: `./gradlew buildPlugin verifyPluginProjectConfiguration`
Expected: BUILD SUCCESSFUL. Confirm the built zip under `build/distributions/` contains the libGDX jars and the `natives-desktop` jar.

- [ ] **Step 3: Manual acceptance against the spec**

With `runIde` on the `Untitled` project, confirm each success criterion: View opens a tab that animates; grid visible; ambient/fog applied (try editing `fogEnabled`/`density` in the `.scene` text — the view updates on save without reopening); orbit/pan/zoom work; collapse the editor or switch tabs and confirm CPU drops (timer stopped); break the JSON and confirm the "Cannot read scene" message, then fix it and confirm the render resumes; open two scenes (or the same scene in a split) and confirm both render.

- [ ] **Step 4: Report**

Summarize results, including anything not verified on Windows/Linux (only macOS was run).
