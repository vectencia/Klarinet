cmake_minimum_required(VERSION 3.15)
project(klarinet_native LANGUAGES C)

set(CMAKE_C_STANDARD 11)

add_library(klarinet_native STATIC
    klarinet_native.c
)

target_include_directories(klarinet_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(APPLE)
    find_library(COREAUDIO CoreAudio)
    find_library(AUDIOUNIT AudioUnit)
    find_library(COREFOUNDATION CoreFoundation)
    target_link_libraries(klarinet_native PRIVATE ${COREAUDIO} ${AUDIOUNIT} ${COREFOUNDATION})
elseif(UNIX)
    find_package(Threads REQUIRED)
    target_link_libraries(klarinet_native PRIVATE Threads::Threads dl m)
elseif(WIN32)
    target_link_libraries(klarinet_native PRIVATE ole32 winmm)
endif()
