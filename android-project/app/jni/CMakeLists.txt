cmake_minimum_required(VERSION 3.6)

project(WILIWILI_ANDROID)

# armeabi-v7a requires cpufeatures library
# include(AndroidNdkModules)
# android_ndk_import_module_cpufeatures()

set(BUILD_SHARED_LIBS ON CACHE BOOL "")

# SDL sources are in a subfolder named "SDL"
add_subdirectory(SDL)

# Compilation of companion libraries
#add_subdirectory(SDL_image)
#add_subdirectory(SDL_mixer)
#add_subdirectory(SDL_ttf)


SET(PLATFORM_ANDROID ON CACHE BOOL "")
SET(USE_LIBROMFS ON CACHE BOOL "")
SET(DISABLE_OPENCC ON CACHE BOOL "")
SET(DISABLE_WEBP ON CACHE BOOL "")
SET(LIBROMFS_PREBUILT_GENERATOR "${CMAKE_CURRENT_SOURCE_DIR}/borealis/libromfs-generator" CACHE STRING "")
SET(WILIWILI_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." CACHE PATH "wiliwili source root")
get_filename_component(WILIWILI_ROOT "${WILIWILI_ROOT}" ABSOLUTE)

if (NOT EXISTS "${LIBROMFS_PREBUILT_GENERATOR}")
    message(FATAL_ERROR "libromfs-generator has not been built, please refer to borealis/build_libromfs_generator.sh for more information")
endif()

add_subdirectory("${WILIWILI_ROOT}" "${CMAKE_CURRENT_BINARY_DIR}/wiliwili")
