# Experiment harness: langlookup <bundle.js> <language> <word|letter> <imported dict dir>... -- <text>...
# Build for the emulator with the NDK toolchain, e.g.:
#   cmake -G Ninja -B build -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=x86_64 \
#     -DANDROID_PLATFORM=31 -DCMAKE_BUILD_TYPE=Release && ninja -C build
# then push build/langlookup, the bundle (assets/yomitan-language/<iso>.js) and dictionaries imported with
# hoshidicts-cli ("hd import x.zip") to /data/local/tmp.
cmake_minimum_required(VERSION 3.31)
project(langlookup LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_C_STANDARD 11)
add_compile_options(-O2)
set(REPO_CPP ${CMAKE_CURRENT_SOURCE_DIR}/../../../dictionary/engine-hoshidicts/src/main/cpp)
set(QJS ${REPO_CPP}/quickjs)
add_subdirectory(${REPO_CPP}/hoshidicts hoshidicts)
add_library(qjs STATIC ${QJS}/quickjs.c ${QJS}/libregexp.c ${QJS}/libunicode.c ${QJS}/dtoa.c)
target_compile_definitions(qjs PRIVATE _GNU_SOURCE)
target_include_directories(qjs PUBLIC ${QJS})
add_executable(langlookup langlookup.cpp ${REPO_CPP}/language/yomitan_language.cpp ${REPO_CPP}/language/candidate_lookup.cpp)
target_include_directories(langlookup PRIVATE ${REPO_CPP}/language)
target_link_libraries(langlookup PRIVATE hoshidicts glaze::glaze qjs m)
