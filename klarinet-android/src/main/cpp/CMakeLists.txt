cmake_minimum_required(VERSION 3.22.1)
project("klarinet-android" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED KLARINET_DSP_DIR)
    set(KLARINET_DSP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../klarinet/src/cpp/dsp")
endif()

# Each Android ABI links the Rust JNI staticlib. That crate cross-compiles
# the DSP rlib, so this static library must not also run the host-triple
# cargo build.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(KLARINET_ANDROID_RUST_TARGET "aarch64-linux-android")
    set(KLARINET_ANDROID_RUST_LINKER_ENV "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(KLARINET_ANDROID_RUST_TARGET "armv7-linux-androideabi")
    set(KLARINET_ANDROID_RUST_LINKER_ENV "CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(KLARINET_ANDROID_RUST_TARGET "x86_64-linux-android")
    set(KLARINET_ANDROID_RUST_LINKER_ENV "CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER")
endif()
if(KLARINET_ANDROID_RUST_TARGET)
    set(KLARINET_DSP_RUST_EFFECTS ON CACHE BOOL "Rust effect shims for this ABI's JNI library" FORCE)
    set(KLARINET_DSP_RUST_QUEUES OFF CACHE BOOL "Keep the C++ ring out of the effect shims" FORCE)
    set(KLARINET_DSP_RUST_LINK_CORE OFF CACHE BOOL "The JNI crate links the DSP rlib" FORCE)
endif()

include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.11.0
)
FetchContent_MakeAvailable(oboe)

add_subdirectory(${KLARINET_DSP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/klarinet-dsp)

message(STATUS "Klarinet Android ABI ${ANDROID_ABI} effects=${KLARINET_DSP_RUST_EFFECTS} queues=${KLARINET_DSP_RUST_QUEUES} link_core=${KLARINET_DSP_RUST_LINK_CORE}")

if(KLARINET_ANDROID_RUST_TARGET)
    set(KLARINET_ANDROID_RUST_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../klarinet/rust/klarinet-android")
    set(KLARINET_ANDROID_RUST_MANIFEST "${KLARINET_ANDROID_RUST_DIR}/Cargo.toml")
    set(KLARINET_ANDROID_RUST_TARGET_DIR "${CMAKE_CURRENT_BINARY_DIR}/rust-android")
    set(KLARINET_ANDROID_RUST_LIB "${KLARINET_ANDROID_RUST_TARGET_DIR}/${KLARINET_ANDROID_RUST_TARGET}/release/libklarinet_android.a")
    find_program(CARGO_EXECUTABLE cargo HINTS "$ENV{HOME}/.cargo/bin")
    if(NOT CARGO_EXECUTABLE)
        message(FATAL_ERROR "cargo was not found; it is required to build the Rust JNI library")
    endif()
    if(NOT CMAKE_C_COMPILER)
        message(FATAL_ERROR "CMAKE_C_COMPILER was not set; the Android clang is the Rust linker")
    endif()

    set(KLARINET_ANDROID_RUST_SOURCES
        "${KLARINET_ANDROID_RUST_MANIFEST}"
        "${KLARINET_ANDROID_RUST_DIR}/Cargo.lock"
        "${KLARINET_ANDROID_RUST_DIR}/src/lib.rs"
        "${KLARINET_ANDROID_RUST_DIR}/src/analysis.rs"
        "${KLARINET_ANDROID_RUST_DIR}/src/jni.rs"
        "${KLARINET_ANDROID_RUST_DIR}/src/oboe.rs"
        "${KLARINET_ANDROID_RUST_DIR}/src/dsp.rs"
        "${KLARINET_ANDROID_RUST_DIR}/src/audio.rs"
        "${KLARINET_ANDROID_RUST_DIR}/src/engine.rs"
        "${KLARINET_ANDROID_RUST_DIR}/src/bridge.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/Cargo.toml"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/Cargo.lock"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/lib.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/analysis.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/fifo.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/ring.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/offload.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/abi.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/chain.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/fx/mod.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/fx/gain.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/fx/dynamics.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/fx/filters.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/fx/delay.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/fx/reverb.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/fx/modulation.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/primitives/mod.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/primitives/biquad.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/primitives/circular.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/primitives/lfo.rs"
        "${KLARINET_ANDROID_RUST_DIR}/../klarinet-dsp/src/primitives/envelope.rs"
    )

    add_custom_command(
        OUTPUT "${KLARINET_ANDROID_RUST_LIB}"
        COMMAND ${CMAKE_COMMAND} -E make_directory "${KLARINET_ANDROID_RUST_TARGET_DIR}"
        COMMAND ${CMAKE_COMMAND} -E env
                "${KLARINET_ANDROID_RUST_LINKER_ENV}=${CMAKE_C_COMPILER}"
                "${CARGO_EXECUTABLE}" build --release --locked
                --manifest-path "${KLARINET_ANDROID_RUST_MANIFEST}"
                --target-dir "${KLARINET_ANDROID_RUST_TARGET_DIR}"
                --target "${KLARINET_ANDROID_RUST_TARGET}"
        DEPENDS ${KLARINET_ANDROID_RUST_SOURCES}
        WORKING_DIRECTORY "${KLARINET_ANDROID_RUST_DIR}"
        COMMENT "Building Klarinet Android Rust JNI"
        VERBATIM
    )
    add_custom_target(klarinet_android_rust DEPENDS "${KLARINET_ANDROID_RUST_LIB}")

    add_library(klarinet-jni SHARED klarinet_oboe.cpp)
    add_dependencies(klarinet-jni klarinet_android_rust)
    target_link_libraries(klarinet-jni
        PRIVATE
        "-Wl,--whole-archive"
        "${KLARINET_ANDROID_RUST_LIB}"
        "-Wl,--no-whole-archive"
        oboe
        klarinet-dsp
        android
        log
    )
else()
    add_library(klarinet-jni SHARED
        klarinet_jni.cpp
        KlarinetEngine.cpp
        KlarinetCallback.cpp
    )
    target_link_libraries(klarinet-jni
        PRIVATE
        oboe
        klarinet-dsp
        android
        log
    )
endif()

target_include_directories(klarinet-jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${KLARINET_DSP_DIR}
)
