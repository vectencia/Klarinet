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

# Phase 3. Host first, then one JVM ABI at a time. Other ABIs leave this OFF
# and keep compiling klarinet_jvm.c.
option(KLARINET_JVM_RUST "Replace klarinet_jvm.c with the Rust cdylib" OFF)
set(KLARINET_JVM_RUST_TARGET "" CACHE STRING "Cargo triple for the JVM cdylib. Empty builds the host.")
set(KLARINET_JVM_RUST_LINKER "" CACHE FILEPATH "Single executable cargo uses to link a cross-compiled cdylib.")
set(KLARINET_JVM_RUST_ZIG_TARGET "" CACHE STRING "Zig triple passed to KLARINET_JVM_RUST_LINKER.")
option(KLARINET_JVM_RUST_EFFECTS "Link the Rust effects rlib. Off keeps the C++ effect objects." OFF)

if(KLARINET_JVM_RUST)
    add_library(klarinet_ma STATIC miniaudio.c klarinet_ma.c)
    target_include_directories(klarinet_ma PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
    set_target_properties(klarinet_ma PROPERTIES C_VISIBILITY_PRESET hidden)

    set(KLARINET_JVM_RUST_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../rust/klarinet-jvm")
    set(KLARINET_JVM_RUST_MANIFEST "${KLARINET_JVM_RUST_DIR}/Cargo.toml")
    set(KLARINET_JVM_RUST_TARGET_DIR "${CMAKE_CURRENT_BINARY_DIR}/jvm-rust-target")
    if(WIN32)
        set(KLARINET_JVM_RUST_FILE "klarinet_jvm.dll")
    elseif(APPLE)
        set(KLARINET_JVM_RUST_FILE "libklarinet_jvm.dylib")
    else()
        set(KLARINET_JVM_RUST_FILE "libklarinet_jvm.so")
    endif()
    if(KLARINET_JVM_RUST_TARGET)
        set(KLARINET_JVM_RUST_LIB "${KLARINET_JVM_RUST_TARGET_DIR}/${KLARINET_JVM_RUST_TARGET}/release/${KLARINET_JVM_RUST_FILE}")
    else()
        set(KLARINET_JVM_RUST_LIB "${KLARINET_JVM_RUST_TARGET_DIR}/release/${KLARINET_JVM_RUST_FILE}")
    endif()
    set(KLARINET_JVM_RUST_OUTPUT "${CMAKE_BINARY_DIR}/${KLARINET_JVM_RUST_FILE}")

    find_program(CARGO_EXECUTABLE cargo HINTS "$ENV{HOME}/.cargo/bin")
    if(NOT CARGO_EXECUTABLE)
        message(FATAL_ERROR "cargo was not found; it is required when KLARINET_JVM_RUST is ON")
    endif()

    set(KLARINET_JVM_RUST_SOURCES
        "${KLARINET_JVM_RUST_MANIFEST}"
        "${KLARINET_JVM_RUST_DIR}/Cargo.lock"
        "${KLARINET_JVM_RUST_DIR}/build.rs"
        "${KLARINET_JVM_RUST_DIR}/src/lib.rs"
        "${KLARINET_JVM_RUST_DIR}/src/jni.rs"
        "${KLARINET_JVM_RUST_DIR}/src/ffi.rs"
        "${KLARINET_JVM_RUST_DIR}/src/device.rs"
        "${KLARINET_JVM_RUST_DIR}/src/files.rs"
        "${KLARINET_JVM_RUST_DIR}/src/effects.rs"
        "${CMAKE_CURRENT_SOURCE_DIR}/zig_rust_link.py"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/Cargo.toml"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/Cargo.lock"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/lib.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/abi.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/chain.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/ring.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/fx/mod.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/fx/gain.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/fx/dynamics.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/fx/filters.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/fx/delay.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/fx/reverb.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/fx/modulation.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/primitives/mod.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/primitives/biquad.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/primitives/circular.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/primitives/lfo.rs"
        "${KLARINET_DSP_DIR}/../../../rust/klarinet-dsp/src/primitives/envelope.rs"
    )

    set(KLARINET_JVM_CARGO_ARGS
        build --release --locked
        --manifest-path "${KLARINET_JVM_RUST_MANIFEST}"
        --target-dir "${KLARINET_JVM_RUST_TARGET_DIR}"
    )
    if(KLARINET_JVM_RUST_TARGET)
        list(APPEND KLARINET_JVM_CARGO_ARGS --target "${KLARINET_JVM_RUST_TARGET}")
    endif()
    if(NOT KLARINET_JVM_RUST_EFFECTS)
        list(APPEND KLARINET_JVM_CARGO_ARGS --no-default-features)
    endif()

    set(KLARINET_JVM_CARGO_ENV
        "KLARINET_LINK_ARCHIVES=$<TARGET_FILE:klarinet_ma>|$<TARGET_FILE:klarinet-dsp>"
    )
    if(KLARINET_JVM_RUST_LINKER)
        string(REPLACE "-" "_" KLARINET_JVM_RUST_TARGET_ENV "${KLARINET_JVM_RUST_TARGET}")
        string(TOUPPER "${KLARINET_JVM_RUST_TARGET_ENV}" KLARINET_JVM_RUST_TARGET_ENV)
        list(APPEND KLARINET_JVM_CARGO_ENV
            "CARGO_TARGET_${KLARINET_JVM_RUST_TARGET_ENV}_LINKER=${KLARINET_JVM_RUST_LINKER}"
        )
        if(KLARINET_JVM_RUST_ZIG_TARGET)
            list(APPEND KLARINET_JVM_CARGO_ENV
                "KLARINET_ZIG_TARGET=${KLARINET_JVM_RUST_ZIG_TARGET}"
            )
        endif()
    endif()

    add_custom_command(
        OUTPUT "${KLARINET_JVM_RUST_OUTPUT}"
        COMMAND "${CMAKE_COMMAND}" -E env
                ${KLARINET_JVM_CARGO_ENV}
                "${CARGO_EXECUTABLE}" ${KLARINET_JVM_CARGO_ARGS}
        COMMAND "${CMAKE_COMMAND}" -E copy "${KLARINET_JVM_RUST_LIB}" "${KLARINET_JVM_RUST_OUTPUT}"
        DEPENDS
            "$<TARGET_FILE:klarinet_ma>"
            "$<TARGET_FILE:klarinet-dsp>"
            ${KLARINET_JVM_RUST_SOURCES}
        WORKING_DIRECTORY "${KLARINET_JVM_RUST_DIR}"
        COMMENT "Building Klarinet JVM Rust cdylib"
        VERBATIM
    )
    add_custom_target(klarinet_jvm ALL DEPENDS "${KLARINET_JVM_RUST_OUTPUT}")
    add_dependencies(klarinet_jvm klarinet_ma klarinet-dsp)
else()
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
endif()
