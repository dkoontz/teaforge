package teaforge.capabilities

import teaforge.Capability
import teaforge.utils.Result

// FileSystem Effects
sealed interface FileSystemEffect {
    data class ReadFile<TMessage>(
        val path: String,
        val message: (Result<FileContent, FileSystemError>) -> TMessage,
    ) : FileSystemEffect

    data class WriteFile<TMessage>(
        val path: String,
        val content: String,
        val message: (Result<Unit, FileSystemError>) -> TMessage,
    ) : FileSystemEffect

    data class DeleteFile<TMessage>(
        val path: String,
        val message: (Result<Unit, FileSystemError>) -> TMessage,
    ) : FileSystemEffect

    data class CreateDirectory<TMessage>(
        val path: String,
        val createMissingParents: Boolean,
        val message: (Result<Unit, FileSystemError>) -> TMessage,
    ) : FileSystemEffect

    data class ListDirectory<TMessage>(
        val path: String,
        val message: (Result<DirectoryListing, FileSystemError>) -> TMessage,
    ) : FileSystemEffect

    data class CopyFile<TMessage>(
        val source: String,
        val destination: String,
        val message: (Result<Unit, FileSystemError>) -> TMessage,
    ) : FileSystemEffect

    data class MoveFile<TMessage>(
        val source: String,
        val destination: String,
        val message: (Result<Unit, FileSystemError>) -> TMessage,
    ) : FileSystemEffect

    data class FileMetadata<TMessage>(
        val path: String,
        val message: (Result<FileExistsResult, FileSystemError>) -> TMessage,
    ) : FileSystemEffect

    data class FileExists<TMessage>(
        val path: String,
        val message: (Boolean) -> TMessage,
    ) : FileSystemEffect
}

// FileSystem Subscriptions
sealed interface FileSystemSubscription {
    data class WatchFile<TMessage>(
        val path: String,
        val message: (FileSystemEvent) -> TMessage,
    ) : FileSystemSubscription

    data class WatchDirectory<TMessage>(
        val path: String,
        val message: (FileSystemEvent) -> TMessage,
    ) : FileSystemSubscription
}

// FileSystem Result and Error Types
data class FileContent(
    val content: String,
)

data class DirectoryListing(
    val entries: List<FileEntry>,
)

data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)

data class FileExistsResult(
    val exists: Boolean,
)

sealed interface FileSystemError {
    data class FileNotFound(
        val path: String,
    ) : FileSystemError

    data class PermissionDenied(
        val path: String,
    ) : FileSystemError

    data class DirectoryNotFound(
        val path: String,
    ) : FileSystemError

    data class FileAlreadyExists(
        val path: String,
    ) : FileSystemError

    data class IoError(
        val message: String,
    ) : FileSystemError

    data class InvalidPath(
        val path: String,
    ) : FileSystemError
}

// FileSystem Events for subscriptions
sealed interface FileSystemEvent {
    data class FileChanged(
        val path: String,
    ) : FileSystemEvent

    data class FileCreated(
        val path: String,
    ) : FileSystemEvent

    data class FileDeleted(
        val path: String,
    ) : FileSystemEvent
}

fun fileSystemCapability(): Capability<FileSystemEffect, FileSystemSubscription> =
    Capability("FileSystem")
