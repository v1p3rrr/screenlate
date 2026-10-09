cmake_minimum_required(VERSION 3.31)

project(screenlate_hoshidicts LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Import and lookup are far too slow unoptimized, so debug builds are optimized as well.
add_compile_options(-O2)

add_subdirectory(hoshidicts)

# Experiment: QuickJS runs Yomitan's language code (text processors, deinflection) for languages other than Japanese.
add_library(quickjs STATIC
    quickjs/quickjs.c
    quickjs/libregexp.c
    quickjs/libunicode.c
    quickjs/dtoa.c
)
target_compile_definitions(quickjs PRIVATE _GNU_SOURCE)
target_compile_options(quickjs PRIVATE -w)
target_include_directories(quickjs PUBLIC quickjs)

add_library(screenlate_hoshidicts SHARED
    jni_bridge.cpp
    language/yomitan_language.cpp
    language/candidate_lookup.cpp
)

target_compile_options(screenlate_hoshidicts PRIVATE -fvisibility=hidden -Wall)

target_link_libraries(screenlate_hoshidicts PRIVATE
    hoshidicts
    glaze::glaze
    quickjs
    log
)
