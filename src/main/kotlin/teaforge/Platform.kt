package teaforge.platform

import kotlinx.coroutines.CoroutineScope
import teaforge.*
import teaforge.internal.*

fun <
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> initRunner(
        runnerConfig:
                ProgramRunnerConfig<
                        TEffect,
                        TInstantEffect,
                        TLateEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState>,
        runnerArgs: List<String>,
        program: ProgramConfig<TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TSubscription>,
        programArgs: List<String>
): ProgramRunnerInstance<
        TEffect, TInstantEffect, TLateEffect, TMessage, TProgramModel, TRunnerModel, TSubscription, TSubscriptionState> {

        val (initialProgramModel, initialInstantEffects, initialLateEffects) = program.init(programArgs)

        val runner =
                ProgramRunnerInstance(
                        runnerConfig = runnerConfig,
                        programConfig = program,
                        programModel = initialProgramModel,
                        pendingMessages = emptyList(),
                        pendingInstantEffects = initialInstantEffects,
                        pendingLateEffects = initialLateEffects,
                        runningLateEffects = emptyList(),

                        subscriptions = emptyMap(),
                        runnerModel = runnerConfig.initRunner(runnerArgs),
                )

        return updateSubscriptions(runner)
}

fun <
        TEffect,
        TInstantEffect: TEffect,
        TLateEffect: TEffect,
        TMessage,
        TProgramModel,
        TRunnerModel,
        TSubscription,
        TSubscriptionState> stepProgram(
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
        val runnerAfterStart =
                programRunner.copy(
                        runnerModel =
                                programRunner.runnerConfig.startOfUpdateCycle(
                                        programRunner.runnerModel
                                )
                )

        val runnerAfterProcessingSubscriptionAdditionsAndRemovals =
                activateOrDeactivateSubscriptions(runnerAfterStart)

        val runnerAfterUpdatingSubscriptions:
                ProgramRunnerInstance<
                        TEffect,
                        TInstantEffect,
                        TLateEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState> =
                updateSubscriptions(runnerAfterProcessingSubscriptionAdditionsAndRemovals)

        val runnerAfterProcessingMessages:
                ProgramRunnerInstance<
                        TEffect,
                        TInstantEffect,
                        TLateEffect,
                        TMessage,
                        TProgramModel,
                        TRunnerModel,
                        TSubscription,
                        TSubscriptionState> =
                processMessages(programRunner.programConfig, runnerAfterUpdatingSubscriptions)

        val runnerAfterProcessingInstantEffects = processPendingInstantEffects(runnerAfterProcessingMessages)
        val runnerAfterProcessingLateEffects = processPendingLateEffects(scope, runnerAfterProcessingInstantEffects)
        val runnerAfterRunningLateEffects = updateRunningLateEffects(runnerAfterProcessingLateEffects)

        val finalRunnerModel =
                programRunner.runnerConfig.endOfUpdateCycle(
                        runnerAfterRunningLateEffects.runnerModel
                )

        return runnerAfterRunningLateEffects.copy(runnerModel = finalRunnerModel)
}
