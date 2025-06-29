package teaforge.capabilities

import teaforge.Capability
import teaforge.utils.Result

// Database Effects
sealed interface DatabaseEffect {
    data class ExecuteQuery<TMessage>(
        val sql: String,
        val parameters: List<Any> = emptyList(),
        val message: (Result<QueryResult, DatabaseError>) -> TMessage,
    ) : DatabaseEffect

    data class ExecuteUpdate<TMessage>(
        val sql: String,
        val parameters: List<Any> = emptyList(),
        val message: (Result<UpdateResult, DatabaseError>) -> TMessage,
    ) : DatabaseEffect

    data class BeginTransaction<TMessage>(
        val message: (Result<Unit, DatabaseError>) -> TMessage,
    ) : DatabaseEffect

    data class CommitTransaction<TMessage>(
        val message: (Result<Unit, DatabaseError>) -> TMessage,
    ) : DatabaseEffect

    data class RollbackTransaction<TMessage>(
        val message: (Result<Unit, DatabaseError>) -> TMessage,
    ) : DatabaseEffect

    data class ExecuteMigration<TMessage>(
        val migrationScript: String,
        val message: (Result<Unit, DatabaseError>) -> TMessage,
    ) : DatabaseEffect
}

// Database Subscriptions
sealed interface DatabaseSubscription {
    data class TableChanges<TMessage>(
        val tableName: String,
        val message: (TableChangeEvent) -> TMessage,
    ) : DatabaseSubscription

    data class ConnectionStatus<TMessage>(
        val message: (ConnectionStatusEvent) -> TMessage,
    ) : DatabaseSubscription
}

// Database Result and Error Types
data class QueryResult(
    val rows: List<Map<String, Any>>,
    val columns: List<String>,
)

data class UpdateResult(
    val affectedRows: Int,
    val lastInsertId: Long? = null,
)

sealed interface DatabaseError {
    data class ConnectionError(
        val message: String,
    ) : DatabaseError

    data class SqlError(
        val sql: String,
        val message: String,
    ) : DatabaseError

    data class TransactionError(
        val message: String,
    ) : DatabaseError

    data class MigrationError(
        val script: String,
        val message: String,
    ) : DatabaseError

    data class ParameterError(
        val message: String,
    ) : DatabaseError
}

// Database Events for subscriptions
sealed interface TableChangeEvent {
    data class RowInserted(
        val tableName: String,
        val rowData: Map<String, Any>,
    ) : TableChangeEvent

    data class RowUpdated(
        val tableName: String,
        val rowData: Map<String, Any>,
    ) : TableChangeEvent

    data class RowDeleted(
        val tableName: String,
        val rowId: Any,
    ) : TableChangeEvent
}

sealed interface ConnectionStatusEvent {
    data object Connected : ConnectionStatusEvent

    data object Disconnected : ConnectionStatusEvent

    data class ConnectionError(
        val message: String,
    ) : ConnectionStatusEvent
}

fun databaseCapability(): Capability<DatabaseEffect, DatabaseSubscription> =
    Capability("Database")
