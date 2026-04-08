package com.vectencia.klarinet

open class KlarinetException(message: String, cause: Throwable? = null) : Exception(message, cause)
class StreamCreationException(message: String, cause: Throwable? = null) : KlarinetException(message, cause)
class StreamOperationException(message: String, cause: Throwable? = null) : KlarinetException(message, cause)
class DeviceNotFoundException(message: String) : KlarinetException(message)
class AudioSessionException(message: String, cause: Throwable? = null) : KlarinetException(message, cause)
class PermissionException(message: String) : KlarinetException(message)
class UnsupportedFormatException(message: String) : KlarinetException(message)
class AudioFileException(message: String, cause: Throwable? = null) : KlarinetException(message, cause)
