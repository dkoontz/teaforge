package teaforge.capabilities

import teaforge.Capability
import teaforge.utils.Result

// Randomization Result and Error Types
data class RandomIntResult(val value: Int)
data class RandomLongResult(val value: Long)
data class RandomDoubleResult(val value: Double)
data class RandomBooleanResult(val value: Boolean)
data class RandomUuidResult(val value: String)
data class RandomStringResult(val value: String)
data class ShuffledListResult<T>(val list: List<T>)
data class RandomChoiceResult<T>(val choice: T)

sealed interface RandomizationError {
    data object EmptyChoiceList : RandomizationError
    data object EmptyShuffleList : RandomizationError
    data class InvalidRange(val message: String) : RandomizationError
}

// Randomization Effects
sealed interface RandomizationEffect {
    // These effects don't typically fail
    data class RandomInt<TMessage>(
        val message: (RandomIntResult) -> TMessage,
    ) : RandomizationEffect

    data class RandomIntRange<TMessage>(
        val min: Int,
        val max: Int,
        val message: (Result<RandomIntResult, RandomizationError>) -> TMessage,
    ) : RandomizationEffect

    data class RandomLong<TMessage>(
        val message: (RandomLongResult) -> TMessage,
    ) : RandomizationEffect

    data class RandomLongRange<TMessage>(
        val min: Long,
        val max: Long,
        val message: (Result<RandomLongResult, RandomizationError>) -> TMessage,
    ) : RandomizationEffect

    data class RandomDouble<TMessage>(
        val message: (RandomDoubleResult) -> TMessage,
    ) : RandomizationEffect

    data class RandomDoubleRange<TMessage>(
        val min: Double,
        val max: Double,
        val message: (Result<RandomDoubleResult, RandomizationError>) -> TMessage,
    ) : RandomizationEffect

    data class RandomBoolean<TMessage>(
        val message: (RandomBooleanResult) -> TMessage,
    ) : RandomizationEffect

    data class RandomUuid<TMessage>(
        val message: (RandomUuidResult) -> TMessage,
    ) : RandomizationEffect

    data class RandomString<TMessage>(
        val length: Int,
        val charset: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
        val message: (RandomStringResult) -> TMessage,
    ) : RandomizationEffect

    // These effects can fail if lists are empty
    data class ShuffleList<T, TMessage>(
        val list: List<T>,
        val message: (Result<ShuffledListResult<T>, RandomizationError>) -> TMessage,
    ) : RandomizationEffect

    data class RandomChoice<T, TMessage>(
        val options: List<T>,
        val message: (Result<RandomChoiceResult<T>, RandomizationError>) -> TMessage,
    ) : RandomizationEffect
}

typealias RandomizationSubscription = Nothing

fun randomizationCapability(): Capability<RandomizationEffect, RandomizationSubscription> =
    Capability("Randomization")