package com.vectencia.klarinet

/**
 * Base exception for all errors originating from the Klarinet audio SDK.
 *
 * All Klarinet-specific exceptions extend this class, allowing callers to catch
 * the entire family with a single `catch (e: KlarinetException)` block.
 *
 * @param message A human-readable description of the error.
 * @param cause The underlying exception that caused this error, or `null`.
 */
open class KlarinetException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when an audio stream or effect could not be created.
 *
 * Common causes include invalid stream configuration (e.g., unsupported sample rate
 * or channel count), insufficient system resources, or a failure in the native
 * audio backend.
 *
 * @param message A human-readable description of the creation failure.
 * @param cause The underlying exception, or `null`.
 */
class StreamCreationException(message: String, cause: Throwable? = null) : KlarinetException(message, cause)

/**
 * Thrown when an operation on an active audio stream fails.
 *
 * This includes errors during [AudioStream] start, stop, pause, or flush operations,
 * as well as unexpected disconnections from the audio device.
 *
 * @param message A human-readable description of the operation failure.
 * @param cause The underlying exception, or `null`.
 */
class StreamOperationException(message: String, cause: Throwable? = null) : KlarinetException(message, cause)

/**
 * Thrown when a requested audio device could not be found.
 *
 * This may occur when specifying a device ID that no longer exists (e.g., the device
 * was unplugged) or when no device matches the requested direction.
 *
 * @param message A human-readable description identifying the missing device.
 */
class DeviceNotFoundException(message: String) : KlarinetException(message)

/**
 * Thrown when an audio session operation fails.
 *
 * On Apple platforms, this wraps errors from `AVAudioSession` operations such as
 * configuring the category/mode or activating/deactivating the session.
 * On Android, this is generally not thrown since audio session management is
 * handled by the system.
 *
 * @param message A human-readable description of the session failure.
 * @param cause The underlying exception, or `null`.
 * @see AudioSessionManager
 */
class AudioSessionException(message: String, cause: Throwable? = null) : KlarinetException(message, cause)

/**
 * Thrown when a required audio permission has not been granted.
 *
 * This is typically thrown when attempting to open an input (recording) stream
 * without the user having granted microphone permission.
 *
 * @param message A human-readable description of the missing permission.
 */
class PermissionException(message: String) : KlarinetException(message)

/**
 * Thrown when the requested audio format is not supported by the platform or device.
 *
 * This may occur when specifying a sample rate, channel count, or sample format
 * that the target audio device cannot handle.
 *
 * @param message A human-readable description of the unsupported format.
 */
class UnsupportedFormatException(message: String) : KlarinetException(message)

/**
 * Thrown when an audio file operation fails.
 *
 * This includes errors during reading, writing, decoding, or encoding audio files
 * (e.g., file not found, unsupported codec, corrupted data).
 *
 * @param message A human-readable description of the file operation failure.
 * @param cause The underlying exception, or `null`.
 */
class AudioFileException(message: String, cause: Throwable? = null) : KlarinetException(message, cause)
