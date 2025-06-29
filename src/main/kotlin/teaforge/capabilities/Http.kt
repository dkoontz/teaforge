package teaforge.capabilities

import teaforge.Capability
import teaforge.utils.Result

data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
)

sealed interface HttpError {
    data class NetworkError(
        val statusCode: Int,
        val message: String,
    ) : HttpError

    data class TimeoutError(
        val statusCode: Int,
        val message: String,
    ) : HttpError

    data class HttpStatusError(
        val statusCode: Int,
        val message: String,
    ) : HttpError

    data class ParseError(
        val statusCode: Int,
        val message: String,
    ) : HttpError
}

sealed interface HttpEffect {
    data class Get<TMessage>(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val message: (Result<HttpResponse, HttpError>) -> TMessage,
    ) : HttpEffect

    data class Post<TMessage>(
        val url: String,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
        val message: (Result<HttpResponse, HttpError>) -> TMessage,
    ) : HttpEffect

    data class Put<TMessage>(
        val url: String,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
        val message: (Result<HttpResponse, HttpError>) -> TMessage,
    ) : HttpEffect

    data class Delete<TMessage>(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val message: (Result<HttpResponse, HttpError>) -> TMessage,
    ) : HttpEffect
}

typealias HttpSubscription = Nothing

fun httpCapability(): Capability<HttpEffect, HttpSubscription> =
    Capability("HTTP")
