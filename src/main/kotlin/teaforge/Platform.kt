package teaforge.platform

import kotlinx.coroutines.CoroutineScope
import teaforge.LoggerStatus
import teaforge.ProgramConfig
import teaforge.ProgramRunnerConfig
import teaforge.ProgramRunnerInstance
import teaforge.debugger.TeaSerializer
import teaforge.internal.activateOrDeactivateSubscriptions
import teaforge.internal.collectCompletedEffects
import teaforge.internal.processMessages
import teaforge.internal.processPendingEffects
import teaforge.internal.updateSubscriptions

fun <TEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> initRunner(
    runnerConfig: ProgramRunnerConfig<
        TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState,
        >,
    runnerArgs: List<String>,
    program: ProgramConfig<TEffect, TMessage, TProgramModel, TSubscription>,
    programArgs: List<String>,
): ProgramRunnerInstance<
    TEffect,
    TMessage,
    TProgramModel,
    TRunnerModel,
    TSubscription,
    TSubscriptionState,
    > {
    val (initialProgramModel, initialEffects) = program.init(programArgs)

    when (val status = runnerConfig.loggerStatus()) {
        is LoggerStatus.Disabled -> {}
        is LoggerStatus.Enabled -> {
            val session = status.session
            val logging = session.config
            val timestamp = logging.getTimestamp()

            session.writeHeaderIfNeeded()

            val modelValue = TeaSerializer.serialize(initialProgramModel)
            val effectValues = initialEffects.map { TeaSerializer.serialize(it) }

            if (logging.compressionEnabled) {
                val modelJson = modelValue.toCompressedJsonString(session.dictionary)
                val effectsJson =
                    effectValues.joinToString(",") {
                        it.toCompressedJsonString(session.dictionary)
                    }
                session.emitPendingDictionaryDefinitions()
                val json =
                    """{"type":"init","timestamp":$timestamp,"model":$modelJson,"effects":[$effectsJson]}"""
                logging.log(json)
            } else {
                val modelJson = modelValue.toJsonString()
                val effectsJson = effectValues.joinToString(",") { it.toJsonString() }
                val json =
                    """{"type":"init","timestamp":$timestamp,"model":$modelJson,"effects":[$effectsJson]}"""
                logging.log(json)
            }
        }
    }

    val runner =
        ProgramRunnerInstance(
            runnerConfig = runnerConfig,
            programConfig = program,
            programModel = initialProgramModel,
            pendingMessages = emptyList(),
            pendingEffects = initialEffects,
            subscriptions = emptyMap(),
            runnerModel = runnerConfig.initRunner(runnerArgs),
            inFlightEffects = emptyList(),
        )

    return updateSubscriptions(runner)
}

fun <TEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> stepProgram(
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
    val runnerAfterStart =
        programRunner.copy(
            runnerModel =
                programRunner.runnerConfig.startOfUpdateCycle(
                    programRunner.runnerModel,
                ),
        )

    val runnerAfterCollectingCompletedEffects =
        collectCompletedEffects(runnerAfterStart)

    val runnerAfterProcessingSubscriptionAdditionsAndRemovals =
        activateOrDeactivateSubscriptions(runnerAfterCollectingCompletedEffects)

    val runnerAfterUpdatingSubscriptions:
        ProgramRunnerInstance<
            TEffect,
            TMessage,
            TProgramModel,
            TRunnerModel,
            TSubscription,
            TSubscriptionState,
            > =
        updateSubscriptions(runnerAfterProcessingSubscriptionAdditionsAndRemovals)

    val runnerAfterProcessingMessages:
        ProgramRunnerInstance<
            TEffect,
            TMessage,
            TProgramModel,
            TRunnerModel,
            TSubscription,
            TSubscriptionState,
            > =
        processMessages(programRunner.programConfig, runnerAfterUpdatingSubscriptions)

    val runnerAfterProcessingEffects = processPendingEffects(runnerAfterProcessingMessages, scope)

    val finalRunnerModel =
        programRunner.runnerConfig.endOfUpdateCycle(
            runnerAfterProcessingEffects.runnerModel,
        )

    return runnerAfterProcessingEffects.copy(runnerModel = finalRunnerModel)
}
