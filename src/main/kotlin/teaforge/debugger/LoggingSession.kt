package teaforge.debugger

import teaforge.DebugLoggingConfig

data class LoggingSession(
    val config: DebugLoggingConfig,
    val dictionary: StringDictionary = StringDictionary(),
    val headerWritten: Boolean = false,
    val lastModel: JsonValue = JsonValue.JsonObject(emptyMap()),
)

fun writeHeaderIfNeeded(session: LoggingSession): LoggingSession {
    if (!session.config.compressionEnabled || session.headerWritten) return session
    session.config.log("""{"type":"header","version":2,"compression":"stringDict"}""")
    return session.copy(headerWritten = true)
}

fun emitPendingDictionaryDefinitions(session: LoggingSession) {
    if (!session.config.compressionEnabled) return
    val pending = session.dictionary.flushPendingDefinitions() ?: return
    val entriesJson =
        pending.entries.joinToString(",") { (id, value) ->
            "\"$id\":\"${escapeJsonString(value)}\""
        }
    session.config.log("""{"type":"stringDict","strings":{$entriesJson}}""")
}

fun updateLastModel(
    session: LoggingSession,
    model: JsonValue,
): LoggingSession = session.copy(lastModel = model)

private fun escapeJsonString(s: String): String =
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
