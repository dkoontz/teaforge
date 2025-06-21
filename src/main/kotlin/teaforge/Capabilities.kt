package teaforge.capabilities

import teaforge.Capability

sealed interface HttpEffect {
    data class Get(
        val url: String,
    ) : HttpEffect

    data class Post(
        val url: String,
        val body: String,
    ) : HttpEffect

    data class Put(
        val url: String,
        val body: String,
    ) : HttpEffect

    data class Delete(
        val url: String,
    ) : HttpEffect
}

typealias HttpSubscription = Nothing

fun httpCapability(): Capability<HttpEffect, HttpSubscription> =
    Capability("HTTP")
