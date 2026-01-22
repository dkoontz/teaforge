package teaforge

import kotlinx.coroutines.Deferred
import teaforge.utils.Maybe

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
)

sealed interface LoggerStatus {
    data object Disabled : LoggerStatus

    data class Enabled(
        val config: DebugLoggingConfig,
    ) : LoggerStatus
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
    val processHistoryEntry: (TRunnerModel, HistoryEntry<TMessage, TProgramModel>) -> TRunnerModel,
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
    val subscriptions: Map<TSubscription, TSubscriptionState>,
    val runnerModel: TRunnerModel,
    val programModel: TProgramModel,
    val inFlightEffects: List<InFlightEffect<TEffect, TRunnerModel, TMessage>>,
)

sealed interface HistoryEventSource<TMessage> {
    object ProgramInit : HistoryEventSource<Nothing>
    data class ProgramMessage<TMessage>(
        val message: TMessage,
    ) : HistoryEventSource<TMessage>
}

data class HistoryEntry<TMessage, TProgramModel>(
    val source: HistoryEventSource<TMessage>,
    val programModelAfterEvent: TProgramModel,
)
