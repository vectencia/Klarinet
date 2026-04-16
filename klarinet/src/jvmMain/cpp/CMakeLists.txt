cmake_minimum_required(VERSION 3.15)
project(klarinet_jvm LANGUAGES C)

set(CMAKE_C_STANDARD 11)

find_package(JNI REQUIRED)

add_library(klarinet_jvm SHARED
    miniaudio.c
    klarinet_jvm.c
)

target_include_directories(klarinet_jvm PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(APPLE)
    find_library(COREAUDIO CoreAudio)
    find_library(AUDIOUNIT AudioUnit)
    find_library(COREFOUNDATION CoreFoundation)
    target_link_libraries(klarinet_jvm PRIVATE ${COREAUDIO} ${AUDIOUNIT} ${COREFOUNDATION})
elseif(UNIX)
    find_package(Threads REQUIRED)
    target_link_libraries(klarinet_jvm PRIVATE Threads::Threads dl m)
elseif(WIN32)
    target_link_libraries(klarinet_jvm PRIVATE ole32 winmm)
endif()
