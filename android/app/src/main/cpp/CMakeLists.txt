cmake_minimum_required(VERSION 3.22.1)
project(localassistant_whisper LANGUAGES C CXX)

set_property(GLOBAL PROPERTY JOB_POOLS
    localassistant_compile_pool=2
    localassistant_link_pool=1)
set(CMAKE_JOB_POOL_COMPILE localassistant_compile_pool)
set(CMAKE_JOB_POOL_LINK localassistant_link_pool)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_STATIC ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)

# Keep the packaged arm64 library compatible with the Android arm64-v8a
# baseline. Older devices such as Snapdragon 845 do not support dotprod and
# terminate the process with SIGILL if those instructions are emitted.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GGML_CPU_ARM_ARCH "armv7-a" CACHE STRING "" FORCE)
endif()
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

set(WHISPER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/whisper.cpp")
add_subdirectory("${WHISPER_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp")

add_library(localassistant_whisper SHARED whisper_jni.cpp)
target_include_directories(localassistant_whisper PRIVATE "${WHISPER_DIR}/include")
target_link_libraries(localassistant_whisper PRIVATE whisper android log)
target_compile_options(localassistant_whisper PRIVATE -O3 -DNDEBUG -fvisibility=hidden)
