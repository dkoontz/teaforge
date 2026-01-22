import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import teaforge.*
import teaforge.platform.initRunner
import teaforge.platform.stepProgram
import teaforge.utils.Maybe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncEffectTest {

    sealed interface TestEffect {
        data class SyncEffect(val value: Int) : TestEffect
        data class AsyncEffect(val id: String, val delayMs: Long) : TestEffect
    }

    sealed interface TestMessage {
        data class EffectCompleted(val id: String, val runnerCounterAtCompletion: Int) : TestMessage
        data class SyncEffectDone(val value: Int) : TestMessage
    }

    data class ProgramModel(
        val completedEffects: List<String> = emptyList(),
        val syncValues: List<Int> = emptyList(),
        val lastCompletionCounterValue: Int? = null
    )

    data class RunnerModel(
        val counter: Int = 0,
        val asyncInFlight: Set<String> = emptySet()
    )

    data class SubState(val unit: Unit = Unit)

    private val programConfig = ProgramConfig<TestEffect, TestMessage, ProgramModel, Nothing>(
        init = { _ -> Pair(ProgramModel(), emptyList()) },
        update = { message, model ->
            when (message) {
                is TestMessage.EffectCompleted -> Pair(
                    model.copy(
                        completedEffects = model.completedEffects + message.id,
                        lastCompletionCounterValue = message.runnerCounterAtCompletion
                    ),
                    emptyList()
                )
                is TestMessage.SyncEffectDone -> Pair(
                    model.copy(syncValues = model.syncValues + message.value),
                    emptyList()
                )
            }
        },
        subscriptions = { _ -> emptyList() }
    )

    private fun createRunnerConfig(
        asyncCompletions: Map<String, CompletableDeferred<Unit>> = emptyMap()
    ) = ProgramRunnerConfig<TestEffect, TestMessage, ProgramModel, RunnerModel, Nothing, SubState>(
        initRunner = { _ -> RunnerModel() },
        processEffect = { model, effect ->
            when (effect) {
                is TestEffect.SyncEffect -> EffectResult.Sync(
                    updatedModel = model,
                    message = Maybe.Some(TestMessage.SyncEffectDone(effect.value))
                )
                is TestEffect.AsyncEffect -> EffectResult.Async(
                    updatedModel = model.copy(
                        asyncInFlight = model.asyncInFlight + effect.id
                    ),
                    completion = {
                        asyncCompletions[effect.id]?.await()
                        if (effect.delayMs > 0) {
                            delay(effect.delayMs)
                        }
                        { currentModel: RunnerModel ->
                            Pair(
                                currentModel.copy(
                                    asyncInFlight = currentModel.asyncInFlight - effect.id,
                                    counter = currentModel.counter + 1
                                ),
                                Maybe.Some(TestMessage.EffectCompleted(
                                    id = effect.id,
                                    runnerCounterAtCompletion = currentModel.counter
                                ))
                            )
                        }
                    }
                )
            }
        },
        processSubscription = { model, state -> Triple(model, state, Maybe.None) },
        startSubscription = { model, _ -> Pair(model, SubState()) },
        stopSubscription = { model, _ -> model },
        startOfUpdateCycle = { model -> model },
        endOfUpdateCycle = { model -> model },
        processHistoryEntry = { model, _ -> model }
    )

    @Test
    fun `sync effect completes immediately and message is queued`() = runTest {
        val runnerConfig = createRunnerConfig()

        val programWithEffect = programConfig.copy(
            init = { _ -> Pair(ProgramModel(), listOf(TestEffect.SyncEffect(42))) }
        )

        val runner = initRunner(
            runnerConfig = runnerConfig,
            runnerArgs = emptyList(),
            program = programWithEffect,
            programArgs = emptyList()
        )

        // First step processes effects, messages are queued
        val afterFirstStep = stepProgram(runner, this)
        assertTrue(afterFirstStep.inFlightEffects.isEmpty())
        assertEquals(1, afterFirstStep.pendingMessages.size)

        // Second step processes messages, updating program model
        val afterSecondStep = stepProgram(afterFirstStep, this)
        assertEquals(listOf(42), afterSecondStep.programModel.syncValues)
    }

    @Test
    fun `async effect immediate model update applies`() = runTest {
        val asyncCompletion = CompletableDeferred<Unit>()
        val runnerConfig = createRunnerConfig(mapOf("async1" to asyncCompletion))

        val programWithEffect = programConfig.copy(
            init = { _ -> Pair(ProgramModel(), listOf(TestEffect.AsyncEffect("async1", 0))) }
        )

        val runner = initRunner(
            runnerConfig = runnerConfig,
            runnerArgs = emptyList(),
            program = programWithEffect,
            programArgs = emptyList()
        )

        val afterStep = stepProgram(runner, this)
        advanceUntilIdle()

        // Immediate model update should have been applied
        assertTrue(afterStep.runnerModel.asyncInFlight.contains("async1"))
        assertEquals(1, afterStep.inFlightEffects.size)
        assertEquals("async1", (afterStep.inFlightEffects[0].effect as TestEffect.AsyncEffect).id)

        asyncCompletion.complete(Unit)
    }

    @Test
    fun `async effect completion function is given the current model not a stale model`() = runTest {
        val asyncCompletion = CompletableDeferred<Unit>()
        val runnerConfig = createRunnerConfig(mapOf("async1" to asyncCompletion))

        val programWithEffect = programConfig.copy(
            init = { _ -> Pair(ProgramModel(), listOf(TestEffect.AsyncEffect("async1", 0))) }
        )

        val runner = initRunner(
            runnerConfig = runnerConfig,
            runnerArgs = emptyList(),
            program = programWithEffect,
            programArgs = emptyList()
        )

        // First step launches the async effect
        val afterFirstStep = stepProgram(runner, this)
        advanceUntilIdle()
        assertEquals(0, afterFirstStep.runnerModel.counter)

        // Simulate external modification to runner model (e.g., from another source)
        val modifiedRunner = afterFirstStep.copy(
            runnerModel = afterFirstStep.runnerModel.copy(counter = 100)
        )

        // Complete the async effect and let it run
        asyncCompletion.complete(Unit)
        advanceUntilIdle()

        // Second step collects the completed effect
        // The completion function should receive the CURRENT model (counter=100)
        val afterSecondStep = stepProgram(modifiedRunner, this)

        // The completion captured the counter value at completion time (100)
        // and stored it in the program model via the message
        assertEquals(100, afterSecondStep.programModel.lastCompletionCounterValue)

        // Counter should now be 101 (100 + 1 from completion)
        assertEquals(101, afterSecondStep.runnerModel.counter)

        // Effect should be marked as completed
        assertEquals(listOf("async1"), afterSecondStep.programModel.completedEffects)
    }

    @Test
    fun `multiple async effects completing in different order`() = runTest {
        val async1Completion = CompletableDeferred<Unit>()
        val async2Completion = CompletableDeferred<Unit>()
        val runnerConfig = createRunnerConfig(mapOf(
            "async1" to async1Completion,
            "async2" to async2Completion
        ))

        val programWithEffects = programConfig.copy(
            init = { _ -> Pair(
                ProgramModel(),
                listOf(
                    TestEffect.AsyncEffect("async1", 0),
                    TestEffect.AsyncEffect("async2", 0)
                )
            )}
        )

        val runner = initRunner(
            runnerConfig = runnerConfig,
            runnerArgs = emptyList(),
            program = programWithEffects,
            programArgs = emptyList()
        )

        // First step launches both async effects
        val afterFirstStep = stepProgram(runner, this)
        advanceUntilIdle()
        assertEquals(2, afterFirstStep.inFlightEffects.size)
        assertTrue(afterFirstStep.runnerModel.asyncInFlight.containsAll(setOf("async1", "async2")))

        // Complete async2 first (out of order)
        async2Completion.complete(Unit)
        advanceUntilIdle()

        // Second step collects async2's completion
        val afterSecondStep = stepProgram(afterFirstStep, this)
        assertEquals(1, afterSecondStep.inFlightEffects.size)
        assertEquals("async1", (afterSecondStep.inFlightEffects[0].effect as TestEffect.AsyncEffect).id)
        assertEquals(1, afterSecondStep.runnerModel.counter)

        // Complete async1
        async1Completion.complete(Unit)
        advanceUntilIdle()

        // Third step collects async1's completion
        val afterThirdStep = stepProgram(afterSecondStep, this)
        assertEquals(0, afterThirdStep.inFlightEffects.size)
        assertTrue(afterThirdStep.runnerModel.asyncInFlight.isEmpty())
        assertEquals(2, afterThirdStep.runnerModel.counter)
    }

    @Test
    fun `mixed sync and async effects`() = runTest {
        val asyncCompletion = CompletableDeferred<Unit>()
        val runnerConfig = createRunnerConfig(mapOf("async1" to asyncCompletion))

        val programWithEffects = programConfig.copy(
            init = { _ -> Pair(
                ProgramModel(),
                listOf(
                    TestEffect.SyncEffect(10),
                    TestEffect.AsyncEffect("async1", 0),
                    TestEffect.SyncEffect(20)
                )
            )}
        )

        val runner = initRunner(
            runnerConfig = runnerConfig,
            runnerArgs = emptyList(),
            program = programWithEffects,
            programArgs = emptyList()
        )

        // First step processes all effects
        // Sync effects produce messages immediately, async effect goes to inFlight
        val afterFirstStep = stepProgram(runner, this)
        advanceUntilIdle()
        assertEquals(1, afterFirstStep.inFlightEffects.size)
        assertEquals(2, afterFirstStep.pendingMessages.size) // Two sync messages

        // Second step processes the sync messages
        val afterSecondStep = stepProgram(afterFirstStep, this)
        assertEquals(listOf(10, 20), afterSecondStep.programModel.syncValues)

        // Complete async effect
        asyncCompletion.complete(Unit)
        advanceUntilIdle()

        // Third step collects async completion and processes the resulting message
        // The message from the completion is processed in the same step
        val afterThirdStep = stepProgram(afterSecondStep, this)
        assertEquals(0, afterThirdStep.inFlightEffects.size)

        // The EffectCompleted message was processed by processMessages in the same step
        assertEquals(listOf("async1"), afterThirdStep.programModel.completedEffects)
    }
}
