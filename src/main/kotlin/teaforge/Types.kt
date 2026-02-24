package teaforge

import kotlinx.coroutines.Deferred
import teaforge.debugger.LoggingSession
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

sealed interface LoggerStatus {
    data object Disabled : LoggerStatus

    class Enabled(
        var session: LoggingSession,
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
