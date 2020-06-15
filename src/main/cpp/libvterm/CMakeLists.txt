set(SRC_FILES
        src/keyboard.c
        src/vterm.c
        src/encoding.c
        src/parser.c
        src/unicode.c
        src/pen.c
        src/screen.c
        src/state.c
        src/mouse.c
        )

include_directories(include)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99 -Wall -Werror -Wno-missing-field-initializers\
                                    -Wno-sign-compare -Wno-unused-function -Wno-unused-parameter\
                                    -DNDEBUG")

add_library(vterm_static STATIC ${SRC_FILES})

target_link_libraries(vterm_static)
