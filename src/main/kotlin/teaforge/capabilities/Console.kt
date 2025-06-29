package teaforge.capabilities

import teaforge.Capability
import teaforge.utils.Result

// Console Effects
sealed interface ConsoleEffect<TMessage> {
    // These effects don't return results as they rarely fail
    data class Print(
        val message: String,
    ) : ConsoleEffect<Nothing>

    data class PrintLine(
        val message: String,
    ) : ConsoleEffect<Nothing>

    data object ClearScreen : ConsoleEffect<Nothing>

    data class SetColor(
        val color: ConsoleColor,
    ) : ConsoleEffect<Nothing>

    // This effect can fail and needs a message
    data class ReadLine<TMessage>(
        val prompt: String? = null,
        val message: (Result<ConsoleInput, ConsoleError>) -> TMessage,
    ) : ConsoleEffect<TMessage>

    data class Exit(
        val code: Int,
    ) : ConsoleEffect<Nothing>
}

// Console Subscriptions
sealed interface ConsoleSubscription<TMessage> {
    data class KeyboardInput<TMessage>(
        val message: (ConsoleInput) -> TMessage,
    ) : ConsoleSubscription<TMessage>

    data class StdinStream<TMessage>(
        val message: (ConsoleInput) -> TMessage,
    ) : ConsoleSubscription<TMessage>
}

fun <TMessage> consoleCapability(): Capability<ConsoleEffect<TMessage>, ConsoleSubscription<TMessage>> =
    Capability("Console")

// Console Result and Error Types
data class ConsoleInput(
    val input: String,
)

sealed interface ConsoleError {
    data class ReadError(
        val message: String,
    ) : ConsoleError

    data class IoError(
        val message: String,
    ) : ConsoleError
}

enum class ConsoleColor {
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE,
    RESET,
}
