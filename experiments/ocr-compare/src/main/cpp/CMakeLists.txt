cmake_minimum_required(VERSION 3.22)
project(mnnocr CXX)

set(CMAKE_CXX_STANDARD 17)
set(MNN_ROOT "" CACHE PATH "Folder with MNN's source (MNN-*) and a static build per ABI (mnn-<abi>)")
file(GLOB MNN_SOURCE LIST_DIRECTORIES true "${MNN_ROOT}/mnn-src/MNN-*")

add_library(mnn STATIC IMPORTED)
set_target_properties(mnn PROPERTIES IMPORTED_LOCATION "${MNN_ROOT}/mnn-${ANDROID_ABI}/libMNN.a")

add_library(mnnocr SHARED mnn_model.cpp)
target_include_directories(mnnocr PRIVATE "${MNN_SOURCE}/include")
target_link_libraries(mnnocr -Wl,--whole-archive mnn -Wl,--no-whole-archive log)
target_link_options(mnnocr PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -s)
