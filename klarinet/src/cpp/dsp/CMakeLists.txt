cmake_minimum_required(VERSION 3.22.1)
project(klarinet-dsp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Host dspTests sets both options ON.
# The host JVM shared library sets KLARINET_DSP_RUST_EFFECTS only, so its
# offload stays the C++ AudioCallbackOffload.cpp.
# Android, Apple, native desktop, and the other JVM ABIs leave both OFF.
option(KLARINET_DSP_RUST_QUEUES "Link Rust FIFO, ring, and offload into this static library" OFF)
option(KLARINET_DSP_RUST_EFFECTS "Link Rust primitives, effects, and effect chain into this static library" OFF)

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
endif()
if(KLARINET_DSP_RUST_EFFECTS)
    target_compile_definitions(klarinet-dsp PUBLIC KLARINET_DSP_RUST_EFFECTS=1)
endif()

if(KLARINET_DSP_RUST_QUEUES OR KLARINET_DSP_RUST_EFFECTS)
    set(KLARINET_RUST_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../rust/klarinet-dsp")
    set(KLARINET_RUST_MANIFEST "${KLARINET_RUST_DIR}/Cargo.toml")
    set(RUST_TARGET_DIR "${CMAKE_CURRENT_BINARY_DIR}/rust-target")
    set(RUST_LIB "${RUST_TARGET_DIR}/release/libklarinet_dsp_core.a")
    set(RUST_STAMP "${CMAKE_CURRENT_BINARY_DIR}/klarinet_dsp_rust.stamp")
    find_program(CARGO_EXECUTABLE cargo HINTS "$ENV{HOME}/.cargo/bin")
    if(NOT CARGO_EXECUTABLE)
        message(FATAL_ERROR "cargo was not found; it is required when Klarinet Rust DSP is enabled")
    endif()

    set(KLARINET_RUST_SOURCES
        "${KLARINET_RUST_MANIFEST}"
        "${KLARINET_RUST_DIR}/Cargo.lock"
        "${KLARINET_RUST_DIR}/src/lib.rs"
        "${KLARINET_RUST_DIR}/src/fifo.rs"
        "${KLARINET_RUST_DIR}/src/ring.rs"
        "${KLARINET_RUST_DIR}/src/offload.rs"
        "${KLARINET_RUST_DIR}/src/abi.rs"
        "${KLARINET_RUST_DIR}/src/chain.rs"
        "${KLARINET_RUST_DIR}/src/fx/mod.rs"
        "${KLARINET_RUST_DIR}/src/fx/gain.rs"
        "${KLARINET_RUST_DIR}/src/fx/dynamics.rs"
        "${KLARINET_RUST_DIR}/src/fx/filters.rs"
        "${KLARINET_RUST_DIR}/src/fx/delay.rs"
        "${KLARINET_RUST_DIR}/src/fx/reverb.rs"
        "${KLARINET_RUST_DIR}/src/fx/modulation.rs"
        "${KLARINET_RUST_DIR}/src/primitives/mod.rs"
        "${KLARINET_RUST_DIR}/src/primitives/biquad.rs"
        "${KLARINET_RUST_DIR}/src/primitives/circular.rs"
        "${KLARINET_RUST_DIR}/src/primitives/lfo.rs"
        "${KLARINET_RUST_DIR}/src/primitives/envelope.rs"
    )

    if(KLARINET_DSP_RUST_EFFECTS AND NOT KLARINET_DSP_RUST_QUEUES)
        set(KLARINET_CARGO_FEATURE_ARGS --no-default-features --features effects)
    elseif(KLARINET_DSP_RUST_EFFECTS)
        set(KLARINET_CARGO_FEATURE_ARGS --features effects)
    else()
        set(KLARINET_CARGO_FEATURE_ARGS)
    endif()

    add_custom_command(
        OUTPUT "${RUST_STAMP}"
        COMMAND "${CARGO_EXECUTABLE}" build --release --locked
                --manifest-path "${KLARINET_RUST_MANIFEST}"
                --target-dir "${RUST_TARGET_DIR}"
                ${KLARINET_CARGO_FEATURE_ARGS}
        COMMAND "${CMAKE_COMMAND}" -E touch "${RUST_STAMP}"
        DEPENDS ${KLARINET_RUST_SOURCES}
        WORKING_DIRECTORY "${KLARINET_RUST_DIR}"
        COMMENT "Building Klarinet Rust DSP"
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
