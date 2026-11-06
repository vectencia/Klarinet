# JNI looks up this method by name on the resampler read instance.
-keepclassmembers class com.vectencia.klarinet.ResamplerRead {
    float[] pull(int);
}
