import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import teaforge.DebugLoggingConfig
import teaforge.EffectResult
import teaforge.LoggerStatus
import teaforge.ProgramConfig
import teaforge.ProgramRunnerConfig
import teaforge.SubscriptionIdentifier
import teaforge.debugger.LoggingSession
import teaforge.platform.initRunner
import teaforge.platform.stepProgram
import teaforge.utils.Maybe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebugLoggingDiffTest {
    // ---- Simple TEA program for testing ----

    data class TestModel(
        val count: Int = 0,
        val name: String = "default",
    )

    sealed interface TestMessage {
        data class Increment(val by: Int) : TestMessage

        data object NoOp : TestMessage
    }

    sealed interface TestEffect

    private val programConfig =
        ProgramConfig<TestEffect, TestMessage, TestModel, Nothing>(
            init = { _ -> Pair(TestModel(), emptyList()) },
            update = { message, model ->
                when (message) {
                    is TestMessage.Increment -> Pair(model.copy(count = model.count + message.by), emptyList())
                    TestMessage.NoOp -> Pair(model, emptyList())
                }
            },
            subscriptions = { _ -> emptyList() },
        )

    private fun makeRunnerConfig(
        logs: MutableList<String>,
        compressionEnabled: Boolean = false,
        session: LoggingSession? = null,
    ): ProgramRunnerConfig<TestEffect, TestMessage, TestModel, Unit, Nothing, Unit> {
        val loggingSession =
            session ?: LoggingSession(
                DebugLoggingConfig(
                    getTimestamp = { 12345L },
                    log = { json -> logs.add(json) },
                    compressionEnabled = compressionEnabled,
                ),
            )
        val enabled = LoggerStatus.Enabled(loggingSession)
        return ProgramRunnerConfig(
            initRunner = { _ -> Unit },
            processEffect = { _, _ -> EffectResult.Sync(Unit, Maybe.None) },
            processSubscription = { _, state -> Triple(Unit, state, Maybe.None) },
            startSubscription = { _, _ -> Pair(Unit, Unit) },
            stopSubscription = { _, _ -> Unit },
            startOfUpdateCycle = { it },
            endOfUpdateCycle = { it },
            getUniqueIdentifierForSubscription = { SubscriptionIdentifier(it.toString()) },
            loggerStatus = { enabled },
        )
    }

    // ----- Non-compressed mode tests -----

    @Test
    fun `initRunner with logging enabled produces modelDiff entry with all add operations`() =
        runTest {
            val logs = mutableListOf<String>()
            val runnerConfig = makeRunnerConfig(logs)

            initRunner(
                runnerConfig = runnerConfig,
                runnerArgs = emptyList(),
                program = programConfig,
                programArgs = emptyList(),
            )

            assertEquals(1, logs.size)
            val line = logs[0]

            // Must use modelDiff not model
            assertTrue(line.contains("\"modelDiff\""), "Expected 'modelDiff' field in: $line")
            assertFalse(
                line.contains("\"model\":") && !line.contains("\"modelDiff\""),
                "Should not contain bare 'model' field",
            )

            // All ops must be "add"
            assertTrue(line.contains("\"op\":\"add\""), "Expected add operations in: $line")
            assertFalse(line.contains("\"op\":\"replace\""), "Should not have replace in init")
            assertFalse(line.contains("\"op\":\"remove\""), "Should not have remove in init")

            // Must contain the initial field paths (as JSON Pointer paths in the diff operations)
            assertTrue(line.contains("/count"), "Expected count field path in: $line")
            assertTrue(line.contains("/name"), "Expected name field path in: $line")
        }

    @Test
    fun `stepProgram after model-changing message produces modelDiff with replace operations`() =
        runTest {
            val logs = mutableListOf<String>()
            val session =
                LoggingSession(
                    DebugLoggingConfig(
                        getTimestamp = { 12345L },
                        log = { json -> logs.add(json) },
                        compressionEnabled = false,
                    ),
                )
            val runnerConfig = makeRunnerConfig(logs, compressionEnabled = false, session = session)

            var runner =
                initRunner(
                    runnerConfig = runnerConfig,
                    runnerArgs = emptyList(),
                    program = programConfig,
                    programArgs = emptyList(),
                )

            // Enqueue a message that changes the model
            runner = runner.copy(pendingMessages = listOf(TestMessage.Increment(5)))
            stepProgram(runner, this)

            // logs[0] = init, logs[1] = update
            assertEquals(2, logs.size)
            val updateLine = logs[1]

            assertTrue(updateLine.contains("\"modelDiff\""), "Expected 'modelDiff' in update: $updateLine")
            assertTrue(updateLine.contains("\"op\":\"replace\""), "Expected replace operation: $updateLine")
            assertTrue(updateLine.contains("\"type\":\"update\""), "Expected type=update: $updateLine")
        }

    @Test
    fun `stepProgram after no-op message produces empty modelDiff`() =
        runTest {
            val logs = mutableListOf<String>()
            val session =
                LoggingSession(
                    DebugLoggingConfig(
                        getTimestamp = { 12345L },
                        log = { json -> logs.add(json) },
                        compressionEnabled = false,
                    ),
                )
            val runnerConfig = makeRunnerConfig(logs, compressionEnabled = false, session = session)

            var runner =
                initRunner(
                    runnerConfig = runnerConfig,
                    runnerArgs = emptyList(),
                    program = programConfig,
                    programArgs = emptyList(),
                )

            // Enqueue a no-op message
            runner = runner.copy(pendingMessages = listOf(TestMessage.NoOp))
            stepProgram(runner, this)

            assertEquals(2, logs.size)
            val updateLine = logs[1]

            // modelDiff should be an empty array
            assertTrue(
                updateLine.contains("\"modelDiff\":[]"),
                "Expected empty modelDiff array for no-op: $updateLine",
            )
        }

    @Test
    fun `multiple sequential updates produce correct incremental diffs`() =
        runTest {
            val logs = mutableListOf<String>()
            val session =
                LoggingSession(
                    DebugLoggingConfig(
                        getTimestamp = { 12345L },
                        log = { json -> logs.add(json) },
                        compressionEnabled = false,
                    ),
                )
            val runnerConfig = makeRunnerConfig(logs, compressionEnabled = false, session = session)

            var runner =
                initRunner(
                    runnerConfig = runnerConfig,
                    runnerArgs = emptyList(),
                    program = programConfig,
                    programArgs = emptyList(),
                )

            // First update: count 0 -> 3
            runner = runner.copy(pendingMessages = listOf(TestMessage.Increment(3)))
            runner = stepProgram(runner, this)

            // Second update: count 3 -> 8
            runner = runner.copy(pendingMessages = listOf(TestMessage.Increment(5)))
            stepProgram(runner, this)

            // 3 total log lines: init + 2 updates
            assertEquals(3, logs.size)

            // Both updates should have replace operations (count changed each time)
            val update1 = logs[1]
            val update2 = logs[2]
            assertTrue(update1.contains("\"op\":\"replace\""), "First update should have replace: $update1")
            assertTrue(update2.contains("\"op\":\"replace\""), "Second update should have replace: $update2")
        }

    @Test
    fun `header entry has version 2`() =
        runTest {
            val logs = mutableListOf<String>()
            val session =
                LoggingSession(
                    DebugLoggingConfig(
                        getTimestamp = { 12345L },
                        log = { json -> logs.add(json) },
                        compressionEnabled = true,
                    ),
                )
            val runnerConfig = makeRunnerConfig(logs, compressionEnabled = true, session = session)

            initRunner(
                runnerConfig = runnerConfig,
                runnerArgs = emptyList(),
                program = programConfig,
                programArgs = emptyList(),
            )

            // First entry should be the header
            assertTrue(logs.isNotEmpty())
            val headerLine = logs[0]
            assertTrue(headerLine.contains("\"version\":2"), "Header must have version 2: $headerLine")
            assertTrue(headerLine.contains("\"type\":\"header\""), "Must be a header entry: $headerLine")
        }

    // ----- Compressed mode tests -----

    @Test
    fun `compression mode produces valid compressed init diff entry`() =
        runTest {
            val logs = mutableListOf<String>()
            val session =
                LoggingSession(
                    DebugLoggingConfig(
                        getTimestamp = { 12345L },
                        log = { json -> logs.add(json) },
                        compressionEnabled = true,
                    ),
                )
            val runnerConfig = makeRunnerConfig(logs, compressionEnabled = true, session = session)

            initRunner(
                runnerConfig = runnerConfig,
                runnerArgs = emptyList(),
                program = programConfig,
                programArgs = emptyList(),
            )

            // Logs: header + (optional stringDict entries) + init
            assertTrue(logs.size >= 2, "Expected at least header and init entries")
            val initLine = logs.last()

            assertTrue(initLine.contains("\"modelDiff\""), "Compressed init must have modelDiff: $initLine")
            // In compressed mode, string values are replaced with @N references
            // The 'add' op string should be compressed
            assertTrue(
                initLine.contains("\"op\":") || initLine.contains("\"@"),
                "Compressed init should have op field: $initLine",
            )
        }

    @Test
    fun `compression mode produces valid compressed update diff entry`() =
        runTest {
            val logs = mutableListOf<String>()
            val session =
                LoggingSession(
                    DebugLoggingConfig(
                        getTimestamp = { 12345L },
                        log = { json -> logs.add(json) },
                        compressionEnabled = true,
                    ),
                )
            val runnerConfig = makeRunnerConfig(logs, compressionEnabled = true, session = session)

            var runner =
                initRunner(
                    runnerConfig = runnerConfig,
                    runnerArgs = emptyList(),
                    program = programConfig,
                    programArgs = emptyList(),
                )

            runner = runner.copy(pendingMessages = listOf(TestMessage.Increment(3)))
            stepProgram(runner, this)

            // Find the update entry (type contains "update")
            val updateLine = logs.last { it.contains("\"type\":\"update\"") }

            assertTrue(updateLine.contains("\"modelDiff\""), "Compressed update must have modelDiff: $updateLine")
        }

    @Test
    fun `non-compressed init entry does not contain model field`() =
        runTest {
            val logs = mutableListOf<String>()
            val runnerConfig = makeRunnerConfig(logs, compressionEnabled = false)

            initRunner(
                runnerConfig = runnerConfig,
                runnerArgs = emptyList(),
                program = programConfig,
                programArgs = emptyList(),
            )

            val line = logs[0]
            // "model": should only appear as part of "modelDiff" not standalone
            val hasStandaloneModel =
                Regex(""""model"\s*:\s*\{""").containsMatchIn(line) ||
                    Regex(""""model"\s*:\s*\[""").containsMatchIn(line) ||
                    Regex(""""model"\s*:\s*"[^D]""").containsMatchIn(line)
            assertFalse(hasStandaloneModel, "Should not contain standalone 'model' field, got: $line")
        }
}
