/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.dto

import net.nevinsky.abyssus.lib.core.assets.Asset
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.ui.model.UiActionType
import net.nevinsky.abyssus.lib.core.ui.model.UiAdmission
import net.nevinsky.abyssus.lib.core.ui.model.UiCollection
import net.nevinsky.abyssus.lib.core.ui.model.UiMeta
import net.nevinsky.abyssus.lib.core.ui.model.UiWidget

enum class UiUsageProblem { MISSING, WRONG_TYPE, DUPLICATE, UNSUPPORTED, MISSING_SCREEN, CYCLE }

/** A problem of the UI usage graph: [asset] is the registered name or the navigating UI asset, [target] the asset it names. */
data class UiUsageIssue(val asset: String, val problem: UiUsageProblem, val target: String? = null)

/** The folders some root reaches, and the UI problems found while following the registry and navigation links. */
data class AssetUsage(val used: Set<String>, val uiIssues: List<UiUsageIssue>)

/**
 * The UI side of asset usage: registered `uiAssetNames` are roots when they name a supported UI asset, and a reached UI
 * asset reaches the UI assets its `openScreen` / `showOverlay` actions name. Pure; problems never stop the traversal.
 */
class UiAssetUsage(assets: List<Asset<Any>>, private val registered: List<String>) {
    private val byName = assets.associateBy { it.name }

    private fun collectionOf(asset: Asset<Any>): UiCollection? =
        ((asset.meta.additional as? UiMeta)?.admission as? UiAdmission.Admitted)?.collection

    private fun isUi(asset: Asset<Any>?) = asset != null && asset.type == MetaType.UI

    /** The registered, supported UI assets. */
    fun roots(): List<Asset<Any>> = registered.distinct().mapNotNull { byName[it] }.filter { isUi(it) && collectionOf(it) != null }

    /** The UI assets [asset] navigates to; a refused one is still reached, but not followed. */
    fun links(asset: Asset<Any>): List<Asset<Any>> =
        targets(asset).mapNotNull { (target, _) -> byName[target]?.takeIf(::isUi) }

    fun issues(used: Set<String>): List<UiUsageIssue> {
        val result = LinkedHashSet<UiUsageIssue>()
        val seen = HashSet<String>()
        for (name in registered) {
            if (!seen.add(name)) { result += UiUsageIssue(name, UiUsageProblem.DUPLICATE); continue }
            val asset = byName[name]
            when {
                asset == null -> result += UiUsageIssue(name, UiUsageProblem.MISSING)
                !isUi(asset) -> result += UiUsageIssue(name, UiUsageProblem.WRONG_TYPE)
                collectionOf(asset) == null -> result += UiUsageIssue(name, UiUsageProblem.UNSUPPORTED)
            }
        }
        val graph = LinkedHashMap<String, Set<String>>()
        for (asset in used.mapNotNull { byName[it] }.filter { isUi(it) && collectionOf(it) != null }) {
            val edges = LinkedHashSet<String>()
            for ((targetName, screenId) in targets(asset)) {
                val target = byName[targetName]
                when {
                    target == null -> result += UiUsageIssue(asset.name, UiUsageProblem.MISSING, targetName)
                    !isUi(target) -> result += UiUsageIssue(asset.name, UiUsageProblem.WRONG_TYPE, targetName)
                    collectionOf(target) == null -> result += UiUsageIssue(asset.name, UiUsageProblem.UNSUPPORTED, targetName)
                    else -> {
                        edges += targetName
                        if (screenId != null && collectionOf(target)!!.screen(screenId) == null)
                            result += UiUsageIssue(asset.name, UiUsageProblem.MISSING_SCREEN, targetName)
                    }
                }
            }
            graph[asset.name] = edges
        }
        cycles(graph).forEach { result += UiUsageIssue(it, UiUsageProblem.CYCLE) }
        return result.toList()
    }

    /** `assetName` / `screenId` pairs of the cross-asset navigation actions of [asset], in document order. */
    private fun targets(asset: Asset<Any>): List<Pair<String, String?>> {
        val collection = collectionOf(asset) ?: return emptyList()
        val found = mutableListOf<Pair<String, String?>>()
        val stack = ArrayDeque<UiWidget>()
        collection.screens.asReversed().forEach { stack.addLast(it.root) }
        while (stack.isNotEmpty()) {
            val widget = stack.removeLast()
            for (action in widget.actions.values) {
                val name = action.assetName ?: continue
                if (action.type == UiActionType.OPEN_SCREEN || action.type == UiActionType.SHOW_OVERLAY) found += name to action.screenId
            }
            widget.children.asReversed().forEach { stack.addLast(it) }
        }
        return found
    }

    /** Assets on a cycle, found by an iterative depth-first search (a self link counts). */
    private fun cycles(graph: Map<String, Set<String>>): Set<String> {
        val onCycle = LinkedHashSet<String>()
        val done = HashSet<String>()
        for (start in graph.keys) {
            if (start in done) continue
            val path = ArrayList<String>()
            val iterators = ArrayList<Iterator<String>>()
            path += start; iterators += graph.getValue(start).iterator()
            while (path.isNotEmpty()) {
                val it = iterators.last()
                if (!it.hasNext()) { done += path.removeLast(); iterators.removeLast(); continue }
                val next = it.next()
                val at = path.indexOf(next)
                if (at >= 0) onCycle += path.subList(at, path.size)
                else if (next !in done && next in graph) { path += next; iterators += graph.getValue(next).iterator() }
            }
        }
        return onCycle
    }
}
