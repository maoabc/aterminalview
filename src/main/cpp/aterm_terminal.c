

#include "jni.h"

#include <fcntl.h>
#include <stdio.h>
#include <termios.h>
#include <unistd.h>

#include <vterm.h>

#include <string.h>
#include <errno.h>
#include <pthread.h>

#include "utils/log.h"


#ifdef __cplusplus
extern "C" {
#endif


#define LOG_TAG "Terminal"

#define DEBUG_CALLBACKS 0
#define DEBUG_SCROLLBACK 0

typedef int status_t;
typedef short unsigned int dimen_t;

#ifdef _LP64
#define jlong_to_ptr(a) ((void*)(a))
#define ptr_to_jlong(a) ((jlong)(a))
#else
#define jlong_to_ptr(a) ((void*)(int)(a))
#define ptr_to_jlong(a) ((jlong)(int)(a))
#endif

/*
 * Callback class reference
 */
static jclass terminalCallbacksClass;

/*
 * Callback methods
 */
static jmethodID damageMethod;
static jmethodID moveRectMethod;
static jmethodID moveCursorMethod;
static jmethodID setTermPropBooleanMethod;
static jmethodID setTermPropIntMethod;
static jmethodID setTermPropStringMethod;
static jmethodID setTermPropColorMethod;
static jmethodID bellMethod;

/*
 * CellRun class
 */
static jclass cellRunClass;
static jfieldID cellRunDataField;
static jfieldID cellRunWidthField;
static jfieldID cellRunDataSizeField;
static jfieldID cellRunColSizeField;
static jfieldID cellRunFgField;
static jfieldID cellRunBgField;
static jfieldID cellRunBoldField;
static jfieldID cellRunUnderlineField;
static jfieldID cellRunStrikeField;

static jclass outputCallbackClass;
static jmethodID outputWriteMethod;


extern JavaVM *javaVM;

static JNIEnv *getJNIEnv() {
    JNIEnv *env;
    if ((*javaVM)->GetEnv(javaVM, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        ALOGE("error GetEnv");
        return NULL;
    }
    return env;
}


typedef struct {
    uint32_t code;
    VTermScreenCellAttrs attrs;
    char width;
    VTermColor fg, bg;
} CellAttr;


typedef struct {
    dimen_t cols;

    CellAttr *cells;
} ScrollbackLine;

static inline ScrollbackLine *new_scrollback_line(dimen_t cols) {
    ScrollbackLine *line = (ScrollbackLine *) malloc(sizeof(ScrollbackLine));
    line->cols = cols;
    line->cells = (CellAttr *) malloc(sizeof(CellAttr) * cols);
    return line;
}

static inline void free_scrollback_line(ScrollbackLine *line) {
    if (line) {
        free(line->cells);
        free(line);
    }
}

static inline dimen_t scroll_line_copy_from(ScrollbackLine *line, dimen_t cols,
                                            const VTermScreenCell *cells) {
    dimen_t n = line->cols > cols ? cols : line->cols;

    for (int i = 0; i < n; ++i) {
        line->cells[i].code = cells[i].chars[0];
        line->cells[i].width = cells[i].width;

        line->cells[i].attrs = cells[i].attrs;
        line->cells[i].fg = cells[i].fg;
        line->cells[i].bg = cells[i].bg;
    }
    return n;
}

static inline dimen_t
scroll_line_copy_to(ScrollbackLine *line, dimen_t cols, VTermScreenCell *cells) {
    dimen_t n = cols > line->cols ? line->cols : cols;

    for (int i = 0; i < n; ++i) {
        cells[i].chars[0] = line->cells[i].code;
        cells[i].chars[1] = 0;

        cells[i].width = line->cells[i].width;

        cells[i].attrs = line->cells[i].attrs;
        cells[i].fg = line->cells[i].fg;
        cells[i].bg = line->cells[i].bg;
    }
    return n;
}

static inline void scroll_line_get_cell(ScrollbackLine *line, dimen_t col, VTermScreenCell *cell) {
    cell->chars[0] = line->cells[col].code;
    cell->chars[1] = 0;

    cell->width = line->cells[col].width;

    cell->attrs = line->cells[col].attrs;
    cell->fg = line->cells[col].fg;
    cell->bg = line->cells[col].bg;
}

/*
 * Terminal session
 */
typedef struct {
    VTerm *vt;

    jobject callbacks;
    jobject outputCallback;

    dimen_t rows;
    dimen_t cols;

    ScrollbackLine **scrollLines;
    dimen_t scrollCur;
    dimen_t scrollSize;

    jbyteArray buffer;
    jint bufferSize;

} Terminal;

static Terminal *
new_terminal(jobject callbacks, jobject outputCallback,
             dimen_t rows, dimen_t cols, dimen_t scrollRows, int fg, int bg);

static void free_terminal(Terminal *term);


static bool terminal_dispatchCharacter(Terminal *term, int mod, int character);

static bool terminal_dispatchKey(Terminal *term, int mod, int key);

static status_t terminal_resize(Terminal *term, dimen_t rows, dimen_t cols, dimen_t scrollRows);

static status_t terminal_onPushline(Terminal *term, dimen_t cols, const VTermScreenCell *cells);

static status_t terminal_onPopline(Terminal *term, dimen_t cols, VTermScreenCell *cells);

static void terminal_getCellLocked(Terminal *term, VTermPos pos, VTermScreenCell *cell);

/*
 * VTerm event handlers
 */

static int term_damage(VTermRect rect, void *user) {
    Terminal *term = (Terminal *) user;
#if DEBUG_CALLBACKS
    ALOGW("term_damage");
#endif

    JNIEnv *env = getJNIEnv();
    return (*env)->CallIntMethod(env, term->callbacks, damageMethod, rect.start_row,
                                 rect.end_row,
                                 rect.start_col, rect.end_col);
}

static int term_moverect(VTermRect dest, VTermRect src, void *user) {
    Terminal *term = (Terminal *) user;
#if DEBUG_CALLBACKS
    ALOGW("term_moverect");
#endif

    JNIEnv *env = getJNIEnv();
    return (*env)->CallIntMethod(env, term->callbacks, moveRectMethod,
                                 dest.start_row, dest.end_row, dest.start_col, dest.end_col,
                                 src.start_row, src.end_row, src.start_col, src.end_col);
}

static int term_movecursor(VTermPos pos, VTermPos oldpos, int visible, void *user) {
    Terminal *term = (Terminal *) user;
#if DEBUG_CALLBACKS
    ALOGW("term_movecursor new pos(%d,%d)  old(%d,%d) visible %d", pos.col, pos.row, oldpos.col,
          oldpos.row, visible);
#endif

    JNIEnv *env = getJNIEnv();
    return (*env)->CallIntMethod(env, term->callbacks, moveCursorMethod, pos.row,
                                 pos.col, oldpos.row, oldpos.col, visible);
}

static int term_settermprop(VTermProp prop, VTermValue *val, void *user) {
    Terminal *term = (Terminal *) user;
#if DEBUG_CALLBACKS
    ALOGW("term_settermprop %d %d", prop, val->boolean);
#endif

    JNIEnv *env = getJNIEnv();
    switch (vterm_get_prop_type(prop)) {
        case VTERM_VALUETYPE_BOOL: {
            return (*env)->CallIntMethod(env, term->callbacks, setTermPropBooleanMethod,
                                         (int) prop,
                                         val->boolean ? JNI_TRUE : JNI_FALSE);
        }
        case VTERM_VALUETYPE_INT: {
            return (*env)->CallIntMethod(env, term->callbacks, setTermPropIntMethod,
                                         (int) prop,
                                         val->number);
        }
        case VTERM_VALUETYPE_STRING: {
            return (*env)->CallIntMethod(env, term->callbacks, setTermPropStringMethod,
                                         (int) prop,
                                         (*env)->NewStringUTF(env, val->string));
        }
        case VTERM_VALUETYPE_COLOR: {
            return (*env)->CallIntMethod(env, term->callbacks, setTermPropColorMethod,
                                         (int) prop,
                                         val->color.rgb.red,
                                         val->color.rgb.green, val->color.rgb.blue);
        }
        default:
            ALOGE("unknown callback type");
            return 0;
    }
}


static int term_bell(void *user) {
    Terminal *term = (Terminal *) user;
#if DEBUG_CALLBACKS
    ALOGW("term_bell");
#endif

    JNIEnv *env = getJNIEnv();
    return (*env)->CallIntMethod(env, term->callbacks, bellMethod);
}

static int term_sb_pushline(int cols, const VTermScreenCell *cells, void *user) {
    Terminal *term = (Terminal *) user;
#if DEBUG_CALLBACKS
    ALOGW("term_sb_pushline");
#endif

    return terminal_onPushline(term, (dimen_t) cols, cells);
}

static int term_sb_popline(int cols, VTermScreenCell *cells, void *user) {
    Terminal *term = (Terminal *) user;
#if DEBUG_CALLBACKS
    ALOGW("term_sb_popline");
#endif

    return terminal_onPopline(term, (dimen_t) cols, cells);
}

static VTermScreenCallbacks cb = {
        .damage = term_damage,
        .moverect = term_moverect,
        .movecursor = term_movecursor,
        .settermprop = term_settermprop,
        .bell = term_bell,
        .resize = NULL,
        .sb_pushline = term_sb_pushline,
        .sb_popline = term_sb_popline,
};


static void output_callback(const char *s, size_t len, void *user) {
    Terminal *term = (Terminal *) user;
    JNIEnv *env = getJNIEnv();
    jbyteArray bytes = term->buffer;
    bool local = false;
    if (len > term->bufferSize) {
        bytes = (*env)->NewByteArray(env, len);
        local = true;
    }

    (*env)->SetByteArrayRegion(env, bytes, 0, len, (const jbyte *) s);
    (*env)->CallVoidMethod(env, term->outputCallback, outputWriteMethod, bytes, (jint) len);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }

    if (local) {
        (*env)->DeleteLocalRef(env, bytes);
    }
}


static Terminal *new_terminal(jobject callbacks, jobject outputCallback,
                              dimen_t rows, dimen_t cols, dimen_t scrollRows, int fg, int bg) {
    Terminal *term = (Terminal *) malloc(sizeof(Terminal));
    JNIEnv *env = getJNIEnv();

    term->callbacks = (*env)->NewGlobalRef(env, callbacks);
    term->outputCallback = (*env)->NewGlobalRef(env, outputCallback);

    term->bufferSize = 4 * 1024;
    term->buffer = (*env)->NewGlobalRef(env, (*env)->NewByteArray(env, term->bufferSize));

    term->rows = rows;
    term->cols = cols;
    term->scrollCur = 0;
    term->scrollSize = scrollRows;

    term->scrollLines = (ScrollbackLine **) malloc(sizeof(ScrollbackLine *) * term->scrollSize);

    memset(term->scrollLines, 0, sizeof(ScrollbackLine *) * term->scrollSize);

    /* Create VTerm */
    VTerm *vt = vterm_new(term->rows, term->cols);
    vterm_set_utf8(vt, 1);
    term->vt = vt;

    vterm_output_set_callback(vt, output_callback, term);

    VTermColor color_fg;
    VTermColor color_bg;
    vterm_color_rgb(&color_fg, (uint8_t) ((fg >> 16) & 0xff),
                    (uint8_t) ((fg >> 8) & 0xff),
                    (uint8_t) (fg & 0xff));
    vterm_color_rgb(&color_bg, (uint8_t) ((bg >> 16) & 0xff),
                    (uint8_t) ((bg >> 8) & 0xff),
                    (uint8_t) (bg & 0xff));
    vterm_state_set_default_colors(vterm_obtain_state(term->vt), &color_fg, &color_bg);


    /* Set up screen */
    VTermScreen *vts = vterm_obtain_screen(vt);
    vterm_screen_enable_altscreen(vts, 1);
    vterm_screen_set_callbacks(vts, &cb, term);
    vterm_screen_set_damage_merge(vts, VTERM_DAMAGE_SCROLL);
    vterm_screen_reset(vts, 1);

    return term;
}

static void free_terminal(Terminal *term) {
    if (!term) {
        return;
    }

    vterm_free(term->vt);

    for (int i = 0; i < term->scrollSize; ++i) {
        free_scrollback_line(term->scrollLines[i]);
    }
    free(term->scrollLines);


    JNIEnv *env = getJNIEnv();
    (*env)->DeleteGlobalRef(env, term->callbacks);

    (*env)->DeleteGlobalRef(env, term->outputCallback);

    (*env)->DeleteGlobalRef(env, term->buffer);

    free(term);

}


bool terminal_dispatchCharacter(Terminal *term, int mod, int character) {
    vterm_keyboard_unichar(term->vt, (uint32_t) character, (VTermModifier) mod);
    return true;
}

static bool terminal_dispatchKey(Terminal *term, int mod, int key) {
    vterm_keyboard_key(term->vt, (VTermKey) key, (VTermModifier) mod);
    return true;
}


static status_t terminal_resize(Terminal *term, dimen_t rows, dimen_t cols, dimen_t scrollRows) {

#if DEBUG_CALLBACKS
    ALOGD("resize(%d, %d, %d)", rows, cols, scrollRows);
#endif

    term->rows = rows;
    term->cols = cols;

    if (scrollRows > term->scrollSize) {//新滑动缓存大于原来的，直接从后往前复制原来的到新状态
        ScrollbackLine **scrollLines = (ScrollbackLine **) malloc(
                sizeof(ScrollbackLine *) * scrollRows);

        dimen_t delta = scrollRows - term->scrollSize;
        for (int n = term->scrollSize - 1; n >= 0; --n) {
            scrollLines[n + delta] = term->scrollLines[n];
        }

        free(term->scrollLines);
        term->scrollLines = scrollLines;
        term->scrollSize = scrollRows;

    } else if (scrollRows < term->scrollSize) {
        ScrollbackLine **scrollLines = (ScrollbackLine **) malloc(
                sizeof(ScrollbackLine *) * scrollRows);

        dimen_t delta = term->scrollSize - scrollRows;
        for (int i = 0; i < delta; ++i) {//新滑动小于原来的，释放多余的缓存，其他的复制到新状态
            free_scrollback_line(term->scrollLines[i]);
        }

        for (int n = scrollRows - 1; n >= 0; --n) {
            scrollLines[n] = term->scrollLines[n + delta];
        }

        free(term->scrollLines);
        term->scrollLines = scrollLines;
        term->scrollSize = scrollRows;
    }


    vterm_set_size(term->vt, rows, cols);

    return 0;
}

static status_t terminal_onPushline(Terminal *term, dimen_t cols, const VTermScreenCell *cells) {
    ScrollbackLine *line = NULL;
#if DEBUG_CALLBACKS
    ALOGD("onPushline %d", term->scrollCur);
#endif
    if (term->scrollCur == term->scrollSize) {
        /* Recycle old row if it's the right size */
        if (term->scrollLines[term->scrollCur - 1]->cols == cols) {
            line = term->scrollLines[term->scrollCur - 1];
        } else {
            free_scrollback_line(term->scrollLines[term->scrollCur - 1]);
        }

        memmove(term->scrollLines + 1, term->scrollLines,
                sizeof(ScrollbackLine *) * (term->scrollCur - 1));
    } else if (term->scrollCur > 0) {
        memmove(term->scrollLines + 1, term->scrollLines,
                sizeof(ScrollbackLine *) * term->scrollCur);
    }

    if (line == NULL) {
        line = new_scrollback_line(cols);
    }

    term->scrollLines[0] = line;

    if (term->scrollCur < term->scrollSize) {
        term->scrollCur++;
    }

    scroll_line_copy_from(line, cols, cells);
    return 1;
}

static status_t terminal_onPopline(Terminal *term, dimen_t cols, VTermScreenCell *cells) {
#if DEBUG_CALLBACKS
    ALOGD("onPopline %d", term->scrollCur);
#endif
    if (term->scrollCur == 0) {
        return 0;
    }

    ScrollbackLine *line = term->scrollLines[0];
    term->scrollCur--;
    memmove(term->scrollLines, term->scrollLines + 1, sizeof(ScrollbackLine *) * term->scrollCur);

    dimen_t n = scroll_line_copy_to(line, cols, cells);
    for (dimen_t col = n; col < cols; col++) {
        cells[col].chars[0] = 0;
        cells[col].width = 1;
    }

    free_scrollback_line(line);

    return 1;
}

static void terminal_getCellLocked(Terminal *term, VTermPos pos, VTermScreenCell *cell) {
    // The UI may be asking for cell data while the model is changing
    // underneath it, so we always fill with meaningful data.

    if (pos.row < 0) {
        size_t scrollRow = (size_t) (-pos.row);
        if (scrollRow > term->scrollCur) {
            // Invalid region above current scrollback
            cell->width = 1;
#if DEBUG_SCROLLBACK
            cell->bg.rgb.red = 255;
#endif
            return;
        }

        ScrollbackLine *line = term->scrollLines[scrollRow - 1];
        if ((size_t) pos.col < line->cols) {
            // Valid scrollback cell
            scroll_line_get_cell(line, (dimen_t) (pos.col), cell);
#if DEBUG_SCROLLBACK
            cell->bg.rgb.blue = 255;
#endif
            return;
        } else {
            // Extend last scrollback cell into invalid region
            scroll_line_get_cell(line, (dimen_t) (line->cols - 1), cell);
            cell->width = 1;
            cell->chars[0] = ' ';
#if DEBUG_SCROLLBACK
            cell->bg.rgb.green = 255;
#endif
            return;
        }
    }

    if ((size_t) pos.row >= term->rows) {
        // Invalid region below screen
        cell->width = 1;
#if DEBUG_SCROLLBACK
        cell->bg.rgb.red = 128;
#endif
        return;
    }

    // Valid screen cell
    vterm_screen_get_cell(vterm_obtain_screen(term->vt), pos, cell);
}


/*
 * JNI glue
 */

static jlong
aterm_terminal_Terminal_nativeInit(JNIEnv *env, jclass clazz,
                                   jobject callbacks, jobject outputCallback,
                                   jint rows, jint cols, jint scrollRows, jint fg, jint bg) {
    return ptr_to_jlong(new_terminal(callbacks, outputCallback,
                                     (dimen_t) rows, (dimen_t) cols, (dimen_t) scrollRows, fg, bg));
}

static jint aterm_terminal_Terminal_nativeDestroy(JNIEnv *env, jclass clazz, jlong ptr) {
    Terminal *term = jlong_to_ptr(ptr);
    free_terminal(term);
    return 0;
}


static jint aterm_terminal_Terminal_nativeResize(JNIEnv *env, jclass clazz, jlong ptr,
                                                 jint rows, jint cols, jint scrollRows) {
    Terminal *term = jlong_to_ptr(ptr);
    return terminal_resize(term, (dimen_t) rows, (dimen_t) cols, (dimen_t) scrollRows);
}

static inline int toArgb(const VTermColor *color) {
    return (0xff << 24 | color->rgb.red << 16 | color->rgb.green << 8 | color->rgb.blue);
}

static inline bool isCellStyleEqual(const VTermScreenCell *a, const VTermScreenCell *b) {
    if (vterm_color_is_equal(&a->fg, &b->fg) != 1) return false;
    if (vterm_color_is_equal(&a->bg, &b->bg) != 1) return false;

    if (a->attrs.bold != b->attrs.bold) return false;
    if (a->attrs.underline != b->attrs.underline) return false;
    if (a->attrs.italic != b->attrs.italic) return false;
    if (a->attrs.blink != b->attrs.blink) return false;
    if (a->attrs.reverse != b->attrs.reverse) return false;
    if (a->attrs.strike != b->attrs.strike) return false;
    if (a->attrs.font != b->attrs.font) return false;

    return true;
}

static jint aterm_terminal_Terminal_nativeGetCellRun(JNIEnv *env,
                                                     jclass clazz, jlong ptr, jint row,
                                                     jint col, jobject run) {
    Terminal *term = jlong_to_ptr(ptr);

    jintArray dataArray = (jintArray) (*env)->GetObjectField(env, run, cellRunDataField);
    jint *data = (*env)->GetIntArrayElements(env, dataArray, NULL);

    jbyteArray widthArray = (jbyteArray) (*env)->GetObjectField(env, run, cellRunWidthField);
    jbyte *widths = (*env)->GetByteArrayElements(env, widthArray, NULL);

    jsize dataLength = (*env)->GetArrayLength(env, dataArray);


    VTermScreenCell firstCell = {};
    VTermScreenCell cell;

    VTermPos pos = {
            .row = row,
            .col = col,
    };

    size_t dataSize = 0;
    size_t colSize = 0;
    while ((size_t) pos.col < term->cols) {
        memset(&cell, 0, sizeof(VTermScreenCell));
        terminal_getCellLocked(term, pos, &cell);

        if (colSize == 0) {
            VTermColor fg = cell.attrs.reverse ? cell.bg : cell.fg;
            VTermColor bg = cell.attrs.reverse ? cell.fg : cell.bg;

            VTermColor color;
            if (VTERM_COLOR_IS_INDEXED(&fg)) {
                vterm_state_get_palette_color(vterm_obtain_state(term->vt),
                                              fg.indexed.idx, &color);
                (*env)->SetIntField(env, run, cellRunFgField, toArgb(&color));
            } else {
                (*env)->SetIntField(env, run, cellRunFgField, toArgb(&fg));
            }
            if (VTERM_COLOR_IS_INDEXED(&bg)) {
                vterm_state_get_palette_color(vterm_obtain_state(term->vt),
                                              bg.indexed.idx, &color);
                (*env)->SetIntField(env, run, cellRunBgField, toArgb(&color));
            } else {
                (*env)->SetIntField(env, run, cellRunBgField, toArgb(&bg));
            }

            //bold
            (*env)->SetBooleanField(env, run, cellRunBoldField, (jboolean) (cell.attrs.bold == 1));
            //underline
            (*env)->SetBooleanField(env, run, cellRunUnderlineField,
                                    (jboolean) (cell.attrs.underline == 1));
            //strike
            (*env)->SetBooleanField(env, run, cellRunStrikeField,
                                    (jboolean) (cell.attrs.strike == 1));

            memcpy(&firstCell, &cell, sizeof(VTermScreenCell));
        } else {
            if (!isCellStyleEqual(&cell, &firstCell)) {
                break;
            }
        }

//        if (VTERM_COLOR_IS_INDEXED(&cell.fg)) {
//            ALOGD("color %d %d", VTERM_COLOR_IS_RGB(&cell.fg), VTERM_COLOR_IS_DEFAULT_FG(&cell.fg));
//        }

        // Only include cell chars if they fit into run
        const uint32_t rawCell = cell.chars[0];
        if (dataSize < dataLength) {
            widths[dataSize] = cell.width;
            data[dataSize++] = rawCell;


            colSize += cell.width;
            pos.col += cell.width;
        } else {
            break;
        }
    }

    (*env)->SetIntField(env, run, cellRunDataSizeField, (jint) dataSize);
    (*env)->SetIntField(env, run, cellRunColSizeField, (jint) colSize);

    (*env)->ReleaseIntArrayElements(env, dataArray, data, 0);

    (*env)->ReleaseByteArrayElements(env, widthArray, widths, 0);

    return 0;
}

static jint aterm_terminal_Terminal_nativeGetRows(JNIEnv *env, jclass clazz, jlong ptr) {
    Terminal *term = jlong_to_ptr(ptr);
    return term->rows;
}

static jint aterm_terminal_Terminal_nativeGetCols(JNIEnv *env, jclass clazz, jlong ptr) {
    Terminal *term = jlong_to_ptr(ptr);
    return term->cols;
}

static jint
aterm_terminal_Terminal_nativeGetScrollRows(JNIEnv *env, jclass clazz, jlong ptr) {
    Terminal *term = jlong_to_ptr(ptr);
    return term->scrollSize;
}

static jint
aterm_terminal_Terminal_nativeGetScrollCur(JNIEnv *env, jclass clazz, jlong ptr) {
    Terminal *term = jlong_to_ptr(ptr);
    return term->scrollCur;
}

static jboolean aterm_terminal_Terminal_nativeDispatchCharacter(JNIEnv *env, jclass clazz,
                                                                jlong ptr, jint mod, jint c) {
    Terminal *term = jlong_to_ptr(ptr);
    return (jboolean) terminal_dispatchCharacter(term, mod, c);
}

static jboolean aterm_terminal_Terminal_nativeDispatchKey(JNIEnv *env, jclass clazz,
                                                          jlong ptr, jint mod, jint c) {
    Terminal *term = jlong_to_ptr(ptr);
    return (jboolean) terminal_dispatchKey(term, mod, c);
}

static jint aterm_terminal_Terminal_nativeGetLineText(JNIEnv *env, jclass clazz,
                                                      jlong ptr, jint row,
                                                      jint startCol, jint endCol,
                                                      jintArray out) {
    Terminal *term = jlong_to_ptr(ptr);

    jint *codePoints = (*env)->GetIntArrayElements(env, out, NULL);
    jsize len = (*env)->GetArrayLength(env, out);
    VTermScreenCell cell;
    int count = 0;

    VTermPos pos = {
            .row = row,
            .col = startCol,
    };
    while (pos.col < endCol && count < len) {
        memset(&cell, 0, sizeof(VTermScreenCell));
        terminal_getCellLocked(term, pos, &cell);
        if (cell.chars[0] != 0 && cell.chars[0] != (uint32_t) -1) {
            codePoints[count] = cell.chars[0];
            count++;
        }
        pos.col += cell.width;
    }

    (*env)->ReleaseIntArrayElements(env, out, codePoints, 0);

    return count;
}

static void aterm_terminal_Terminal_nativeMouseMove(JNIEnv *env, jclass clazz,
                                                    jlong ptr, jint row, jint col,
                                                    jint mod) {
    Terminal *term = jlong_to_ptr(ptr);
    vterm_mouse_move(term->vt, row, col, (VTermModifier) mod);

}

static void aterm_terminal_Terminal_nativeMouseButton(JNIEnv *env, jclass clazz,
                                                      jlong ptr, jint button, jboolean pressed,
                                                      jint mod) {
    Terminal *term = jlong_to_ptr(ptr);
    vterm_mouse_button(term->vt, button, pressed, (VTermModifier) mod);

}

static jint aterm_terminal_Terminal_nativeGetValidCol(JNIEnv *env, jclass clazz,
                                                      jlong ptr, jint row, jint col) {

    Terminal *term = jlong_to_ptr(ptr);
    VTermPos pos = {
            .row = row,
            .col = 0,
    };
    VTermScreenCell cell;
    while (pos.col < term->cols) {
        memset(&cell, 0, sizeof(VTermScreenCell));
        terminal_getCellLocked(term, pos, &cell);

        const int cend = pos.col + cell.width;
        if (pos.col < col && col < cend) {
            return cend;
        }
        if (cend == col) {
            return col;
        }
        pos.col = cend;
    }
    return col;
}

static void aterm_terminal_Terminal_nativeSetDefaultColors(JNIEnv *env, jclass clazz,
                                                           jlong ptr, jintArray colors) {
    Terminal *term = jlong_to_ptr(ptr);
    jsize len = (*env)->GetArrayLength(env, colors);
    if (len != 2) {
        return;
    }
    jint *cls = (*env)->GetIntArrayElements(env, colors, NULL);
    const int default_fg = cls[0];
    const int default_bg = cls[1];
    (*env)->ReleaseIntArrayElements(env, colors, cls, JNI_ABORT);

    VTermColor color_fg;
    VTermColor color_bg;
    vterm_color_rgb(&color_fg, (uint8_t) ((default_fg >> 16) & 0xff),
                    (uint8_t) ((default_fg >> 8) & 0xff),
                    (uint8_t) (default_fg & 0xff));
    vterm_color_rgb(&color_bg, (uint8_t) ((default_bg >> 16) & 0xff),
                    (uint8_t) ((default_bg >> 8) & 0xff),
                    (uint8_t) (default_bg & 0xff));

    vterm_state_set_default_colors(vterm_obtain_state(term->vt), &color_fg,
                                   &color_bg);
//    vterm_state_reset(vterm_obtain_state(term->vt), 0);

}

static void aterm_terminal_Terminal_nativeGetDefaultColors(JNIEnv *env, jclass clazz,
                                                           jlong ptr, jintArray colors) {
    jint out_colors[2];
    Terminal *term = jlong_to_ptr(ptr);

    jsize len = (*env)->GetArrayLength(env, colors);
    if (len != 2) {
        return;
    }

    VTermColor color_fg;
    VTermColor color_bg;
    vterm_state_get_default_colors(vterm_obtain_state(term->vt),
                                   &color_fg, &color_bg);

    if (VTERM_COLOR_IS_DEFAULT_FG(&color_fg)) {
        out_colors[0] = toArgb(&color_fg);
    }
    if (VTERM_COLOR_IS_DEFAULT_BG(&color_bg)) {
        out_colors[1] = toArgb(&color_bg);
    }
    (*env)->SetIntArrayRegion(env, colors, 0, 2, out_colors);
}

static int aterm_terminal_Terminal_nativeInputWrite(JNIEnv *env, jclass clazz, jlong ptr,
                                                    jbyteArray data, jint off, jint len) {

    Terminal *term = jlong_to_ptr(ptr);

    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);

    size_t ret = vterm_input_write(term->vt, (const char *) (bytes + off),
                                   (size_t) len);
    vterm_screen_flush_damage(vterm_obtain_screen(term->vt));

    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return ret;
}

static int aterm_terminal_Terminal_nativeWordOffset(JNIEnv *env, jclass clazz, jlong ptr,
                                                    jint row, int col, jint dir) {

    Terminal *term = jlong_to_ptr(ptr);
    VTermPos pos = {
            .row = row,
            .col = col,
    };
    VTermScreenCell cell;
    while (pos.col >= 0 && pos.col < term->cols) {
        memset(&cell, 0, sizeof(VTermScreenCell));
        terminal_getCellLocked(term, pos, &cell);

//        ALOGD("cell %d,dir=%d", cell.chars[0], dir);
        if (cell.chars[0] == ' ') {
            return dir > 0 ? pos.col : pos.col + 1;
        }
        if (cell.chars[0] == '\0') {
            return pos.col;
        }

        pos.col += dir > 0 ? 1 : -1;
    }
    if (pos.col < 0) {
        return 0;
    }
    return pos.col;
}

static void initConstant(JNIEnv *env, jclass c, const char *fieldName, int value) {
    jfieldID field = (*env)->GetStaticFieldID(env, c, fieldName, "I");
    (*env)->SetStaticIntField(env, c, field, value);
}

#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))

static jboolean
registerNativeMethods(JNIEnv *env, const char *cls_name, JNINativeMethod *methods, jint size) {
    jclass clazz = (*env)->FindClass(env, cls_name);
    if (clazz == NULL) {
        return JNI_FALSE;
    }
    if ((*env)->RegisterNatives(env, clazz, methods, size) < 0) {
        (*env)->DeleteLocalRef(env, clazz);
        return JNI_FALSE;
    }
    (*env)->DeleteLocalRef(env, clazz);

    return JNI_TRUE;
}

static JNINativeMethod gMethods[] = {
        {"nativeInit",              "(Laterm/terminal/TerminalCallbacks;Laterm/terminal/OutputCallback;IIIII)J",
                                                                         (void *) aterm_terminal_Terminal_nativeInit},

        {"nativeDestroy",           "(J)I",                              (void *) aterm_terminal_Terminal_nativeDestroy},


        {"nativeResize",            "(JIII)I",                           (void *) aterm_terminal_Terminal_nativeResize},

        {"nativeGetCellRun",        "(JIILaterm/terminal/ScreenCell;)I", (void *) aterm_terminal_Terminal_nativeGetCellRun},

        {"nativeGetRows",           "(J)I",                              (void *) aterm_terminal_Terminal_nativeGetRows},

        {"nativeGetCols",           "(J)I",                              (void *) aterm_terminal_Terminal_nativeGetCols},

        {"nativeGetScrollRows",     "(J)I",                              (void *) aterm_terminal_Terminal_nativeGetScrollRows},

        {"nativeGetScrollCur",      "(J)I",                              (void *) aterm_terminal_Terminal_nativeGetScrollCur},

        {"nativeDispatchCharacter", "(JII)Z",                            (void *) aterm_terminal_Terminal_nativeDispatchCharacter},

        {"nativeDispatchKey",       "(JII)Z",                            (void *) aterm_terminal_Terminal_nativeDispatchKey},

        {"nativeGetLineText",       "(JIII[I)I",                         (void *) aterm_terminal_Terminal_nativeGetLineText},

        {"nativeMouseMove",         "(JIII)V",                           (void *) aterm_terminal_Terminal_nativeMouseMove},

        {"nativeMouseButton",       "(JIZI)V",                           (void *) aterm_terminal_Terminal_nativeMouseButton},

        {"nativeGetValidCol",       "(JII)I",                            (void *) aterm_terminal_Terminal_nativeGetValidCol},

        {"nativeSetDefaultColors",  "(J[I)V",                            (void *) aterm_terminal_Terminal_nativeSetDefaultColors},

        {"nativeGetDefaultColors",  "(J[I)V",                            (void *) aterm_terminal_Terminal_nativeGetDefaultColors},

        {"nativeInputWrite",        "(J[BII)I",                          (void *) aterm_terminal_Terminal_nativeInputWrite},

        {"nativeWordOffset",        "(JIII)I",                           (void *) aterm_terminal_Terminal_nativeWordOffset},
};


int register_aterm_terminal_Terminal(JNIEnv *env) {
    jclass localClass = (*env)->FindClass(env, "aterm/terminal/TerminalCallbacks");

    terminalCallbacksClass = (*env)->NewGlobalRef(env, localClass);

    damageMethod = (*env)->GetMethodID(env, terminalCallbacksClass, "damage", "(IIII)I");
    moveRectMethod = (*env)->GetMethodID(env, terminalCallbacksClass, "moveRect", "(IIIIIIII)I");
    moveCursorMethod = (*env)->GetMethodID(env, terminalCallbacksClass, "moveCursor",
                                           "(IIIII)I");
    setTermPropBooleanMethod = (*env)->GetMethodID(env, terminalCallbacksClass,
                                                   "setTermPropBoolean", "(IZ)I");
    setTermPropIntMethod = (*env)->GetMethodID(env, terminalCallbacksClass, "setTermPropInt",
                                               "(II)I");
    setTermPropStringMethod = (*env)->GetMethodID(env, terminalCallbacksClass, "setTermPropString",
                                                  "(ILjava/lang/String;)I");
    setTermPropColorMethod = (*env)->GetMethodID(env, terminalCallbacksClass, "setTermPropColor",
                                                 "(IIII)I");
    bellMethod = (*env)->GetMethodID(env, terminalCallbacksClass, "bell", "()I");

    (*env)->DeleteLocalRef(env, localClass);

    jclass cellRunLocal = (*env)->FindClass(env, "aterm/terminal/ScreenCell");
    cellRunClass = (*env)->NewGlobalRef(env, cellRunLocal);

    cellRunDataField = (*env)->GetFieldID(env, cellRunClass, "data", "[I");
    cellRunWidthField = (*env)->GetFieldID(env, cellRunClass, "widths", "[B");
    cellRunDataSizeField = (*env)->GetFieldID(env, cellRunClass, "dataSize", "I");
    cellRunColSizeField = (*env)->GetFieldID(env, cellRunClass, "colSize", "I");
    cellRunFgField = (*env)->GetFieldID(env, cellRunClass, "fg", "I");
    cellRunBgField = (*env)->GetFieldID(env, cellRunClass, "bg", "I");
    cellRunBoldField = (*env)->GetFieldID(env, cellRunClass, "bold", "Z");
    cellRunUnderlineField = (*env)->GetFieldID(env, cellRunClass, "underline", "Z");
    cellRunStrikeField = (*env)->GetFieldID(env, cellRunClass, "strike", "Z");

    (*env)->DeleteLocalRef(env, cellRunLocal);


    jclass outputCallback = (*env)->FindClass(env, "aterm/terminal/OutputCallback");
    outputCallbackClass = (*env)->NewGlobalRef(env, outputCallback);

    outputWriteMethod = (*env)->GetMethodID(env, outputCallbackClass, "writeToPty", "([BI)V");

    (*env)->DeleteLocalRef(env, outputCallback);

    //init keys constant
    jclass keysClass = (*env)->FindClass(env, "aterm/terminal/TerminalKeys");
    initConstant(env, keysClass, "VTERM_KEY_NONE", VTERM_KEY_NONE);
    initConstant(env, keysClass, "VTERM_KEY_ENTER", VTERM_KEY_ENTER);
    initConstant(env, keysClass, "VTERM_KEY_TAB", VTERM_KEY_TAB);
    initConstant(env, keysClass, "VTERM_KEY_BACKSPACE", VTERM_KEY_BACKSPACE);
    initConstant(env, keysClass, "VTERM_KEY_ESCAPE", VTERM_KEY_ESCAPE);
    initConstant(env, keysClass, "VTERM_KEY_UP", VTERM_KEY_UP);
    initConstant(env, keysClass, "VTERM_KEY_DOWN", VTERM_KEY_DOWN);
    initConstant(env, keysClass, "VTERM_KEY_LEFT", VTERM_KEY_LEFT);
    initConstant(env, keysClass, "VTERM_KEY_RIGHT", VTERM_KEY_RIGHT);
    initConstant(env, keysClass, "VTERM_KEY_INS", VTERM_KEY_INS);
    initConstant(env, keysClass, "VTERM_KEY_DEL", VTERM_KEY_DEL);
    initConstant(env, keysClass, "VTERM_KEY_HOME", VTERM_KEY_HOME);
    initConstant(env, keysClass, "VTERM_KEY_END", VTERM_KEY_END);
    initConstant(env, keysClass, "VTERM_KEY_PAGEUP", VTERM_KEY_PAGEUP);
    initConstant(env, keysClass, "VTERM_KEY_PAGEDOWN", VTERM_KEY_PAGEDOWN);
    initConstant(env, keysClass, "VTERM_KEY_FUNCTION_0", VTERM_KEY_FUNCTION_0);
    initConstant(env, keysClass, "VTERM_KEY_FUNCTION_MAX", VTERM_KEY_FUNCTION_MAX);
    initConstant(env, keysClass, "VTERM_KEY_KP_0", VTERM_KEY_KP_0);
    initConstant(env, keysClass, "VTERM_KEY_KP_1", VTERM_KEY_KP_1);
    initConstant(env, keysClass, "VTERM_KEY_KP_2", VTERM_KEY_KP_2);
    initConstant(env, keysClass, "VTERM_KEY_KP_3", VTERM_KEY_KP_3);
    initConstant(env, keysClass, "VTERM_KEY_KP_4", VTERM_KEY_KP_4);
    initConstant(env, keysClass, "VTERM_KEY_KP_5", VTERM_KEY_KP_5);
    initConstant(env, keysClass, "VTERM_KEY_KP_6", VTERM_KEY_KP_6);
    initConstant(env, keysClass, "VTERM_KEY_KP_7", VTERM_KEY_KP_7);
    initConstant(env, keysClass, "VTERM_KEY_KP_8", VTERM_KEY_KP_8);
    initConstant(env, keysClass, "VTERM_KEY_KP_9", VTERM_KEY_KP_9);
    initConstant(env, keysClass, "VTERM_KEY_KP_MULT", VTERM_KEY_KP_MULT);
    initConstant(env, keysClass, "VTERM_KEY_KP_PLUS", VTERM_KEY_KP_PLUS);
    initConstant(env, keysClass, "VTERM_KEY_KP_COMMA", VTERM_KEY_KP_COMMA);
    initConstant(env, keysClass, "VTERM_KEY_KP_MINUS", VTERM_KEY_KP_MINUS);
    initConstant(env, keysClass, "VTERM_KEY_KP_PERIOD", VTERM_KEY_KP_PERIOD);
    initConstant(env, keysClass, "VTERM_KEY_KP_ENTER", VTERM_KEY_KP_ENTER);
    initConstant(env, keysClass, "VTERM_KEY_KP_EQUAL", VTERM_KEY_KP_EQUAL);
    initConstant(env, keysClass, "VTERM_MOD_NONE", VTERM_MOD_NONE);
    initConstant(env, keysClass, "VTERM_MOD_SHIFT", VTERM_MOD_SHIFT);
    initConstant(env, keysClass, "VTERM_MOD_ALT", VTERM_MOD_ALT);
    initConstant(env, keysClass, "VTERM_MOD_CTRL", VTERM_MOD_CTRL);
    (*env)->DeleteLocalRef(env, keysClass);

    return registerNativeMethods(env, "aterm/terminal/AbstractTerminal",
                                 gMethods, NELEM(gMethods));
}


#ifdef __cplusplus
}
#endif
