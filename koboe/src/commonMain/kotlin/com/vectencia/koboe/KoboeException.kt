package com.vectencia.koboe

open class KoboeException(message: String, cause: Throwable? = null) : Exception(message, cause)
class StreamCreationException(message: String, cause: Throwable? = null) : KoboeException(message, cause)
class StreamOperationException(message: String, cause: Throwable? = null) : KoboeException(message, cause)
class DeviceNotFoundException(message: String) : KoboeException(message)
class AudioSessionException(message: String, cause: Throwable? = null) : KoboeException(message, cause)
class PermissionException(message: String) : KoboeException(message)
class UnsupportedFormatException(message: String) : KoboeException(message)
class AudioFileException(message: String, cause: Throwable? = null) : KoboeException(message, cause)
