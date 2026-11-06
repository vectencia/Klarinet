#ifndef KLARINET_AUDIO_TEST_H
#define KLARINET_AUDIO_TEST_H

/* Test entry points compiled into libklarinet_native.a. Not part of klarinet_native.h. */
void klarinet_test_playback_chain(void* chain, float* buffer, int frames, int channels);
int klarinet_test_offload_user_stays_off_the_audio_thread(int frames, int channels);

#endif
