cmake_minimum_required(VERSION 3.31)

project(screenlate_hoshidicts LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Import and lookup are far too slow unoptimized, so debug builds are optimized as well.
add_compile_options(-O2)

add_subdirectory(hoshidicts)

add_library(screenlate_hoshidicts SHARED
    jni_bridge.cpp
)

target_compile_options(screenlate_hoshidicts PRIVATE -fvisibility=hidden -Wall)

target_link_libraries(screenlate_hoshidicts PRIVATE
    hoshidicts
    glaze::glaze
    log
)
