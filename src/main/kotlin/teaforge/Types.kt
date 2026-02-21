package teaforge

import kotlinx.coroutines.Deferred
import teaforge.debugger.StringDictionary
import teaforge.utils.Maybe

@JvmInline
value class SubscriptionIdentifier(val value: String)

// The completion function receives current model, returns updated model + optional message
typealias EffectCompletion<TRunnerModel, TMessage> =
    (TRunnerModel) -> Pair<TRunnerModel, Maybe<TMessage>>

sealed interface EffectResult<TRunnerModel, TMessage> {
    // Synchronous effect - completes immediately
    data class Sync<TRunnerModel, TMessage>(
        val updatedModel: TRunnerModel,
        val message: Maybe<TMessage>,
    ) : EffectResult<TRunnerModel, TMessage>

    // Async effect - synchronous model update + deferred message generation and model update
    data class Async<TRunnerModel, TMessage>(
        val updatedModel: TRunnerModel,
        val completion: suspend () -> EffectCompletion<TRunnerModel, TMessage>,
    ) : EffectResult<TRunnerModel, TMessage>
}

// Tracks an in-flight async effect
data class InFlightEffect<TEffect, TRunnerModel, TMessage>(
    val effect: TEffect,
    val asyncProcess: Deferred<EffectCompletion<TRunnerModel, TMessage>>,
)

data class ProgramConfig<TEffect, TMessage, TModel, TSubscription>(
    val init: (List<String>) -> Pair<TModel, List<TEffect>>,
    val update: (TMessage, TModel) -> Pair<TModel, List<TEffect>>,
    val subscriptions: (TModel) -> List<TSubscription>,
)

data class DebugLoggingConfig(
    val getTimestamp: () -> Long,
    val log: (json: String) -> Unit,
    val compressionEnabled: Boolean = false,
)

/**
 * Manages mutable state for a debug logging session.
 * Holds the string dictionary and tracks whether the header has been written.
 */
class LoggingSession(val config: DebugLoggingConfig) {
    val dictionary: StringDictionary = StringDictionary()
    var headerWritten: Boolean = false
        private set

    fun markHeaderWritten() {
        headerWritten = true
    }

    /**
     * Write the header entry if compression is enabled and header hasn't been written yet.
     */
    fun writeHeaderIfNeeded() {
        if (config.compressionEnabled && !headerWritten) {
            config.log("""{"type":"header","version":1,"compression":"stringDict"}""")
            markHeaderWritten()
        }
    }

    /**
     * Emit a stringDict entry if there are pending dictionary definitions.
     */
    fun emitPendingDictionaryDefinitions() {
        if (!config.compressionEnabled) return
        val pending = dictionary.flushPendingDefinitions() ?: return
        val entriesJson =
            pending.entries.joinToString(",") { (id, value) ->
                "\"$id\":\"${escapeJsonString(value)}\""
            }
        config.log("""{"type":"stringDict","strings":{$entriesJson}}""")
    }

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
}

sealed interface LoggerStatus {
    data object Disabled : LoggerStatus

    data class Enabled(
        val session: LoggingSession,
    ) : LoggerStatus {
        constructor(config: DebugLoggingConfig) : this(LoggingSession(config))

        val config: DebugLoggingConfig get() = session.config
    }
}

data class ProgramRunnerConfig<
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    >(
    val initRunner: (List<String>) -> TRunnerModel,
    val processEffect: (TRunnerModel, TEffect) -> EffectResult<TRunnerModel, TMessage>,
    val processSubscription: (TRunnerModel, TSubscriptionState) -> Triple<
        TRunnerModel,
        TSubscriptionState,
        Maybe<TMessage>,
        >,
    val startSubscription: (TRunnerModel, TSubscription) -> Pair<TRunnerModel, TSubscriptionState>,
    val stopSubscription: (TRunnerModel, TSubscriptionState) -> TRunnerModel,
    val startOfUpdateCycle: (TRunnerModel) -> TRunnerModel,
    val endOfUpdateCycle: (TRunnerModel) -> TRunnerModel,
    val getUniqueIdentifierForSubscription: (TSubscription) -> SubscriptionIdentifier,
    val loggerStatus: () -> LoggerStatus,
)

data class ProgramRunnerInstance<
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    >(
    val runnerConfig: ProgramRunnerConfig<
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState,
        >,
    val programConfig: ProgramConfig<TEffect, TMessage, TProgramModel, TSubscription>,
    val pendingMessages: List<TMessage>,
    val pendingEffects: List<TEffect>,
    val subscriptions: Map<SubscriptionIdentifier, Pair<TSubscription, TSubscriptionState>>,
    val runnerModel: TRunnerModel,
    val programModel: TProgramModel,
    val inFlightEffects: List<InFlightEffect<TEffect, TRunnerModel, TMessage>>,
)
