package teaforge.platform

import kotlinx.coroutines.CoroutineScope
import teaforge.ProgramConfig
import teaforge.ProgramRunnerConfig
import teaforge.ProgramRunnerInstance
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
