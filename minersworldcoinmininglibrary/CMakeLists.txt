cmake_minimum_required(VERSION 3.4.1)

# x86 linker flags (optional)
if(ANDROID_ABI STREQUAL "x86")
    set(CMAKE_SHARED_LINKER_FLAGS "-Wall -v")
endif()

# Global optimization flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fomit-frame-pointer -ffast-math -funroll-loops -ftree-vectorize -fno-strict-aliasing -fno-stack-protector")
set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -O3")

# CPU-specific optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8-a+crypto -mtune=cortex-a75")
endif()

if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon")
endif()

# Include cpuminer library
add_subdirectory(${CMAKE_SOURCE_DIR}/src/main/cpp/libcpuminer)

# Create sugarminer library
add_library(
    sugarminer
    SHARED
    src/main/cpp/sugarminer.cpp
)

# Find Android log library
find_library(
    log-lib
    log
)

# Link libraries
target_link_libraries(
    sugarminer
    ${log-lib}
    cpuminer
)