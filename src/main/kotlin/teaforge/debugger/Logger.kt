package teaforge.debugger

import teaforge.debugger.JsonValue.Companion.arr
import teaforge.debugger.JsonValue.Companion.num
import teaforge.debugger.JsonValue.Companion.obj
import teaforge.debugger.JsonValue.Companion.str
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicLong

/**
 * Logger for TEA applications.
 * Records init, update, and subscription events to a JSONL file.
 * Uses reflection to serialize all values - no type parameters needed.
 */
class Logger(
    private val outputPath: String,
) {
    private val sequenceCounter = AtomicLong(0)
    private var writer: BufferedWriter? = null
    private var lastSubscriptions: Set<String> = emptySet()

    /**
     * Start recording to the output file.
     */
    fun start() {
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        writer = BufferedWriter(FileWriter(file))
    }

    /**
     * Stop recording and close the output file.
     */
    fun stop() {
        writer?.close()
        writer = null
    }

    /**
     * Record the init event.
     */
    fun recordInit(
        model: Any,
        effects: List<Any>,
    ) {
        ensureStarted()

        val initJson =
            obj(
                "entryType" to str("init"),
                "sequence" to num(sequenceCounter.getAndIncrement()),
                "timestamp" to num(System.currentTimeMillis()),
                "model" to TeaSerializer.serialize(model),
                "effects" to arr(effects.map { TeaSerializer.serialize(it) }),
            )

        writeLine(initJson)
    }

    /**
     * Record an update event.
     */
    fun recordUpdate(
        message: Any,
        model: Any,
        effects: List<Any>,
    ) {
        ensureStarted()

        val updateJson =
            obj(
                "entryType" to str("update"),
                "sequence" to num(sequenceCounter.getAndIncrement()),
                "timestamp" to num(System.currentTimeMillis()),
                "message" to TeaSerializer.serializeMessage(message),
                "model" to TeaSerializer.serialize(model),
                "effects" to arr(effects.map { TeaSerializer.serialize(it) }),
            )

        writeLine(updateJson)
    }

    /**
     * Record a subscription change event.
     */
    fun recordSubscriptionChange(
        currentSubscriptions: List<Any>,
        startedReason: String? = null,
        stoppedReason: String? = null,
    ) {
        ensureStarted()

        // Create string identifiers for subscriptions
        val currentIds = currentSubscriptions.map { subscriptionId(it) }.toSet()

        val started = currentIds - lastSubscriptions
        val stopped = lastSubscriptions - currentIds

        // Only record if there are changes
        if (started.isEmpty() && stopped.isEmpty()) return

        val startedSubscriptions =
            currentSubscriptions.filter {
                subscriptionId(it) in started
            }
        val stoppedIds = stopped.toList()

        val subscriptionChangeJson =
            obj(
                "entryType" to str("subscriptionChange"),
                "sequence" to num(sequenceCounter.getAndIncrement()),
                "timestamp" to num(System.currentTimeMillis()),
                "started" to
                    arr(
                        startedSubscriptions.map { sub ->
                            val serialized = TeaSerializer.serialize(sub)
                            if (serialized is JsonValue.JsonObject && startedReason != null) {
                                serialized.with("_reason", str(startedReason))
                            } else {
                                serialized
                            }
                        },
                    ),
                "stopped" to
                    arr(
                        stoppedIds.map { id ->
                            obj(
                                "_subscriptionId" to str(id),
                                "_reason" to str(stoppedReason ?: "Subscription ended"),
                            )
                        },
                    ),
            )

        writeLine(subscriptionChangeJson)
        lastSubscriptions = currentIds
    }

    /**
     * Generate a unique identifier for a subscription.
     */
    private fun subscriptionId(subscription: Any): String = "sub_${subscription.hashCode().toString(16)}"

    /**
     * Ensure the logger has been started.
     */
    private fun ensureStarted() {
        if (writer == null) {
            throw IllegalStateException("Logger.start() must be called before recording events")
        }
    }

    /**
     * Write a JSON line to the output file.
     */
    private fun writeLine(json: JsonValue) {
        writer?.let { w ->
            w.write(json.toJsonString())
            w.newLine()
            w.flush()
        }
    }
}
