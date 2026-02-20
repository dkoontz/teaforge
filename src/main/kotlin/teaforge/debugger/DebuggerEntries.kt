package teaforge.debugger

/**
 * JSONL entry types for the Time Travel Debugger.
 * Each entry type represents a different event in the TEA lifecycle.
 */
sealed interface DebuggerEntry

/**
 * Init entry - captures the result of init().
 * Records initial model and bootstrap effects.
 */
data class InitEntry(
    val sequence: Long,
    val timestamp: Long,
    val model: JsonValue,
    val effects: List<JsonValue>,
) : DebuggerEntry

/**
 * Update entry - captures each message dispatch through update().
 * Records the message, resulting model, and effects.
 */
data class UpdateEntry(
    val sequence: Long,
    val timestamp: Long,
    val message: JsonValue,
    val model: JsonValue,
    val effects: List<JsonValue>,
) : DebuggerEntry

/**
 * Subscription change entry - captures when subscriptions start or stop.
 */
data class SubscriptionChangeEntry(
    val sequence: Long,
    val timestamp: Long,
    val started: List<JsonValue>,
    val stopped: List<JsonValue>,
) : DebuggerEntry
