package com.aura.mcp.server

/** Server instructions/doctrine surfaced to MCP clients. */
object McpInstructions {
    object Doctrine {
        val text: String = McpRpc.INSTRUCTIONS
        const val resourcePolicy: String = "aura://safety/policy"
        const val resourceToolGuide: String = "aura://tools/guide"
    }

    fun connectInstructions(): String = Doctrine.text
}
