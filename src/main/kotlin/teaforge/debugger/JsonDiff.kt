package teaforge.debugger

/**
 * Represents a single diff operation between two JSON values.
 * Follows a format inspired by JSON Patch (RFC 6902).
 */
sealed interface DiffOperation {
    /** Field exists in new value but not in old value. */
    data class Add(
        val path: String,
        val value: JsonValue,
    ) : DiffOperation

    /** Field exists in both values but has a different value. */
    data class Replace(
        val path: String,
        val value: JsonValue,
    ) : DiffOperation

    /** Field exists in old value but not in new value. */
    data class Remove(
        val path: String,
    ) : DiffOperation
}

/**
 * Computes a structural diff between two JsonValue trees.
 * Produces a flat list of DiffOperation values with JSON Pointer paths (RFC 6901).
 *
 * - JsonObject fields are compared key-by-key, recursing into nested objects/arrays.
 * - JsonArray elements are compared element-by-element by index.
 * - All other JsonValue types are compared atomically by equality.
 */
object JsonDiff {
    /**
     * Compute the diff between [oldValue] and [newValue].
     * Returns a list of operations that when applied to [oldValue] would produce [newValue].
     */
    fun diff(
        oldValue: JsonValue,
        newValue: JsonValue,
    ): List<DiffOperation> = diffAt("", oldValue, newValue)

    private fun diffAt(
        path: String,
        oldValue: JsonValue,
        newValue: JsonValue,
    ): List<DiffOperation> {
        // Both are objects — recurse key-by-key
        if (oldValue is JsonValue.JsonObject && newValue is JsonValue.JsonObject) {
            return diffObjects(path, oldValue, newValue)
        }

        // Both are arrays — recurse element-by-element
        if (oldValue is JsonValue.JsonArray && newValue is JsonValue.JsonArray) {
            return diffArrays(path, oldValue, newValue)
        }

        // Atomic comparison
        return if (oldValue == newValue) {
            emptyList()
        } else {
            listOf(DiffOperation.Replace(path, newValue))
        }
    }

    private fun diffObjects(
        path: String,
        oldObj: JsonValue.JsonObject,
        newObj: JsonValue.JsonObject,
    ): List<DiffOperation> {
        val operations = mutableListOf<DiffOperation>()

        val oldKeys = oldObj.entries.keys
        val newKeys = newObj.entries.keys

        // Fields removed (exist in old but not new)
        for (key in oldKeys - newKeys) {
            operations.add(DiffOperation.Remove(pointerAppend(path, key)))
        }

        // Fields added (exist in new but not old)
        for (key in newKeys - oldKeys) {
            operations.add(DiffOperation.Add(pointerAppend(path, key), newObj.entries.getValue(key)))
        }

        // Fields present in both — recurse
        for (key in oldKeys.intersect(newKeys)) {
            val childPath = pointerAppend(path, key)
            val oldChild = oldObj.entries.getValue(key)
            val newChild = newObj.entries.getValue(key)
            operations.addAll(diffAt(childPath, oldChild, newChild))
        }

        return operations
    }

    private fun diffArrays(
        path: String,
        oldArr: JsonValue.JsonArray,
        newArr: JsonValue.JsonArray,
    ): List<DiffOperation> {
        val operations = mutableListOf<DiffOperation>()
        val minLen = minOf(oldArr.elements.size, newArr.elements.size)

        // Compare elements at the same index
        for (i in 0 until minLen) {
            val childPath = pointerAppend(path, i.toString())
            operations.addAll(diffAt(childPath, oldArr.elements[i], newArr.elements[i]))
        }

        // Extra elements in new array — add
        for (i in minLen until newArr.elements.size) {
            operations.add(DiffOperation.Add(pointerAppend(path, i.toString()), newArr.elements[i]))
        }

        // Extra elements in old array — remove (emit in reverse order for correctness)
        for (i in oldArr.elements.size - 1 downTo minLen) {
            operations.add(DiffOperation.Remove(pointerAppend(path, i.toString())))
        }

        return operations
    }

    /**
     * Append a key to a JSON Pointer path, escaping special characters per RFC 6901:
     * - `~` is escaped as `~0`
     * - `/` is escaped as `~1`
     */
    private fun pointerAppend(
        path: String,
        key: String,
    ): String {
        val escaped = key.replace("~", "~0").replace("/", "~1")
        return "$path/$escaped"
    }
}
