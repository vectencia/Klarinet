cmake_minimum_required(VERSION 3.22.1)
project("klarinet-android" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED KLARINET_DSP_DIR)
    set(KLARINET_DSP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../klarinet/src/cpp/dsp")
endif()

include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.10.0
)
FetchContent_MakeAvailable(oboe)

add_subdirectory(${KLARINET_DSP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/klarinet-dsp)

add_library(klarinet-jni SHARED
    klarinet_jni.cpp
    KlarinetEngine.cpp
    KlarinetCallback.cpp
)

target_include_directories(klarinet-jni PRIVATE
    ${KLARINET_DSP_DIR}
)

target_link_libraries(klarinet-jni
    PRIVATE
    oboe
    klarinet-dsp
    android
    log
)
