package teaforge.utils

sealed interface Maybe<out T> {
    object None : Maybe<Nothing>
    data class Some<T>(val value: T) : Maybe<T>
}

sealed interface Result<out TSuccess, out TError> {
    data class Success<TSuccess, TError>(val value: TSuccess) : Result<TSuccess, TError>
    data class Error<TSuccess, TError>(val value: TError) : Result<TSuccess, TError>
}

fun <T, U> Maybe<T>.map(fn: (T) -> U): Maybe<U> {
    return when (this) {
        is Maybe.None -> this
        is Maybe.Some -> Maybe.Some(fn(value))
    }
}

fun <T, U> Maybe<T>.andThen(fn: (T) -> Maybe<U>): Maybe<U> {
    return when (this) {
        is Maybe.None -> this
        is Maybe.Some -> fn(value)
    }
}

fun <T> Maybe<T>.valueOrDefault(default: T): T {
    return when (this) {
        is Maybe.None -> default
        is Maybe.Some -> value
    }
}

fun <T, U> Maybe<T>.unwrap(
    default: U,
    fn: (T) -> U,
): U {
    return when (this) {
        is Maybe.None -> default
        is Maybe.Some -> fn(value)
    }
}

fun <Success, Error, MappedSuccess> Result<Success, Error>.map(
    fn: (Success) -> MappedSuccess,
): Result<MappedSuccess, Error> {
    return when (this) {
        is Result.Error -> Result.Error<MappedSuccess, Error>(value)
        is Result.Success -> Result.Success(fn(value))
    }
}

fun <Success, Error, MappedSuccess> Result<Success, Error>.andThen(
    fn: (Success) -> Result<MappedSuccess, Error>,
): Result<MappedSuccess, Error> {
    return when (this) {
        is Result.Error -> Result.Error<MappedSuccess, Error>(value)
        is Result.Success -> fn(value)
    }
}

fun <Success, Error> Result<Success, Error>.valueOrDefault(default: Success): Success {
    return when (this) {
        is Result.Error -> default
        is Result.Success -> value
    }
}

fun <Success, Error, MappedSuccess> Result<Success, Error>.unwrap(
    default: MappedSuccess,
    fn: (Success) -> MappedSuccess,
): MappedSuccess {
    return when (this) {
        is Result.Error -> default
        is Result.Success -> fn(value)
    }
}

infix fun <T, U> T.then(fn: (T) -> U): U {
    return fn(this)
}

fun <T> conditional(
    item: T,
    condition: Boolean,
): List<T> {
    return if (condition) {
        listOf(item)
    } else {
        emptyList()
    }
}
