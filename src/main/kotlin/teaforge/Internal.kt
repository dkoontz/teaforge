package teaforge.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import teaforge.EffectResult
import teaforge.InFlightEffect
import teaforge.LoggerStatus
import teaforge.ProgramConfig
import teaforge.ProgramRunnerInstance
import teaforge.SubscriptionIdentifier
import teaforge.debugger.JsonDiff
import teaforge.debugger.JsonValue
import teaforge.debugger.TeaSerializer
import teaforge.debugger.emitPendingDictionaryDefinitions
import teaforge.debugger.updateLastModel
import teaforge.utils.Maybe

fun <
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > processMessages(
    program: ProgramConfig<TEffect, TMessage, TProgramModel, TSubscription>,
    programRunner: ProgramRunnerInstance<
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState,
        >,
): ProgramRunnerInstance<
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > {
    val initial: Triple<TRunnerModel, TProgramModel, List<TEffect>> =
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

                when (val status = programRunner.runnerConfig.loggerStatus()) {
                    is LoggerStatus.Disabled -> {}
                    is LoggerStatus.Enabled -> {
                        val session = status.session
                        val logging = session.config
                        val timestamp = logging.getTimestamp()

                        val messageValue = TeaSerializer.serializeMessage(message as Any)
                        val newModelValue = TeaSerializer.serialize(updatedProgramModel)
                        val previousModel: JsonValue = session.lastModel
                        val diffOps = JsonDiff.diff(previousModel, newModelValue)
                        val effectValues = newEffects.map { TeaSerializer.serialize(it) }

                        if (logging.compressionEnabled) {
                            val messageJson = messageValue.toCompressedJsonString(session.dictionary)
                            val modelDiffJson =
                                diffOps.joinToString(",") { op ->
                                    teaforge.debugger.EntrySerializer.serializeDiffOperation(op)
                                        .toCompressedJsonString(session.dictionary)
                                }
                            val effectsJson =
                                effectValues.joinToString(",") {
                                    it.toCompressedJsonString(session.dictionary)
                                }
                            emitPendingDictionaryDefinitions(session)
                            val json =
                                """{"type":"update","timestamp":$timestamp,""" +
                                    """"message":$messageJson,"modelDiff":[$modelDiffJson],"effects":[$effectsJson]}"""
                            logging.log(json)
                        } else {
                            val messageJson = messageValue.toJsonString()
                            val modelDiffJson =
                                diffOps.joinToString(",") { op ->
                                    teaforge.debugger.EntrySerializer.serializeDiffOperation(op).toJsonString()
                                }
                            val effectsJson = effectValues.joinToString(",") { it.toJsonString() }
                            val json =
                                """{"type":"update","timestamp":$timestamp,""" +
                                    """"message":$messageJson,"modelDiff":[$modelDiffJson],"effects":[$effectsJson]}"""
                            logging.log(json)
                        }

                        status.session = updateLastModel(session, newModelValue)
                    }
                }

                Triple(runnerModel, updatedProgramModel, currentEffects + newEffects)
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
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > activateOrDeactivateSubscriptions(
    programRunner: ProgramRunnerInstance<
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState,
        >,
): ProgramRunnerInstance<
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > {
    val previousSubscriptions = programRunner.subscriptions
    val previousKeys = previousSubscriptions.keys

    val getId = programRunner.runnerConfig.getUniqueIdentifierForSubscription
    val currentSubscriptionList =
        programRunner.programConfig.subscriptions(programRunner.programModel)
    val currentById = currentSubscriptionList.associateBy { getId(it) }
    val currentKeys = currentById.keys

    if (currentKeys != previousKeys) {
        val addedKeys = currentKeys - previousKeys
        val removedKeys = previousKeys - currentKeys

        val newSubscriptions = addedKeys.map { currentById.getValue(it) }
        val removedSubscriptions = removedKeys.map { previousSubscriptions.getValue(it) }

        when (val status = programRunner.runnerConfig.loggerStatus()) {
            is LoggerStatus.Disabled -> {}
            is LoggerStatus.Enabled -> {
                val session = status.session
                val logging = session.config
                val timestamp = logging.getTimestamp()

                val startedValues = newSubscriptions.map { TeaSerializer.serialize(it) }
                val stoppedValues =
                    removedSubscriptions.map { (sub, _) -> TeaSerializer.serialize(sub) }

                if (logging.compressionEnabled) {
                    val startedJson =
                        startedValues.joinToString(",") {
                            it.toCompressedJsonString(session.dictionary)
                        }
                    val stoppedJson =
                        stoppedValues.joinToString(",") {
                            it.toCompressedJsonString(session.dictionary)
                        }
                    emitPendingDictionaryDefinitions(session)
                    val json =
                        """{"type":"subscriptionChange","timestamp":$timestamp,""" +
                            """"started":[$startedJson],"stopped":[$stoppedJson]}"""
                    logging.log(json)
                } else {
                    val startedJson = startedValues.joinToString(",") { it.toJsonString() }
                    val stoppedJson = stoppedValues.joinToString(",") { it.toJsonString() }
                    val json =
                        """{"type":"subscriptionChange","timestamp":$timestamp,""" +
                            """"started":[$startedJson],"stopped":[$stoppedJson]}"""
                    logging.log(json)
                }
            }
        }

        val remainingSubscriptions =
            previousSubscriptions.filterKeys { it in currentKeys }

        val runnerModelAfterProcessingSubscriptionRemovals =
            removedSubscriptions.fold(
                initial = programRunner.runnerModel,
                operation = { runnerModel, (_, subscriptionState) ->
                    programRunner.runnerConfig.stopSubscription(
                        runnerModel,
                        subscriptionState,
                    )
                },
            )

        val (runnerModelAfterProcessingSubscriptionAdditions, updatedSubscriptions) =
            newSubscriptions.fold(
                initial =
                    Pair(
                        runnerModelAfterProcessingSubscriptionRemovals,
                        remainingSubscriptions,
                    ),
                operation = { (runnerModel, subscriptions), newSubscription ->
                    val (updatedRunner, initialSubscriptionState) =
                        programRunner.runnerConfig.startSubscription(
                            runnerModel,
                            newSubscription,
                        )
                    Pair(
                        updatedRunner,
                        subscriptions +
                            (
                                getId(newSubscription) to
                                    Pair(newSubscription, initialSubscriptionState)
                            ),
                    )
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
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > updateSubscriptions(
    programRunner: ProgramRunnerInstance<
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState,
        >,
): ProgramRunnerInstance<
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > {
    val (finalModel, finalSubscriptions, messages) =
        programRunner
            .subscriptions
            .toList()
            .fold(
                initial =
                    Triple<
                        TRunnerModel,
                        Map<SubscriptionIdentifier, Pair<TSubscription, TSubscriptionState>>,
                        List<TMessage>,
                        >(
                        programRunner.runnerModel,
                        programRunner.subscriptions,
                        emptyList(),
                    ),
                operation = { acc, (key, subscriptionPair) ->
                    val (model, subscriptions, messages) = acc
                    val (subscription, subscriptionState) = subscriptionPair

                    val (updatedModel, updatedSubscriptionState, newMessage) =
                        programRunner.runnerConfig.processSubscription(
                            model,
                            subscriptionState,
                        )

                    val updatedSubscriptions =
                        subscriptions +
                            (key to Pair(subscription, updatedSubscriptionState))

                    when (newMessage) {
                        is Maybe.None ->
                            Triple(
                                updatedModel,
                                updatedSubscriptions,
                                messages,
                            )
                        is Maybe.Some ->
                            Triple(
                                updatedModel,
                                updatedSubscriptions,
                                messages + newMessage.value,
                            )
                    }
                },
            )

    return programRunner.copy(
        runnerModel = finalModel,
        subscriptions = finalSubscriptions,
        pendingMessages = programRunner.pendingMessages + messages,
    )
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun <
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > collectCompletedEffects(
    programRunner: ProgramRunnerInstance<
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState,
        >,
): ProgramRunnerInstance<
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > {
    val (completedEffects, stillInFlight) =
        programRunner.inFlightEffects.partition {
            it.asyncProcess.isCompleted
        }

    if (completedEffects.isEmpty()) {
        return programRunner
    }

    val (finalModel, messages) =
        completedEffects.fold(
            initial = Pair<TRunnerModel, List<TMessage>>(programRunner.runnerModel, emptyList()),
            operation = { acc, inFlightEffect ->
                val (model, messages) = acc
                val completionFunction = inFlightEffect.asyncProcess.getCompleted()
                val (updatedModel, message) = completionFunction(model)

                when (message) {
                    is Maybe.None -> Pair(updatedModel, messages)
                    is Maybe.Some -> Pair(updatedModel, messages + message.value)
                }
            },
        )

    return programRunner.copy(
        runnerModel = finalModel,
        inFlightEffects = stillInFlight,
        pendingMessages = programRunner.pendingMessages + messages,
    )
}

fun <
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > processPendingEffects(
    programRunner: ProgramRunnerInstance<
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState,
        >,
    scope: CoroutineScope,
): ProgramRunnerInstance<
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > {
    data class EffectEvaluationState(
        val model: TRunnerModel,
        val messages: List<TMessage>,
        val newInFlightEffects: List<InFlightEffect<TEffect, TRunnerModel, TMessage>>,
    )

    val initialState =
        EffectEvaluationState(
            model = programRunner.runnerModel,
            messages = emptyList(),
            newInFlightEffects = emptyList(),
        )

    val finalState =
        programRunner.pendingEffects.fold(
            initial = initialState,
            operation = { acc, effect ->
                val result = programRunner.runnerConfig.processEffect(acc.model, effect)

                when (result) {
                    is EffectResult.Sync -> {
                        when (result.message) {
                            is Maybe.None -> acc.copy(model = result.updatedModel)
                            is Maybe.Some ->
                                acc.copy(
                                    model = result.updatedModel,
                                    messages = acc.messages + result.message.value,
                                )
                        }
                    }
                    is EffectResult.Async -> {
                        val deferred = scope.async { result.completion() }
                        val inFlightEffect =
                            InFlightEffect(
                                effect = effect,
                                asyncProcess = deferred,
                            )
                        acc.copy(
                            model = result.updatedModel,
                            newInFlightEffects = acc.newInFlightEffects + inFlightEffect,
                        )
                    }
                }
            },
        )

    return programRunner.copy(
        runnerModel = finalState.model,
        pendingEffects = emptyList(),
        pendingMessages = programRunner.pendingMessages + finalState.messages,
        inFlightEffects = programRunner.inFlightEffects + finalState.newInFlightEffects,
    )
}
