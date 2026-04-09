cmake_minimum_required(VERSION 3.22.1)
project(klarinet-dsp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(klarinet-dsp STATIC
    AudioEffect.cpp
    EffectChain.cpp
    EffectFactory.cpp
    klarinet_dsp.cpp
    primitives/Biquad.cpp
    primitives/CircularBuffer.cpp
    primitives/LFO.cpp
    primitives/EnvelopeFollower.cpp
    effects/Gain.cpp
    effects/Pan.cpp
    effects/MuteSolo.cpp
    effects/Compressor.cpp
    effects/Limiter.cpp
    effects/NoiseGate.cpp
    effects/ParametricEQ.cpp
    effects/LowPassFilter.cpp
    effects/HighPassFilter.cpp
    effects/BandPassFilter.cpp
    effects/Delay.cpp
    effects/Reverb.cpp
    effects/Chorus.cpp
    effects/Flanger.cpp
    effects/Phaser.cpp
    effects/Tremolo.cpp
)

target_include_directories(klarinet-dsp PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

option(KLARINET_DSP_BUILD_TESTS "Build DSP unit tests" OFF)
if(KLARINET_DSP_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
