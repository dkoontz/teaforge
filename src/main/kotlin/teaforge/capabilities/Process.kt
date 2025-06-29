package teaforge.capabilities

import teaforge.Capability
import teaforge.utils.Result

// Process Result and Error Types
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

data class SpawnedProcess(
    val processId: Int,
)

sealed interface ProcessError {
    data class CommandNotFound(val command: String) : ProcessError
    data class PermissionDenied(val command: String) : ProcessError
    data class ExecutionFailed(val command: String, val message: String) : ProcessError
    data class ProcessNotFound(val processId: Int) : ProcessError
    data class TimeoutError(val command: String) : ProcessError
    data class IoError(val message: String) : ProcessError
}

// Process Effects
sealed interface ProcessEffect {
    data class ExecuteCommand<TMessage>(
        val command: String,
        val args: List<String> = emptyList(),
        val workingDirectory: String? = null,
        val message: (Result<ProcessResult, ProcessError>) -> TMessage,
    ) : ProcessEffect

    data class SpawnProcess<TMessage>(
        val command: String,
        val args: List<String> = emptyList(),
        val workingDirectory: String? = null,
        val message: (Result<SpawnedProcess, ProcessError>) -> TMessage,
    ) : ProcessEffect

    data class KillProcess<TMessage>(
        val processId: Int,
        val message: (Result<Unit, ProcessError>) -> TMessage,
    ) : ProcessEffect
}

// Process Subscriptions
sealed interface ProcessSubscription {
    data class ProcessOutput<TMessage>(
        val processId: Int,
        val message: (ProcessOutputEvent) -> TMessage,
    ) : ProcessSubscription

    data class ProcessStatus<TMessage>(
        val processId: Int,
        val message: (ProcessStatusEvent) -> TMessage,
    ) : ProcessSubscription
}

// Process Events for subscriptions
sealed interface ProcessOutputEvent {
    data class StdoutData(val data: String) : ProcessOutputEvent
    data class StderrData(val data: String) : ProcessOutputEvent
}

sealed interface ProcessStatusEvent {
    data class ProcessStarted(val processId: Int) : ProcessStatusEvent
    data class ProcessExited(val processId: Int, val exitCode: Int) : ProcessStatusEvent
    data class ProcessKilled(val processId: Int) : ProcessStatusEvent
}

fun processCapability(): Capability<ProcessEffect, ProcessSubscription> =
    Capability("Process")