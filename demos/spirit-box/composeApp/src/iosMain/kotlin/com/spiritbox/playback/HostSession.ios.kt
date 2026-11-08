package com.spiritbox.playback

import com.vectencia.klarinet.AudioSessionCategory
import com.vectencia.klarinet.AudioSessionManager
import com.vectencia.klarinet.AudioSessionMode

internal actual fun preparePlaybackSession(): AudioSessionManager? {
    val session = AudioSessionManager()
    session.configure(AudioSessionCategory.PLAYBACK, AudioSessionMode.DEFAULT)
    return session
}
