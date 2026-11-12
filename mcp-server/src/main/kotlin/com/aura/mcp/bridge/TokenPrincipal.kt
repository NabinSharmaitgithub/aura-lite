package com.aura.mcp.bridge

data class TokenPrincipal(
    val tokenId: String,
    val scopes: Set<McpScope>,
    val label: String?,
)

enum class McpScope {
    READ,
    WRITE,
}
