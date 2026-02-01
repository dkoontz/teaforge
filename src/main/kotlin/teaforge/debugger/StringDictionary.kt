package teaforge.debugger

/**
 * Dictionary for LZ78-style string compression in debug logs.
 * Replaces repeated strings with compact integer references (e.g., "@0", "@5").
 *
 * Used for:
 * - _type values (e.g., "InputSubsystem.Model" → "@0")
 * - Property names/keys (e.g., "leftJoystick" → "@5")
 * - String values (repeated string constants)
 */
class StringDictionary {
    private var nextId = 0
    private val stringToId = mutableMapOf<String, Int>()
    private val pendingDefinitions = mutableMapOf<Int, String>()

    /**
     * Get a reference for a string value.
     * If the string hasn't been seen before, assigns a new ID and queues it for definition.
     *
     * @param value The string to get a reference for
     * @return A reference string like "@0" or "@5"
     */
    fun getReference(value: String): String {
        val id =
            stringToId.getOrPut(value) {
                val newId = nextId++
                pendingDefinitions[newId] = value
                newId
            }
        return "@$id"
    }

    /**
     * Flush pending string definitions that need to be emitted.
     * Returns null if there are no pending definitions.
     *
     * @return Map of ID to string value, or null if empty
     */
    fun flushPendingDefinitions(): Map<Int, String>? {
        if (pendingDefinitions.isEmpty()) return null
        val result = pendingDefinitions.toMap()
        pendingDefinitions.clear()
        return result
    }

    /**
     * Check if there are pending definitions to emit.
     */
    fun hasPendingDefinitions(): Boolean = pendingDefinitions.isNotEmpty()
}
