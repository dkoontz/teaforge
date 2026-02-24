package teaforge.debugger

import teaforge.debugger.JsonValue.Companion.arr
import teaforge.debugger.JsonValue.Companion.num
import teaforge.debugger.JsonValue.Companion.obj
import teaforge.debugger.JsonValue.Companion.str

/**
 * Serializes DebuggerEntry objects to JsonValue for writing to JSONL.
 */
object EntrySerializer {
    /**
     * Serialize any DebuggerEntry to a JsonValue.
     */
    fun serialize(entry: DebuggerEntry): JsonValue {
        return when (entry) {
            is InitEntry -> serializeInit(entry)
            is UpdateEntry -> serializeUpdate(entry)
            is SubscriptionChangeEntry -> serializeSubscriptionChange(entry)
        }
    }

    /**
     * Serialize an InitEntry to JsonValue.
     */
    private fun serializeInit(entry: InitEntry): JsonValue {
        return obj(
            "entryType" to str("init"),
            "sequence" to num(entry.sequence),
            "timestamp" to num(entry.timestamp),
            "modelDiff" to arr(entry.modelDiff.map { serializeDiffOperation(it) }),
            "effects" to arr(entry.effects),
        )
    }

    /**
     * Serialize an UpdateEntry to JsonValue.
     */
    private fun serializeUpdate(entry: UpdateEntry): JsonValue {
        return obj(
            "entryType" to str("update"),
            "sequence" to num(entry.sequence),
            "timestamp" to num(entry.timestamp),
            "message" to entry.message,
            "modelDiff" to arr(entry.modelDiff.map { serializeDiffOperation(it) }),
            "effects" to arr(entry.effects),
        )
    }

    /**
     * Serialize a SubscriptionChangeEntry to JsonValue.
     */
    private fun serializeSubscriptionChange(entry: SubscriptionChangeEntry): JsonValue {
        return obj(
            "entryType" to str("subscriptionChange"),
            "sequence" to num(entry.sequence),
            "timestamp" to num(entry.timestamp),
            "started" to arr(entry.started),
            "stopped" to arr(entry.stopped),
        )
    }

    /**
     * Serialize a single DiffOperation to a JsonValue object.
     */
    fun serializeDiffOperation(op: DiffOperation): JsonValue {
        return when (op) {
            is DiffOperation.Add ->
                obj(
                    "op" to str("add"),
                    "path" to str(op.path),
                    "value" to op.value,
                )
            is DiffOperation.Replace ->
                obj(
                    "op" to str("replace"),
                    "path" to str(op.path),
                    "value" to op.value,
                )
            is DiffOperation.Remove ->
                obj(
                    "op" to str("remove"),
                    "path" to str(op.path),
                )
        }
    }

    /**
     * Serialize a DebuggerEntry to a JSON string.
     */
    fun toJsonString(entry: DebuggerEntry): String {
        return serialize(entry).toJsonString()
    }
}
