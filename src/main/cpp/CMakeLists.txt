cmake_minimum_required(VERSION 3.4.1)

add_subdirectory(libvterm)

include_directories(libvterm/include)

set(SRC_FILES aterm_terminal.c jni_init.c)
add_library(aterm SHARED ${SRC_FILES})

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -fvisibility=hidden -DNDEBUG")

find_library(log-lib log)


target_link_libraries(aterm ${log-lib} vterm_static)

