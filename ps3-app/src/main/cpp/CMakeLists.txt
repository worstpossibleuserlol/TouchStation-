cmake_minimum_required(VERSION 3.22.1)
project(touchstation_ps3 LANGUAGES CXX)
if(ANDROID)
    add_library(touchstation_ps3 SHARED
        ppu_interpreter.cpp
        spu_interpreter.cpp
        rsx_command_processor.cpp
        guest_pipeline.cpp
        jni_bridge.cpp
    )
    find_library(log-lib log)
    target_link_libraries(touchstation_ps3 PRIVATE ${log-lib})
else()
    include(CTest)
    if(BUILD_TESTING)
        add_executable(engine_self_test
            ppu_interpreter.cpp
            spu_interpreter.cpp
            rsx_command_processor.cpp
            guest_pipeline.cpp
            ppu_self_test.cpp
        )
        add_test(NAME engine_self_test COMMAND engine_self_test)
    endif()
endif()
if(TARGET touchstation_ps3)
    target_compile_features(touchstation_ps3 PRIVATE cxx_std_17)
    target_compile_options(touchstation_ps3 PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wsign-conversion -fno-exceptions)
endif()
if(TARGET engine_self_test)
    target_compile_features(engine_self_test PRIVATE cxx_std_17)
    target_compile_options(engine_self_test PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wsign-conversion)
endif()
