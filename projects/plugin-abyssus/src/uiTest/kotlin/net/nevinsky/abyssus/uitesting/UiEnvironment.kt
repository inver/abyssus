package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

/** Settings the `uiTest` Gradle task and `scripts/run-ui-tests.sh` pass in as system properties. */
class UiEnvironment(
    val robotUrl: String = System.getProperty("abyssus.ui.robotUrl", "http://127.0.0.1:8082"),
    /** The disposable project copy the IDE opened; null when the suite was started without a prepared project. */
    val projectDir: Path? = System.getProperty("abyssus.ui.project")?.let { Paths.get(it) },
    val fixtureSource: Path = Paths.get(System.getProperty("abyssus.ui.fixtureSource")),
    val reportsDir: Path = Paths.get(System.getProperty("abyssus.ui.reportsDir")),
    val connectTimeout: Duration = Duration.ofSeconds(System.getProperty("abyssus.ui.connectTimeoutSeconds", "15").toLong()),
) {
    fun requireProjectDir(): Path =
        projectDir ?: error("Pass -PuiProject=<copy of Untitled> (scripts/run-ui-tests.sh does this)")

    /** Connects to robot-server and fails promptly, naming the URL, when no IDE answers. */
    fun connect(): RemoteRobot {
        val robot = RemoteRobot(robotUrl)
        var lastError: Throwable? = null
        try {
            waitFor(connectTimeout, Duration.ofSeconds(1), "robot-server at $robotUrl") {
                try {
                    robot.callJs<Boolean>("true")
                } catch (e: Exception) {
                    lastError = e
                    false
                }
            }
        } catch (e: Exception) {
            lastError?.let { e.addSuppressed(it) }
            throw IllegalStateException(
                "No IDE answered at $robotUrl within ${connectTimeout.seconds}s (last error: ${lastError?.message}). " +
                    "Start one with scripts/run-ui-tests.sh or ./gradlew :plugin-abyssus:runIdeForUiTests.", e,
            )
        }
        return robot
    }
}
