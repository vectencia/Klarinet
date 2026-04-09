cmake_minimum_required(VERSION 3.22.1)

set(TEST_SOURCES
    test_primitives.cpp
    test_ring_buffer.cpp
    test_gain.cpp
    test_pan.cpp
    test_compressor.cpp
    test_filters.cpp
    test_delay.cpp
    test_reverb.cpp
    test_modulation.cpp
    test_effect_chain.cpp
)

foreach(TEST_SRC ${TEST_SOURCES})
    get_filename_component(TEST_NAME ${TEST_SRC} NAME_WE)
    if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/${TEST_SRC})
        add_executable(${TEST_NAME} ${TEST_SRC})
        target_link_libraries(${TEST_NAME} klarinet-dsp)
        target_include_directories(${TEST_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
        add_test(NAME ${TEST_NAME} COMMAND ${TEST_NAME})
    endif()
endforeach()
