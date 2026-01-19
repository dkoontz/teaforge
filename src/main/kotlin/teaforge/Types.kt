package teaforge

import kotlinx.coroutines.Deferred
import teaforge.utils.Maybe

data class ProgramConfig<TEffect, TMessage, TModel, TSubscription>(
        val init: (List<String>) -> Pair<TModel, List<TEffect>>,
        val update: (TMessage, TModel) -> Pair<TModel, List<TEffect>>,
        val subscriptions: (TModel) -> List<TSubscription>,
)

data class ProgramRunnerConfig<
        TEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState>(
        val initRunner: (List<String>) -> TRunnerModel,
        val processEffect: suspend (TRunnerModel, TEffect) -> (TRunnerModel) -> Pair<TRunnerModel, Maybe<TMessage>>,
        val processSubscription:
                (TRunnerModel, TSubscriptionState) -> Triple<
                                TRunnerModel, TSubscriptionState, Maybe<TMessage>>,
        val startSubscription:
                (TRunnerModel, TSubscription) -> Pair<TRunnerModel, TSubscriptionState>,
        val stopSubscription: (TRunnerModel, TSubscriptionState) -> TRunnerModel,
        val startOfUpdateCycle: (TRunnerModel) -> TRunnerModel,
        val endOfUpdateCycle: (TRunnerModel) -> TRunnerModel,
        val processHistoryEntry:
                (TRunnerModel, HistoryEntry<TMessage, TProgramModel>) -> TRunnerModel,
)

data class ProgramRunnerInstance<
        TEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState>(
        val runnerConfig:
                ProgramRunnerConfig<
                        TEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>,
        val programConfig: ProgramConfig<TEffect, TMessage, TProgramModel, TSubscription>,
        val pendingMessages: List<TMessage>,
        val pendingEffects: List<TEffect>,
        val pendingLateEffects: List<Deferred<(TRunnerModel) -> Pair<TRunnerModel, Maybe<TMessage>>>>,
        val subscriptions: Map<TSubscription, TSubscriptionState>,
        val runnerModel: TRunnerModel,
        val programModel: TProgramModel,
)

sealed interface HistoryEventSource<TMessage> {
        object ProgramInit : HistoryEventSource<Nothing>
        data class ProgramMessage<TMessage>(val message: TMessage) : HistoryEventSource<TMessage>
}

data class HistoryEntry<TMessage, TProgramModel>(
        val source: HistoryEventSource<TMessage>,
        val programModelAfterEvent: TProgramModel,
)
