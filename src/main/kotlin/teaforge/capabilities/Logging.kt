package teaforge.capabilities

import teaforge.Capability

// Logging Effects - these don't typically need to return results
sealed interface LoggingEffect {
    data class Debug(
        val message: String,
        val context: Map<String, Any> = emptyMap(),
    ) : LoggingEffect

    data class Info(
        val message: String,
        val context: Map<String, Any> = emptyMap(),
    ) : LoggingEffect

    data class Warn(
        val message: String,
        val context: Map<String, Any> = emptyMap(),
    ) : LoggingEffect

    data class Error(
        val message: String,
        val context: Map<String, Any> = emptyMap(),
        val throwable: Throwable? = null,
    ) : LoggingEffect
}

typealias LoggingSubscription = Nothing

fun loggingCapability(): Capability<LoggingEffect, LoggingSubscription> =
    Capability("Logging")