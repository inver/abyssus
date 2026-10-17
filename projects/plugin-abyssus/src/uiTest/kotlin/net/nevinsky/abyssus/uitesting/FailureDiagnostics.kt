package net.nevinsky.abyssus.uitesting

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** On a failing scenario writes the IDE frame screenshot and the component hierarchy next to the test reports. */
class FailureDiagnostics(private val env: () -> UiEnvironment, private val robot: () -> RemoteRobot) : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        runCatching {
            val dir: Path = env().reportsDir.resolve("failures")
                .resolve("${description.className.substringAfterLast('.')}.${description.methodName}")
            Files.createDirectories(dir)
            runCatching { Files.writeString(dir.resolve("hierarchy.html"), URI.create(env().robotUrl).toURL().readText()) }
            runCatching {
                val frame = robot().find<IdeFrame>(byXpath("//div[@class='IdeFrameImpl']"), java.time.Duration.ofSeconds(5))
                ImageIO.write(frame.getScreenshot(), "png", dir.resolve("screen.png").toFile())
            }
            Files.writeString(dir.resolve("failure.txt"), e.stackTraceToString())
        }
    }
}
