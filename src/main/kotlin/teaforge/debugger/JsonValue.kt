package teaforge.debugger

/**
 * A simple JSON value representation for serialization.
 * Provides a type-safe way to build JSON structures before converting to string.
 */
sealed interface JsonValue {
    fun toJsonString(): String

    data class JsonObject(val entries: Map<String, JsonValue>) : JsonValue {
        constructor(vararg pairs: Pair<String, JsonValue>) : this(pairs.toMap())

        override fun toJsonString(): String {
            if (entries.isEmpty()) return "{}"
            return entries.entries.joinToString(
                separator = ",",
                prefix = "{",
                postfix = "}",
            ) { (key, value) ->
                "\"${escapeString(key)}\":${value.toJsonString()}"
            }
        }

        fun with(
            key: String,
            value: JsonValue,
        ): JsonObject = JsonObject(entries + (key to value))

        fun withType(typeName: String): JsonObject = JsonObject(mapOf("_type" to JsonString(typeName)) + entries)
    }

    data class JsonArray(val elements: List<JsonValue>) : JsonValue {
        constructor(vararg elements: JsonValue) : this(elements.toList())

        override fun toJsonString(): String {
            if (elements.isEmpty()) return "[]"
            return elements.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
            ) { it.toJsonString() }
        }
    }

    data class JsonString(val value: String) : JsonValue {
        override fun toJsonString(): String = "\"${escapeString(value)}\""
    }

    data class JsonNumber(val value: Number) : JsonValue {
        override fun toJsonString(): String =
            when (value) {
                is Double -> if (value.isNaN() || value.isInfinite()) "null" else value.toString()
                is Float -> if (value.isNaN() || value.isInfinite()) "null" else value.toString()
                else -> value.toString()
            }
    }

    data class JsonBoolean(val value: Boolean) : JsonValue {
        override fun toJsonString(): String = value.toString()
    }

    data object JsonNull : JsonValue {
        override fun toJsonString(): String = "null"
    }

    companion object {
        fun escapeString(s: String): String =
            buildString {
                for (c in s) {
                    when (c) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        '\b' -> append("\\b")
                        '\u000C' -> append("\\f")
                        else ->
                            if (c.code < 32) {
                                append("\\u${c.code.toString(16).padStart(4, '0')}")
                            } else {
                                append(c)
                            }
                    }
                }
            }

        fun obj(vararg pairs: Pair<String, JsonValue>): JsonObject = JsonObject(*pairs)
        fun arr(vararg elements: JsonValue): JsonArray = JsonArray(*elements)
        fun arr(elements: List<JsonValue>): JsonArray = JsonArray(elements)
        fun str(value: String): JsonString = JsonString(value)
        fun num(value: Number): JsonNumber = JsonNumber(value)
        fun bool(value: Boolean): JsonBoolean = JsonBoolean(value)
        val nullValue: JsonNull = JsonNull
    }
}
