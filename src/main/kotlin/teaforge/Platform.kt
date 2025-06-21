package teaforge.platform

import teaforge.PlatformConfig
import teaforge.ProgramConfig
import teaforge.ProgramRunnerInstance
import teaforge.RegisteredCapability
import teaforge.internal.activateOrDeactivateSubscriptions
import teaforge.internal.processMessages
import teaforge.internal.processPendingEffects
import teaforge.internal.updateSubscriptions

fun <TMessage, TProgramModel, TRunnerModel> initRunner(
    platformConfig: PlatformConfig<TRunnerModel, TProgramModel, TMessage>,
    capabilities: List<RegisteredCapability>,
    runnerArgs: List<String>,
    program: ProgramConfig<TMessage, TProgramModel>,
    programArgs: List<String>,
): ProgramRunnerInstance<
    TMessage,
    TProgramModel,
    TRunnerModel,
> {
    val (initialProgramModel, initialEffects) = program.init(programArgs)

    val runner =
        ProgramRunnerInstance(
            platformConfig = platformConfig,
            capabilities = capabilities,
            programConfig = program,
            programModel = initialProgramModel,
            pendingMessages = emptyList(),
            pendingEffects = initialEffects,
            subscriptions = emptyMap(),
            runnerModel = platformConfig.initRunner(runnerArgs),
        )

    return updateSubscriptions(runner)
}

fun <TMessage, TProgramModel, TRunnerModel> stepProgram(
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
    val runnerAfterStart =
        programRunner.copy(
            runnerModel =
                programRunner.platformConfig.startOfUpdateCycle(
                    programRunner.runnerModel,
                ),
        )

    val runnerAfterProcessingSubscriptionAdditionsAndRemovals =
        activateOrDeactivateSubscriptions(runnerAfterStart)

    val runnerAfterUpdatingSubscriptions:
        ProgramRunnerInstance<
            TMessage,
            TProgramModel,
            TRunnerModel,
        > =
        updateSubscriptions(runnerAfterProcessingSubscriptionAdditionsAndRemovals)

    val runnerAfterProcessingMessages:
        ProgramRunnerInstance<
            TMessage,
            TProgramModel,
            TRunnerModel,
        > =
        processMessages(programRunner.programConfig, runnerAfterUpdatingSubscriptions)

    val runnerAfterProcessingEffects = processPendingEffects(runnerAfterProcessingMessages)

    val finalRunnerModel =
        programRunner.platformConfig.endOfUpdateCycle(
            runnerAfterProcessingEffects.runnerModel,
        )

    return runnerAfterProcessingEffects.copy(runnerModel = finalRunnerModel)
}
