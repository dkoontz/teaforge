package teaforge.internal

import teaforge.HistoryEntry
import teaforge.HistoryEventSource
import teaforge.ProgramConfig
import teaforge.ProgramRunnerInstance
import teaforge.utils.Maybe

fun <
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> processMessages(
        program: ProgramConfig<TEffect, TMessage, TProgramModel, TSubscription>,
        programRunner:
                ProgramRunnerInstance<
                        TEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
        val initial: Triple<TRunnerModel, TProgramModel, List<TEffect>> =
                Triple(programRunner.runnerModel, programRunner.programModel, emptyList())

        val (
                finalRunnerModel,
                finalProgramModel,
                effects,
        ) = programRunner.pendingMessages.fold(
                initial = initial,
                operation = { acc, message ->
                        val (runnerModel, programModel, currentEffects) = acc
                        val (updatedProgramModel, newEffects) =
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

                        Triple(updatedRunnerModel, updatedProgramModel, currentEffects + newEffects)
                }
        )

        return programRunner.copy(
                runnerModel = finalRunnerModel,
                programModel = finalProgramModel,
                pendingMessages = emptyList(),
                pendingEffects = programRunner.pendingEffects + effects,
        )
}

fun <
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> activateOrDeactivateSubscriptions(
        programRunner:
                ProgramRunnerInstance<
                        TEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
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
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> updateSubscriptions(
        programRunner:
                ProgramRunnerInstance<
                        TEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
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
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> processPendingEffects(
        programRunner:
                ProgramRunnerInstance<
                        TEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>
): ProgramRunnerInstance<
        TEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {
        val (finalModel, messages) =
                programRunner.pendingEffects.fold(
                        initial =
                                Pair<TRunnerModel, List<TMessage>>(
                                        programRunner.runnerModel,
                                        emptyList()
                                ),
                        operation = { acc, effect ->
                                val (model, messages) = acc

                                val (updatedModel, message) =
                                        programRunner.runnerConfig.processEffect(model, effect)

                                when (message) {
                                        is Maybe.None -> Pair(updatedModel, messages)
                                        is Maybe.Some ->
                                                Pair(updatedModel, messages + message.value)
                                }
                        }
                )

        return programRunner.copy(
                runnerModel = finalModel,
                pendingEffects = emptyList(),
                pendingMessages = programRunner.pendingMessages + messages,
        )
}
