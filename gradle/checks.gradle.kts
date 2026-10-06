// Shared source checks. Inputs are captured during configuration so execution supports configuration caching.
fun registerSourceCheck(name: String, sources: FileCollection, pattern: Regex, message: String) {
    val base = layout.projectDirectory.asFile
    val check = tasks.register(name) {
        inputs.files(sources)
        doLast {
            val found = sources.files.sorted().flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (pattern.containsMatchIn(line)) "${file.relativeTo(base)}:${index + 1}: ${line.trim()}" else null
                }
            }
            if (found.isNotEmpty()) throw GradleException(message + "\n" + found.joinToString("\n"))
        }
    }
    tasks.named("check") { dependsOn(check) }
}

// The root applies this script before a module's own build script sets its excludes.
afterEvaluate {
    if (extra.has("abyssusSingletonExcludes")) {
        @Suppress("UNCHECKED_CAST")
        val excluded = extra["abyssusSingletonExcludes"] as List<String>
        registerSourceCheck("checkNoSingletons", fileTree("src/main/kotlin") {
            include("**/*.kt")
            exclude(excluded)
        }, Regex("""^\s*(?:(?:private|internal|public|protected)\s+)*(companion\s+object\b|object\s+[A-Za-z_])"""),
            "Singletons are not allowed in $path; inject an instance instead:")
    }
}

if (extra.has("abyssusRunCatchingRoots")) {
    @Suppress("UNCHECKED_CAST")
    val roots = extra["abyssusRunCatchingRoots"] as List<String>
    registerSourceCheck("checkNoRunCatching", files(roots.map { root -> fileTree(root) { include("**/*.kt") } }),
        Regex("""\brunCatching\s*\{"""), "Use runCatchingKeepingCancellation instead of runCatching:")
}
