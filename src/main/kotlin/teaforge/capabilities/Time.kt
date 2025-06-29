package teaforge.capabilities

import teaforge.Capability
import teaforge.utils.Result

// Time Result and Error Types
data class CurrentTimeResult(
    val timestamp: Long,
)

data class FormattedTime(
    val formattedString: String,
)

data class ParsedTime(
    val timestamp: Long,
)

sealed interface TimeError {
    data class ParseError(val input: String, val format: String, val message: String) : TimeError
    data class FormatError(val timestamp: Long, val format: String, val message: String) : TimeError
    data class SystemTimeError(val message: String) : TimeError
}

// Time Effects
sealed interface TimeEffect {
    data class CurrentTime<TMessage>(
        val message: (Result<CurrentTimeResult, TimeError>) -> TMessage,
    ) : TimeEffect

    data class FormatTime<TMessage>(
        val timestamp: Long,
        val format: String,
        val message: (Result<FormattedTime, TimeError>) -> TMessage,
    ) : TimeEffect

    data class ParseTime<TMessage>(
        val timeString: String,
        val format: String,
        val message: (Result<ParsedTime, TimeError>) -> TMessage,
    ) : TimeEffect
}

// Time Subscriptions
sealed interface TimeSubscription {
    data class Timer<TMessage>(
        val intervalMillis: Long,
        val message: (TimerEvent) -> TMessage,
    ) : TimeSubscription

    data class DelayedEvent<TMessage>(
        val delayMillis: Long,
        val message: (DelayedEventFired) -> TMessage,
    ) : TimeSubscription

    data class ScheduledEvent<TMessage>(
        val cronExpression: String,
        val message: (ScheduledEventFired) -> TMessage,
    ) : TimeSubscription
}

// Time Events for subscriptions
data class TimerEvent(
    val timestamp: Long,
)

data class DelayedEventFired(
    val timestamp: Long,
)

data class ScheduledEventFired(
    val timestamp: Long,
)

fun timeCapability(): Capability<TimeEffect, TimeSubscription> =
    Capability("Time")