// Import-only package graph, separately for each module. No source or classpath is loaded at execution time.
val moduleSources = allprojects.associate { module ->
    module.path to module.fileTree("src/main/kotlin") { include("**/*.kt") }
}
val cycleAllowlist = layout.projectDirectory.file("gradle/package-cycles.allowlist").asFile
val packageCycles = tasks.register("checkPackageCycles") {
    val sourceSets = moduleSources
    val allowlistFile = cycleAllowlist
    group = "verification"
    description = "Reject cyclic package import edges absent from the transition allowlist."
    inputs.files(moduleSources.values)
    inputs.file(cycleAllowlist)
    doLast {
        val allowed = allowlistFile.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        val violations = mutableListOf<String>()
        val declaration = Regex("""(?m)^package\s+([\w.]+)""")
        val imports = Regex("""(?m)^import\s+([\w.]+)""")
        for ((module, sources) in sourceSets) {
            val texts = sources.files.sorted().map { it.readText() }
            val packages = texts.mapNotNull { declaration.find(it)?.groupValues?.get(1) }
                .filter { it == "net.nevinsky.abyssus" || it.startsWith("net.nevinsky.abyssus.") }.toSet()
            // Strip the common package prefix of this module, then group its immediate child packages.
            val parts = packages.map { it.split('.') }
            val prefixLength = if (parts.isEmpty()) 0 else parts.first().indices.takeWhile { index ->
                parts.all { it.getOrNull(index) == parts.first()[index] }
            }.size
            fun group(pkg: String): String = pkg.split('.').drop(prefixLength).firstOrNull() ?: "(root)"
            val graph = mutableMapOf<String, MutableSet<String>>()
            for (text in texts) {
                val pkg = declaration.find(text)?.groupValues?.get(1) ?: continue
                if (pkg !in packages) continue
                val from = group(pkg)
                if (from == "(root)") continue
                for (match in imports.findAll(text)) {
                    val imported = match.groupValues[1]
                    val target = packages.filter { imported.startsWith("$it.") }.maxByOrNull { it.length } ?: continue
                    val to = group(target)
                    if (to != "(root)" && from != to) graph.getOrPut(from) { mutableSetOf() }.add(to)
                }
            }
            fun reaches(start: String, target: String, visited: MutableSet<String>): Boolean {
                if (start == target) return true
                if (!visited.add(start)) return false
                return graph[start].orEmpty().any { reaches(it, target, visited) }
            }
            for ((from, targets) in graph) for (to in targets) {
                if (reaches(to, from, mutableSetOf())) {
                    val edge = "$module $from -> $to"
                    if (edge !in allowed) violations.add(edge)
                }
            }
        }
        if (violations.isNotEmpty()) throw GradleException("Unapproved package cycles:\n" + violations.sorted().joinToString("\n"))
    }
}
tasks.named("check") { dependsOn(packageCycles) }
