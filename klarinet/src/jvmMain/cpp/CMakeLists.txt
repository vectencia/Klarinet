cmake_minimum_required(VERSION 3.15)
project(klarinet_jvm LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(KLARINET_DSP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../cpp/dsp")
add_subdirectory(${KLARINET_DSP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/klarinet-dsp)

if(KLARINET_JNI_INCLUDE)
    set(JNI_INCLUDE_DIRS "${KLARINET_JNI_INCLUDE}")
    if(KLARINET_JNI_MD_INCLUDE)
        list(APPEND JNI_INCLUDE_DIRS "${KLARINET_JNI_MD_INCLUDE}")
    endif()
else()
    find_package(JNI REQUIRED)
endif()

add_library(klarinet_jvm SHARED
    miniaudio.c
    klarinet_jvm.c
)

target_include_directories(klarinet_jvm PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${KLARINET_DSP_DIR}
)

target_link_libraries(klarinet_jvm PRIVATE klarinet-dsp)
set_target_properties(klarinet_jvm PROPERTIES LINKER_LANGUAGE CXX)
if(WIN32)
    set_target_properties(klarinet_jvm PROPERTIES PREFIX "")
endif()

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
