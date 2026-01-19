package teaforge

import kotlinx.coroutines.Deferred
import teaforge.utils.Maybe

data class ProgramConfig<TEffect, TInstantEffect: TEffect, TLateEffect: TEffect, TMessage, TModel, TSubscription>(
        val init: (List<String>) -> Triple<TModel, List<TInstantEffect>, List<TLateEffect>>,
        val update: (TMessage, TModel) -> Triple<TModel, List<TInstantEffect>, List<TLateEffect>>,
        val subscriptions: (TModel) -> List<TSubscription>,
)

data class ProgramRunnerConfig<
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage, TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState>(
        val initRunner: (List<String>) -> TRunnerModel,
        val processInstantEffect: (TRunnerModel, TInstantEffect) -> Pair<TRunnerModel, Maybe<TMessage>>,
        val processLateEffect: suspend (TRunnerModel, TLateEffect) -> (TRunnerModel) -> Pair<TRunnerModel, Maybe<TMessage>>,
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
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage, TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState>(
        val runnerConfig:
                ProgramRunnerConfig<
                        TEffect,
                        TInstantEffect,
                        TLateEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>,
        val programConfig: ProgramConfig<TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TSubscription>,
        val pendingMessages: List<TMessage>,
        val pendingInstantEffects: List<TInstantEffect>,
        val pendingLateEffects: List<TLateEffect>,
        val runningLateEffects: List<Deferred<(TRunnerModel) -> Pair<TRunnerModel, Maybe<TMessage>>>>,
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
