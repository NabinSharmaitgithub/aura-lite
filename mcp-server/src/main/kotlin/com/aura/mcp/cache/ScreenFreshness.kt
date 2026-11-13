package com.aura.mcp.cache

/** Freshness of the last extracted screen snapshot relative to an expected generation. */
enum class ScreenFreshness {
    EMPTY,
    FRESH,
    STALE,
}
