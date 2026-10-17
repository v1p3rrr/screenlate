cmake_minimum_required(VERSION 3.31)

project(screenlate_mnn LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Inference is far too slow unoptimized, so debug builds compile MNN as release builds do.
set(CMAKE_BUILD_TYPE Release)
set(CMAKE_C_FLAGS_DEBUG "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")

# MNN as one static library with the CPU backend only.
set(MNN_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(MNN_SEP_BUILD OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(MNN_USE_LOGCAT OFF CACHE BOOL "" FORCE)
set(MNN_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_FOR_ANDROID_COMMAND ON CACHE BOOL "" FORCE)

add_subdirectory(MNN EXCLUDE_FROM_ALL)

add_library(screenlate_mnn SHARED mnn_network.cpp)

target_compile_options(screenlate_mnn PRIVATE -fvisibility=hidden -Wall)
target_include_directories(screenlate_mnn PRIVATE MNN/include)

# MNN registers its operators and backends from static initializers, so the whole archive is linked; unused sections
# and MNN's symbols are dropped from the result.
target_link_libraries(screenlate_mnn PRIVATE -Wl,--whole-archive MNN -Wl,--no-whole-archive log)
target_link_options(screenlate_mnn PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -s)
