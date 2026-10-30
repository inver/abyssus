/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.loading

import net.nevinsky.abyssus.lib.core.assets.loading.exception.AssetPipelineException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.DependencyFailedException
import net.nevinsky.abyssus.lib.core.assets.loading.exception.MissingDependencyException

/** Where one load of an asset is. */
internal enum class Phase {
    Preparing,
    Waiting,
    Uploading,
    UploadDone,
    Ready,
    Failed,
}

/**
 * What [LoadBook] knows about one load of an asset: [id] is its generation (every request gets a new one, so a message
 * from a superseded load is told apart from the current one by id), [phase], and the names it waits for once it is prepared.
 */
internal interface LoadView {
    val id: Long
    val name: String
    val phase: Phase
    val dependencies: Set<String>?
    val error: Throwable?

    /** The built asset was marked changed; its replacement is not built yet. */
    val stale: Boolean
}

/**
 * The revision and dependency bookkeeping of an [AssetStorage]: the current load of each name ([lives]), the replacement of
 * a built asset while it loads ([revisions]), which generation owns a name, and the questions the pipeline asks about them
 * (is this settled, what does it still wait for, did a dependency fail, which loads form a cycle). It holds no GL object and
 * does no upload, build, discard or notification; the storage owns those, and calls this on its render thread only.
 */
internal class LoadBook<L : LoadView> {
    val lives = LinkedHashMap<String, L>()
    val revisions = LinkedHashMap<String, L>()

    /** Every load in flight or settled: the current ones, then the replacements. */
    fun active(): List<L> = lives.values.toList() + revisions.values

    /** The load of [name] or its replacement that has generation [id]; null when it was forgotten or superseded meanwhile. */
    fun owner(name: String, id: Long): L? =
        revisions[name]?.takeIf { it.id == id } ?: lives[name]?.takeIf { it.id == id }

    /** The replacement of [name] when it has one, else its current load. */
    fun entry(name: String): L? = revisions[name] ?: lives[name]

    /** Built and not marked changed. */
    fun settled(view: LoadView): Boolean = view.phase == Phase.Ready && !view.stale

    fun loading(view: LoadView): Boolean = when (view.phase) {
        Phase.Failed -> false
        Phase.Ready -> view.stale
        else -> true
    }

    fun blocksOnDependencies(view: LoadView): Boolean =
        view.phase == Phase.Waiting || view.phase == Phase.Uploading || view.phase == Phase.UploadDone

    /** Every dependency of [view] is built, current, and has no replacement loading. */
    fun depsSatisfied(view: LoadView): Boolean {
        val dependencies = view.dependencies ?: return false
        return dependencies.all { lives[it]?.let(::settled) == true && !revisions.containsKey(it) }
    }

    /** The members of [dependencies] that are not settled. */
    fun unmet(dependencies: Set<String>): Set<String> =
        dependencies.filterNot { lives[it]?.let(::settled) == true }.toSet()

    /** [names] and, transitively, what the loading and built assets among them need. */
    fun withDependencies(names: Set<String>): Set<String> {
        val keep = HashSet(names)
        var frontier: Collection<String> = names
        while (frontier.isNotEmpty()) {
            frontier = frontier
                .flatMap { (lives[it]?.dependencies.orEmpty()) + (revisions[it]?.dependencies.orEmpty()) }
                .filter(keep::add)
        }
        return keep
    }

    /** Why [view] cannot go on, when a dependency of it is gone or has failed; null while all of them may still be built. */
    fun dependencyProblem(view: LoadView): Throwable? {
        for (dependency in view.dependencies ?: return null) {
            val other = lives[dependency]
            when {
                other == null -> return MissingDependencyException(view.name, dependency)
                other.phase == Phase.Failed -> return DependencyFailedException(
                    view.name,
                    dependency,
                    other.error ?: AssetPipelineException("dependency failed"),
                )
            }
        }
        return null
    }

    /** The names of waiting loads that wait for each other in a cycle, in discovery order; empty when there is none. */
    fun cyclicNames(): Set<String> {
        val color = HashMap<String, Int>()
        val stack = ArrayList<String>()
        val cyclic = LinkedHashSet<String>()

        fun outgoing(name: String): List<String> {
            val dependencies = entry(name)?.dependencies ?: return emptyList()
            return dependencies.filter { dependency ->
                val other = entry(dependency)
                other != null && other.phase != Phase.Ready && other.phase != Phase.Failed
            }
        }

        fun dfs(name: String) {
            color[name] = 1
            stack.add(name)
            for (dependency in outgoing(name)) {
                when (color[dependency] ?: 0) {
                    0 -> dfs(dependency)
                    1 -> {
                        val at = stack.indexOf(dependency)
                        if (at >= 0) cyclic.addAll(stack.subList(at, stack.size))
                    }
                }
            }
            stack.removeAt(stack.lastIndex)
            color[name] = 2
        }

        for (live in active().filter { it.phase == Phase.Waiting }) {
            if ((color[live.name] ?: 0) == 0) dfs(live.name)
        }
        return cyclic
    }
}
