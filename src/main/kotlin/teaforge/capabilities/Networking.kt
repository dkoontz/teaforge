package teaforge.capabilities

import teaforge.Capability
import teaforge.utils.Result

// Networking Result and Error Types
data class TcpConnection(
    val connectionId: String,
)

data class PingResult(
    val host: String,
    val responseTimeMs: Long,
    val success: Boolean,
)

sealed interface NetworkingError {
    data class ConnectionError(val host: String, val port: Int, val message: String) : NetworkingError
    data class SendError(val connectionId: String, val message: String) : NetworkingError
    data class NetworkError(val message: String) : NetworkingError
    data class TimeoutError(val host: String, val message: String) : NetworkingError
    data class InvalidConnection(val connectionId: String) : NetworkingError
}

// Networking Effects
sealed interface NetworkingEffect {
    data class TcpConnect<TMessage>(
        val host: String,
        val port: Int,
        val message: (Result<TcpConnection, NetworkingError>) -> TMessage,
    ) : NetworkingEffect

    data class TcpSend<TMessage>(
        val connectionId: String,
        val data: ByteArray,
        val message: (Result<Unit, NetworkingError>) -> TMessage,
    ) : NetworkingEffect

    data class TcpClose<TMessage>(
        val connectionId: String,
        val message: (Result<Unit, NetworkingError>) -> TMessage,
    ) : NetworkingEffect

    data class UdpSend<TMessage>(
        val host: String,
        val port: Int,
        val data: ByteArray,
        val message: (Result<Unit, NetworkingError>) -> TMessage,
    ) : NetworkingEffect

    data class Ping<TMessage>(
        val host: String,
        val message: (Result<PingResult, NetworkingError>) -> TMessage,
    ) : NetworkingEffect
}

// Networking Subscriptions
sealed interface NetworkingSubscription {
    data class TcpServer<TMessage>(
        val port: Int,
        val message: (TcpServerEvent) -> TMessage,
    ) : NetworkingSubscription

    data class UdpServer<TMessage>(
        val port: Int,
        val message: (UdpServerEvent) -> TMessage,
    ) : NetworkingSubscription

    data class NetworkStatus<TMessage>(
        val message: (NetworkStatusEvent) -> TMessage,
    ) : NetworkingSubscription
}

// Networking Events for subscriptions
sealed interface TcpServerEvent {
    data class ClientConnected(val connectionId: String, val clientAddress: String) : TcpServerEvent
    data class ClientDisconnected(val connectionId: String) : TcpServerEvent
    data class DataReceived(val connectionId: String, val data: ByteArray) : TcpServerEvent
}

sealed interface UdpServerEvent {
    data class DataReceived(val sourceAddress: String, val sourcePort: Int, val data: ByteArray) : UdpServerEvent
}

sealed interface NetworkStatusEvent {
    data object NetworkAvailable : NetworkStatusEvent
    data object NetworkUnavailable : NetworkStatusEvent
    data class NetworkChanged(val interfaceName: String) : NetworkStatusEvent
}

fun networkingCapability(): Capability<NetworkingEffect, NetworkingSubscription> =
    Capability("Networking")