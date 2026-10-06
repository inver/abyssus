// Match full verifier descriptions, so accepting one call cannot accept another call to the same API.
val internalApiAllowlist = layout.projectDirectory.file("gradle/plugin-internal-api-allowlist.txt").asFile
val verificationReports = layout.buildDirectory.dir("reports/pluginVerifier").get().asFile
val internalApiReports = fileTree(verificationReports) { include("**/internal-api-usages.txt") }
val verificationVerdicts = fileTree(verificationReports) { include("**/verification-verdict.txt") }

val checkPluginInternalApis = tasks.register("checkPluginInternalApis") {
    val allowlist = internalApiAllowlist
    val reports = internalApiReports
    val verdicts = verificationVerdicts
    inputs.file(internalApiAllowlist)
    inputs.files(internalApiReports, verificationVerdicts)
    doLast {
        check(verdicts.files.isNotEmpty()) { "No plugin verification reports; run :verifyPlugin first" }
        for (verdict in verdicts.files) {
            val text = verdict.readText()
            val count = Regex("""\b(\d+) usages? of internal API\b""").find(text)?.groupValues?.get(1)?.toInt() ?: 0
            val details = verdict.resolveSibling("internal-api-usages.txt")
            val lines = if (details.isFile) details.readLines().count { it.isNotBlank() } else 0
            check(lines == count) { "Internal API report is incomplete: $verdict reports $count usages, but $details has $lines descriptions" }
        }
        val known = allowlist.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.toSet()
        val unknown = reports.files.flatMap { it.readLines() }.filter { it.isNotBlank() && it !in known }.toSortedSet()
        if (unknown.isNotEmpty()) throw GradleException("New internal API usages require review:\n" + unknown.joinToString("\n"))
    }
}

tasks.named("verifyPlugin") { finalizedBy(checkPluginInternalApis) }
