package com.aura.aura_ui.agent.strategy

/** Decides between cheap tree-only perception and full visual perception. */
object PerceptionTiering {
    enum class Tier { TREE_ONLY, FULL }

    data class Signals(
        val interactiveNodes: Int = 0,
        val labeledNodes: Int = 0,
        val totalNodes: Int = 0,
        val screenChanged: Boolean = false,
        val lastFailureCount: Int = 0,
        val canvasLike: Boolean = false,
        val requestedFull: Boolean = false,
    )

    data class Decision(val tier: Tier, val reason: String)

    fun decide(s: Signals): Decision {
        if (s.requestedFull) return Decision(Tier.FULL, "caller requested full perception")
        if (s.canvasLike) return Decision(Tier.FULL, "canvas-like surface lacks structural nodes")
        if (s.totalNodes == 0) return Decision(Tier.FULL, "empty accessibility tree")
        if (s.interactiveNodes < 2 && s.totalNodes < 8) return Decision(Tier.FULL, "sparse/ambiguous tree")
        if (s.lastFailureCount >= 2) return Decision(Tier.FULL, "repeated action failures on current screen")
        if (s.screenChanged) return Decision(Tier.TREE_ONLY, "tree-only after settled change")
        return Decision(Tier.TREE_ONLY, "structural tree sufficient")
    }
}
