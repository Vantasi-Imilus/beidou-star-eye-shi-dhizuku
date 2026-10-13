cmake_minimum_required(VERSION 3.31)

project("shizuku")

set(CMAKE_CXX_STANDARD 17)

add_compile_options(-Werror=format -fdata-sections -ffunction-sections -fno-exceptions -fno-rtti -fno-threadsafe-statics)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    message("Builing Release...")

    add_compile_options(-Os -flto -fvisibility=hidden -fvisibility-inlines-hidden)
    add_link_options(-flto -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all)
else ()
    message("Builing Debug...")

    add_definitions(-DDEBUG)
endif ()

find_package(boringssl REQUIRED CONFIG)
find_package(cxx REQUIRED CONFIG)

add_executable(libshizuku.so
        starter.cpp misc.cpp selinux.cpp cgroup.cpp)

target_link_libraries(libshizuku.so log cxx::cxx)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET libshizuku.so POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libshizuku.so")
endif ()

add_library(adb SHARED
        adb_pairing.cpp misc.cpp)

target_link_libraries(adb log boringssl::crypto_static cxx::cxx)

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_custom_command(TARGET adb POST_BUILD
            COMMAND ${CMAKE_STRIP} --remove-section=.comment "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libadb.so")
endif ()

# 天权中枢：伪终端助手（为终端会话分配 pty）
add_library(pty SHARED
        tianquan_pty.c)
target_link_libraries(pty log)

# 天权中枢：伪终端中继（可执行文件，由 Shizuku / Dhizuku / su 以特权身份启动，
# 内部再开 pty 跑 shell 并双向透传，使宿主获得「特权 + 完整 pty 语义」的交互终端）
add_executable(libtianquanpty.so tianquan_pty_relay.c)
target_link_libraries(libtianquanpty.so log)
