cmake_minimum_required(VERSION 3.22.1)
project("koboe-android" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.10.0
)
FetchContent_MakeAvailable(oboe)

add_library(koboe-jni SHARED
    koboe_jni.cpp
    KoboeEngine.cpp
    KoboeCallback.cpp
)

target_link_libraries(koboe-jni
    PRIVATE
    oboe
    android
    log
)
