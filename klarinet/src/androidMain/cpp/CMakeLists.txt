cmake_minimum_required(VERSION 3.22.1)
project("klarinet-android" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.10.0
)
FetchContent_MakeAvailable(oboe)

# Add DSP library
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../cpp/dsp ${CMAKE_CURRENT_BINARY_DIR}/klarinet-dsp)

add_library(klarinet-jni SHARED
    klarinet_jni.cpp
    KlarinetEngine.cpp
    KlarinetCallback.cpp
)

target_include_directories(klarinet-jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../cpp/dsp
)

target_link_libraries(klarinet-jni
    PRIVATE
    oboe
    klarinet-dsp
    android
    log
)
