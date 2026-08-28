cmake_minimum_required(VERSION 3.22.1)

project("localhost_ai_engine" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED TRUE)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED TRUE)

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}"   CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}" CACHE STRING "" FORCE)

if(DEFINED ANDROID_ABI)
    message(STATUS "LocalHostAI: Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
        set(GGML_VULKAN ON CACHE BOOL "" FORCE)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
        set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

# Locate glslc from Android NDK
if(DEFINED ANDROID_NDK AND NOT DEFINED Vulkan_GLSLC_EXECUTABLE)
    if(EXISTS "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc")
        set(Vulkan_GLSLC_EXECUTABLE "${ANDROID_NDK}/shader-tools/linux-x86_64/glslc" CACHE FILEPATH "glslc executable" FORCE)
    endif()
endif()

include(FetchContent)

# Fetch Vulkan-Headers and SPIRV-Headers for Vulkan C++ compilation
FetchContent_Declare(
    vulkan_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/Vulkan-Headers.git
    GIT_TAG        v1.3.296
)

FetchContent_Declare(
    spirv_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Headers.git
    GIT_TAG        vulkan-sdk-1.3.296.0
)

FetchContent_MakeAvailable(vulkan_headers spirv_headers)

export(TARGETS SPIRV-Headers NAMESPACE SPIRV-Headers:: FILE "${CMAKE_CURRENT_BINARY_DIR}/SPIRV-HeadersConfig.cmake")
set(SPIRV-Headers_DIR "${CMAKE_CURRENT_BINARY_DIR}" CACHE PATH "" FORCE)

include_directories(SYSTEM
    ${vulkan_headers_SOURCE_DIR}/include
    ${spirv_headers_SOURCE_DIR}/include
)

# Options for llama.cpp & ggml
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

set(LLAMA_SRC ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(localhost_ai_engine SHARED
    localhost_ai_engine.cpp
)

target_compile_definitions(localhost_ai_engine PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
    GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
    GGML_USE_VULKAN=$<BOOL:${GGML_VULKAN}>
)

target_include_directories(localhost_ai_engine PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/ggml/src
)

target_link_libraries(localhost_ai_engine
    llama
    llama-common
    android
    log
)
