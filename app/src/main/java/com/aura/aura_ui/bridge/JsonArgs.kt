package com.aura.aura_ui.bridge

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Small, forgiving JSON argument reader used by every tool implementation. */
object JsonArgs {
    fun str(args: JsonObject, key: String): String? =
        (args[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    fun rawStr(args: JsonObject, key: String): String? =
        (args[key] as? JsonPrimitive)?.contentOrNull

    fun int(args: JsonObject, key: String, default: Int? = null): Int? =
        (args[key] as? JsonPrimitive)?.intOrNull ?: default

    fun long(args: JsonObject, key: String, default: Long? = null): Long? =
        (args[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: default

    fun float(args: JsonObject, key: String): Float? =
        (args[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()

    fun bool(args: JsonObject, key: String, default: Boolean = false): Boolean =
        (args[key] as? JsonPrimitive)?.booleanOrNull ?: default

    fun intList(args: JsonObject, key: String): List<Int>? =
        (args[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }

    fun stringList(args: JsonObject, key: String): List<String> =
        (args[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
}
