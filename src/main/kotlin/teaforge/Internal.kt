package teaforge.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import teaforge.HistoryEntry
import teaforge.HistoryEventSource
import teaforge.ProgramConfig
import teaforge.ProgramRunnerInstance
import teaforge.utils.Maybe
import teaforge.utils.unwrap

fun <
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> processMessages(
        program: ProgramConfig<TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TSubscription>,
        programRunner:
                ProgramRunnerInstance<
                        TEffect,
                        TInstantEffect,
                        TLateEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
        val initial: Triple<TRunnerModel, TProgramModel, Pair<List<TInstantEffect>, List<TLateEffect>>> =
                Triple(programRunner.runnerModel, programRunner.programModel, Pair(emptyList(), emptyList()))

        val (finalRunnerModel, finalProgramModel, effects) = programRunner.pendingMessages.fold(
                initial = initial,
                operation = { acc, message ->
                        val (runnerModel, programModel, currentEffects) = acc
                        val (updatedProgramModel, newInstantEffects, newLateEffects) =
                                program.update(message, programModel)
                        val historyEntry =
                                HistoryEntry(
                                        source = HistoryEventSource.ProgramMessage(message),
                                        programModelAfterEvent = updatedProgramModel
                                )
                        val updatedRunnerModel =
                                programRunner.runnerConfig.processHistoryEntry(
                                        runnerModel,
                                        historyEntry
                                )
                        val finalInstantEffects = currentEffects.first + newInstantEffects
                        val finalLateEffects = currentEffects.second + newLateEffects
                        Triple(updatedRunnerModel, updatedProgramModel, Pair(finalInstantEffects, finalLateEffects))
                }
        )

        return programRunner.copy(
                runnerModel = finalRunnerModel,
                programModel = finalProgramModel,
                pendingMessages = emptyList(),
                pendingInstantEffects = programRunner.pendingInstantEffects + effects.first,
                pendingLateEffects = programRunner.pendingLateEffects + effects.second,
        )
}

fun <
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> activateOrDeactivateSubscriptions(
        programRunner:
                ProgramRunnerInstance<
                        TEffect,
                        TInstantEffect,
                        TLateEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
        val previousSubscriptions = programRunner.subscriptions
        val previousSubscriptionKeys = previousSubscriptions.keys

        val currentSubscriptions =
                programRunner.programConfig.subscriptions(programRunner.programModel)

        if (currentSubscriptions != previousSubscriptionKeys) {
                val newSubscriptions = currentSubscriptions - previousSubscriptionKeys

                val removedSubscriptions =
                        previousSubscriptions.filterKeys { it !in currentSubscriptions }

                val remainingSubscriptions =
                        previousSubscriptions.filterKeys { it in currentSubscriptions }

                val runnerModelAfterProcessingSubscriptionRemovals =
                        removedSubscriptions.values.fold(
                                initial = programRunner.runnerModel,
                                operation = { runnerModel, subscriptionState ->
                                        programRunner.runnerConfig.stopSubscription(
                                                runnerModel,
                                                subscriptionState,
                                        )
                                }
                        )

                val (runnerModelAfterProcessingSubscriptionAdditions, updatedSubscriptions) =
                        newSubscriptions.fold(
                                initial =
                                        Pair(
                                                runnerModelAfterProcessingSubscriptionRemovals,
                                                remainingSubscriptions
                                        ),
                                operation = { (runnerModel, subscriptions), newSubscription ->
                                        val (updatedRunner, initialSubscriptionState) =
                                                programRunner.runnerConfig.startSubscription(
                                                        runnerModel,
                                                        newSubscription
                                                )
                                        Pair(
                                                updatedRunner,
                                                subscriptions +
                                                        (newSubscription to
                                                                initialSubscriptionState)
                                        )
                                }
                        )
                return programRunner.copy(
                        runnerModel = runnerModelAfterProcessingSubscriptionAdditions,
                        subscriptions = updatedSubscriptions,
                )
        }
        return programRunner
}

fun <
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> updateSubscriptions(
        programRunner:
                ProgramRunnerInstance<
                        TEffect,
                        TInstantEffect,
                        TLateEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
        val (finalModel, finalSubscriptions, messages) =
                programRunner
                        .subscriptions
                        .toList()
                        .fold(
                                initial =
                                        Triple<
                                                TRunnerModel,
                                                Map<TSubscription, TSubscriptionState>,
                                                List<TMessage>>(
                                                programRunner.runnerModel,
                                                programRunner.subscriptions,
                                                emptyList()
                                        ),
                                operation = { acc, (subscription, subscriptionState) ->
                                        val (model, subscriptions, messages) = acc

                                        val (updatedModel, updatedSubscriptionState, newMessage) =
                                                programRunner.runnerConfig.processSubscription(
                                                        model,
                                                        subscriptionState,
                                                )

                                        val updatedSubscriptions =
                                                subscriptions +
                                                        (subscription to updatedSubscriptionState)

                                        when (newMessage) {
                                                is Maybe.None ->
                                                        Triple(
                                                                updatedModel,
                                                                updatedSubscriptions,
                                                                messages
                                                        )
                                                is Maybe.Some ->
                                                        Triple(
                                                                updatedModel,
                                                                updatedSubscriptions,
                                                                messages + newMessage.value
                                                        )
                                        }
                                }
                        )

        return programRunner.copy(
                runnerModel = finalModel,
                subscriptions = finalSubscriptions,
                pendingMessages = programRunner.pendingMessages + messages,
        )
}

fun <
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> processPendingInstantEffects(
        programRunner:
        ProgramRunnerInstance<
                TEffect,
                TInstantEffect,
                TLateEffect,
                TMessage,
                TProgramModel,
                TRunnerModel,
                TSubscription,
                TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
        val (finalModel, messages) =
                programRunner.pendingInstantEffects.fold(
                        initial =
                                Pair<TRunnerModel, List<TMessage>>(
                                        programRunner.runnerModel,
                                        emptyList()
                                ),
                        operation = { acc, effect ->
                                val (model, messages) = acc

                                val (updatedModel, message) =
                                        programRunner.runnerConfig.processInstantEffect(model, effect)

                                when (message) {
                                        is Maybe.None -> Pair(updatedModel, messages)
                                        is Maybe.Some ->
                                                Pair(updatedModel, messages + message.value)
                                }
                        }
                )

        return programRunner.copy(
                runnerModel = finalModel,
                pendingInstantEffects = emptyList(),
                pendingMessages = programRunner.pendingMessages + messages,
        )
}

fun <
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> processPendingLateEffects(
        scope: CoroutineScope,
        programRunner:
                ProgramRunnerInstance<
                        TEffect,
                        TInstantEffect,
                        TLateEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
        val (finalModel, lateEffects) =
                programRunner.pendingLateEffects.fold(
                        initial =
                                Pair<TRunnerModel, List<Deferred<(TRunnerModel) -> Pair<TRunnerModel, Maybe<TMessage>>>>>(
                                        programRunner.runnerModel,
                                        emptyList()
                                ),
                        operation = { acc, effect ->
                                val (model, lateEffects) = acc

                                val job = scope.async(Dispatchers.Default) {
                                        programRunner.runnerConfig.processLateEffect(model, effect)
                                }
                                model to lateEffects + job
                        }
                )

        return programRunner.copy(
                runnerModel = finalModel,
                pendingLateEffects = emptyList(),
                runningLateEffects = programRunner.runningLateEffects + lateEffects,
        )
}


fun <
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> updateRunningLateEffects(
        programRunner: ProgramRunnerInstance<
                TEffect,
                TInstantEffect,
                TLateEffect,
                TMessage,
                TProgramModel,
                TRunnerModel,
                TSubscription,
                TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {

        val (finalModel, finalMessages, finalLateEffects) = programRunner.runningLateEffects.fold(
                initial = Triple(
                        programRunner.runnerModel,
                        programRunner.pendingMessages,
                        programRunner.runningLateEffects
                ),
                operation = { (model, messages, lateEffectJobs), lateEffectJob ->

                        getCompletedJob(lateEffectJob).unwrap(
                                default = Triple(model, messages, lateEffectJobs),
                                fn = { lateEffect ->
                                        val (newModel, message) = lateEffect(model)
                                        val updatedMessages = message.unwrap(
                                                default = messages,
                                                fn = { unwrapped -> messages + unwrapped }
                                        )
                                        val updatedLateEffects = lateEffectJobs - lateEffectJob
                                        Triple(newModel, updatedMessages, updatedLateEffects)
                                }
                        )
                }
        )

        return programRunner.copy(
                runnerModel = finalModel,
                pendingMessages = finalMessages,
                runningLateEffects = finalLateEffects
        )
}


@OptIn(ExperimentalCoroutinesApi::class)
fun <T> getCompletedJob(job: Deferred<T>) : Maybe<T> =
        if (job.isCompleted) Maybe.Some(job.getCompleted()) else Maybe.None