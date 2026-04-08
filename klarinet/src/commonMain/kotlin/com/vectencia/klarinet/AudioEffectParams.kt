package com.vectencia.klarinet

data class ParameterChange(
    val effect: AudioEffect,
    val paramId: Int,
    val value: Float,
)

object GainParams { const val GAIN_DB = 0 }
object PanParams { const val PAN = 0 }
object MuteSoloParams { const val MUTED = 0; const val SOLOED = 1 }
object CompressorParams { const val THRESHOLD = 0; const val RATIO = 1; const val ATTACK_MS = 2; const val RELEASE_MS = 3; const val MAKEUP_GAIN = 4 }
object LimiterParams { const val THRESHOLD = 0; const val RELEASE_MS = 1 }
object NoiseGateParams { const val THRESHOLD = 0; const val ATTACK_MS = 1; const val RELEASE_MS = 2; const val HOLD_MS = 3 }
object EQParams { const val PARAMS_PER_BAND = 4; const val BAND_FREQUENCY = 0; const val BAND_GAIN = 1; const val BAND_Q = 2; const val BAND_TYPE = 3 }
object LPFParams { const val CUTOFF_HZ = 0; const val RESONANCE = 1 }
object HPFParams { const val CUTOFF_HZ = 0; const val RESONANCE = 1 }
object BPFParams { const val CENTER_HZ = 0; const val BANDWIDTH = 1 }
object DelayParams { const val TIME_MS = 0; const val FEEDBACK = 1; const val WET_DRY_MIX = 2 }
object ReverbParams { const val ROOM_SIZE = 0; const val DAMPING = 1; const val WET_DRY_MIX = 2; const val WIDTH = 3 }
object ChorusParams { const val RATE_HZ = 0; const val DEPTH = 1; const val WET_DRY_MIX = 2 }
object FlangerParams { const val RATE_HZ = 0; const val DEPTH = 1; const val FEEDBACK = 2; const val WET_DRY_MIX = 3 }
object PhaserParams { const val RATE_HZ = 0; const val DEPTH = 1; const val STAGES = 2; const val FEEDBACK = 3 }
object TremoloParams { const val RATE_HZ = 0; const val DEPTH = 1 }
