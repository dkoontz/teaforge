package teaforge.internal

import teaforge.HistoryEntry
import teaforge.HistoryEventSource
import teaforge.ProgramConfig
import teaforge.ProgramRunnerInstance
import teaforge.TaggedEffect
import teaforge.TaggedSubscription
import teaforge.utils.Maybe

fun <
    TMessage,
    TProgramModel,
    TRunnerModel,
> processMessages(
    program: ProgramConfig<TMessage, TProgramModel>,
    programRunner: ProgramRunnerInstance<
        TMessage,
        TProgramModel,
        TRunnerModel,
    >,
): ProgramRunnerInstance<
    TMessage,
    TProgramModel,
    TRunnerModel,
> {
    val initial: Triple<TRunnerModel, TProgramModel, List<TaggedEffect>> =
        Triple(programRunner.runnerModel, programRunner.programModel, emptyList())

    val (
        finalRunnerModel,
        finalProgramModel,
        effects,
    ) =
        programRunner.pendingMessages.fold(
            initial = initial,
            operation = { acc, message ->
                val (runnerModel, programModel, currentEffects) = acc
                val (updatedProgramModel, newEffects) =
                    program.update(message, programModel)
                val historyEntry =
                    HistoryEntry(
                        source = HistoryEventSource.ProgramMessage(message),
                        programModelAfterEvent = updatedProgramModel,
                    )
                val updatedRunnerModel =
                    programRunner.platformConfig.processHistoryEntry(
                        runnerModel,
                        historyEntry,
                    )

                Triple(updatedRunnerModel, updatedProgramModel, currentEffects + newEffects)
            },
        )

    return programRunner.copy(
        runnerModel = finalRunnerModel,
        programModel = finalProgramModel,
        pendingMessages = emptyList(),
        pendingEffects = programRunner.pendingEffects + effects,
    )
}

fun <
    TMessage,
    TProgramModel,
    TRunnerModel,
> activateOrDeactivateSubscriptions(
    programRunner: ProgramRunnerInstance<
        TMessage,
        TProgramModel,
        TRunnerModel,
    >,
): ProgramRunnerInstance<
    TMessage,
    TProgramModel,
    TRunnerModel,
> {
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
            removedSubscriptions.entries.fold(
                initial = programRunner.runnerModel,
                operation = { runnerModel, entry ->
                    val (subscription, subscriptionState) = entry.toPair()
                    programRunner.capabilities
                        .firstOrNull { it.canHandleSubscription(subscription) }
                        ?.stopSubscription(subscriptionState)
                    runnerModel
                },
            )

        val (runnerModelAfterProcessingSubscriptionAdditions, updatedSubscriptions) =
            newSubscriptions.fold(
                initial = runnerModelAfterProcessingSubscriptionRemovals to remainingSubscriptions,
                operation = { (runnerModel, subscriptions), newSubscription ->
                    val matchingCapability = programRunner.capabilities
                        .firstOrNull { it.canHandleSubscription(newSubscription) }
                    
                    if (matchingCapability != null) {
                        val initialSubscriptionState = matchingCapability.startSubscription(newSubscription)
                        runnerModel to (subscriptions + (newSubscription to initialSubscriptionState))
                    } else {
                        runnerModel to subscriptions
                    }
                },
            )
        return programRunner.copy(
            runnerModel = runnerModelAfterProcessingSubscriptionAdditions,
            subscriptions = updatedSubscriptions,
        )
    }
    return programRunner
}

fun <
    TMessage,
    TProgramModel,
    TRunnerModel,
> updateSubscriptions(
    programRunner: ProgramRunnerInstance<
        TMessage,
        TProgramModel,
        TRunnerModel,
    >,
): ProgramRunnerInstance<
    TMessage,
    TProgramModel,
    TRunnerModel,
> {
    val (finalModel, finalSubscriptions, messages) =
        programRunner
            .subscriptions
            .toList()
            .fold(
                initial = Triple(
                    programRunner.runnerModel,
                    programRunner.subscriptions,
                    emptyList<TMessage>()
                ),
                operation = { acc, (subscription, subscriptionState) ->
                    val (model, subscriptions, messages) = acc

                    val matchingCapability = programRunner.capabilities
                        .firstOrNull { it.canHandleSubscription(subscription) }
                    
                    val (updatedSubscriptionState, newMessage) = 
                        matchingCapability?.processSubscription(subscriptionState) 
                            ?: Pair(subscriptionState, Maybe.None)
                    
                    val updatedModel = model
                    val updatedSubscriptions = subscriptions + (subscription to updatedSubscriptionState)

                    when (newMessage) {
                        is Maybe.None ->
                            Triple(
                                updatedModel,
                                updatedSubscriptions,
                                messages,
                            )
                        is Maybe.Some -> {
                            @Suppress("UNCHECKED_CAST")
                            val typedMessage = newMessage.value as TMessage
                            Triple(
                                updatedModel,
                                updatedSubscriptions,
                                messages + typedMessage,
                            )
                        }
                    }
                },
            )

    return programRunner.copy(
        runnerModel = finalModel,
        subscriptions = finalSubscriptions,
        pendingMessages = programRunner.pendingMessages + messages,
    )
}

fun <
    TMessage,
    TProgramModel,
    TRunnerModel,
> processPendingEffects(
    programRunner: ProgramRunnerInstance<
        TMessage,
        TProgramModel,
        TRunnerModel,
    >,
): ProgramRunnerInstance<
    TMessage,
    TProgramModel,
    TRunnerModel,
> {
    val (finalModel, messages) =
        programRunner.pendingEffects.fold(
            initial = programRunner.runnerModel to emptyList<TMessage>(),
            operation = { acc, effect ->
                val (model, messages) = acc

                val matchingCapability = programRunner.capabilities
                    .firstOrNull { it.canHandleEffect(effect) }
                
                val message = matchingCapability?.processEffect(effect) ?: Maybe.None

                when (message) {
                    is Maybe.None -> Pair(model, messages)
                    is Maybe.Some -> {
                        @Suppress("UNCHECKED_CAST")
                        val typedMessage = message.value as TMessage
                        Pair(model, messages + typedMessage)
                    }
                }
            },
        )

    return programRunner.copy(
        runnerModel = finalModel,
        pendingEffects = emptyList(),
        pendingMessages = programRunner.pendingMessages + messages,
    )
}
