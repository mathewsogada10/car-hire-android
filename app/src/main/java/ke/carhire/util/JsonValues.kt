package ke.carhire.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

val json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}

fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

fun JsonObject.amount(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L

fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
