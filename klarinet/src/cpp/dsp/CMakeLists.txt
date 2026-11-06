cmake_minimum_required(VERSION 3.22.1)
project(klarinet-dsp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Host dspTests sets this ON. Android, JVM, Apple, and native desktop leave it
# OFF and keep the C++ FIFO, ring, and offload.
option(KLARINET_DSP_RUST_QUEUES "Link Rust FIFO, ring, and offload into this static library" OFF)

set(KLARINET_DSP_SOURCES
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

if(NOT KLARINET_DSP_RUST_QUEUES)
    list(APPEND KLARINET_DSP_SOURCES AudioCallbackOffload.cpp)
endif()

add_library(klarinet-dsp STATIC ${KLARINET_DSP_SOURCES})

target_include_directories(klarinet-dsp PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)
target_link_libraries(klarinet-dsp PUBLIC Threads::Threads)

if(KLARINET_DSP_RUST_QUEUES)
    target_compile_definitions(klarinet-dsp PUBLIC KLARINET_DSP_RUST_QUEUES=1)

    set(KLARINET_RUST_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../rust/klarinet-dsp")
    set(KLARINET_RUST_MANIFEST "${KLARINET_RUST_DIR}/Cargo.toml")
    set(RUST_TARGET_DIR "${CMAKE_CURRENT_BINARY_DIR}/rust-target")
    set(RUST_LIB "${RUST_TARGET_DIR}/release/libklarinet_dsp_core.a")
    set(RUST_STAMP "${CMAKE_CURRENT_BINARY_DIR}/klarinet_dsp_rust.stamp")
    find_program(CARGO_EXECUTABLE cargo HINTS "$ENV{HOME}/.cargo/bin")
    if(NOT CARGO_EXECUTABLE)
        message(FATAL_ERROR "cargo was not found; it is required when KLARINET_DSP_RUST_QUEUES=ON")
    endif()

    set(KLARINET_RUST_SOURCES
        "${KLARINET_RUST_MANIFEST}"
        "${KLARINET_RUST_DIR}/Cargo.lock"
        "${KLARINET_RUST_DIR}/src/lib.rs"
        "${KLARINET_RUST_DIR}/src/fifo.rs"
        "${KLARINET_RUST_DIR}/src/ring.rs"
        "${KLARINET_RUST_DIR}/src/offload.rs"
    )

    add_custom_command(
        OUTPUT "${RUST_STAMP}"
        COMMAND "${CARGO_EXECUTABLE}" build --release --locked
                --manifest-path "${KLARINET_RUST_MANIFEST}"
                --target-dir "${RUST_TARGET_DIR}"
        COMMAND "${CMAKE_COMMAND}" -E touch "${RUST_STAMP}"
        DEPENDS ${KLARINET_RUST_SOURCES}
        WORKING_DIRECTORY "${KLARINET_RUST_DIR}"
        COMMENT "Building Rust FIFO, ring, and offload"
        VERBATIM
    )
    add_custom_target(klarinet_dsp_rust DEPENDS "${RUST_STAMP}")

    add_library(klarinet_dsp_core STATIC IMPORTED)
    set_target_properties(klarinet_dsp_core PROPERTIES IMPORTED_LOCATION "${RUST_LIB}")
    add_dependencies(klarinet_dsp_core klarinet_dsp_rust)
    add_dependencies(klarinet-dsp klarinet_dsp_rust)
    target_link_libraries(klarinet-dsp PUBLIC klarinet_dsp_core)
endif()

option(KLARINET_DSP_BUILD_TESTS "Build DSP unit tests" OFF)
if(KLARINET_DSP_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
