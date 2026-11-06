package com.vectencia.klarinet

expect object AudioMath {
    fun rms(buffer: FloatArray, offset: Int = 0, length: Int = buffer.size - offset): Float
    fun peak(buffer: FloatArray, offset: Int = 0, length: Int = buffer.size - offset): Float
    fun linearToDb(linear: Float): Float
    fun dbToLinear(db: Float): Float
    fun hannWindow(size: Int): FloatArray
}
