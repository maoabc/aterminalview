
package aterm.terminal;

import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.annotation.WorkerThread;

import aterm.terminalview.BuildConfig;

/**
 * Single abstract terminal session backed by a pseudo terminal.
 */
public abstract class AbstractTerminal implements OutputCallback {
    static final String TAG = "Terminal";
    public static final boolean DEBUG = BuildConfig.DEBUG;

    static {
        System.loadLibrary("aterm");
    }


    private volatile long mNativePtr;

    private TerminalClient mClient;

    private volatile boolean mCursorVisible;
    private volatile int mCursorRow;
    private volatile int mCursorCol;

    private volatile boolean mAltScreen;


    protected DestroyCallback mDestroyCallback;

    private final TerminalCallbacks mCallbacks = new TerminalCallbacks() {
        @Override
        public int damage(int startRow, int endRow, int startCol, int endCol) {
            if (DEBUG)
                Log.d(TAG, "damage: " + startRow + "  " + endRow + "  " + startCol + "  " + endCol);
            if (mClient != null) {
                mClient.onDamage(startRow, endRow, startCol, endCol);
            }
            return 1;
        }

        @Override
        public int moveRect(int destStartRow, int destEndRow, int destStartCol, int destEndCol,
                            int srcStartRow, int srcEndRow, int srcStartCol, int srcEndCol) {
            if (DEBUG) Log.d(TAG, "moveRect: ");
            if (mClient != null) {
                mClient.onMoveRect(destStartRow, destEndRow, destStartCol, destEndCol, srcStartRow,
                        srcEndRow, srcStartCol, srcEndCol);
            }
            return 1;
        }

        @Override
        public int moveCursor(int posRow, int posCol, int oldPosRow, int oldPosCol, int visible) {
            if (DEBUG) Log.d(TAG, "moveCursor: " + posRow + "  " + posCol);

            mCursorRow = posRow;
            mCursorCol = posCol;
            if (mClient != null) {
                mClient.onMoveCursor(posRow, posCol, oldPosRow, oldPosCol, visible);
            }
            return 1;
        }

        @Override
        public int bell() {
            if (mClient != null) {
                mClient.onBell();
            }
            return 1;
        }


        //            /* VTERM_PROP_NONE = 0 */
        //                    VTERM_PROP_CURSORVISIBLE = 1, // bool
        //                    VTERM_PROP_CURSORBLINK,       // bool
        //                    VTERM_PROP_ALTSCREEN,         // bool
        //                    VTERM_PROP_TITLE,             // string
        //                    VTERM_PROP_ICONNAME,          // string
        //                    VTERM_PROP_REVERSE,           // bool
        //                    VTERM_PROP_CURSORSHAPE,       // number
        //                    VTERM_PROP_MOUSE,             // number
        //
        //                    VTERM_N_PROPS
        @Override
        public int setTermPropBoolean(int prop, boolean value) {
            if (DEBUG) Log.d(TAG, "setTermPropBoolean: " + prop + "  " + value);
            if (prop == 1) {
                mCursorVisible = value;
            }
//            if(prop==2){
//                return
//            }
            if (prop == 3) {
                mAltScreen = value;
            }

            return 1;
        }
    };


    public AbstractTerminal(int rows, int cols, int scrollRows, @ColorInt int fg, @ColorInt int bg) {
        mNativePtr = nativeInit(mCallbacks, this, rows, cols, scrollRows, fg, bg);
    }

    public abstract void start();

    @NonNull
    public abstract String getTitle();

    public abstract void setTitle(@NonNull String title);

    @NonNull
    public abstract String getKey();

    protected abstract void setPtyWindowSize(int cols, int rows);

    protected abstract void closePty();


    protected abstract int scrollRowSize();

    public abstract void flushToPty();

    public abstract void release();

    public void setDestroyCallback(DestroyCallback destroyCallback) {
        this.mDestroyCallback = destroyCallback;
    }

    private void destroy() {
        synchronized (this) {
            if (mNativePtr != 0) {
                closePty();
                if (nativeDestroy(mNativePtr) != 0) {
                    mNativePtr = 0;
                    throw new IllegalStateException("destroy failed");
                }
                mNativePtr = 0;
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            destroy();
        } finally {
            super.finalize();
        }
    }

    final void setClient(TerminalClient client) {
        mClient = client;
    }

    public final void resize(int cols, int rows) {
        synchronized (this) {
            setPtyWindowSize(cols, rows);
            if (nativeResize(mNativePtr, rows, cols, scrollRowSize()) != 0) {
                throw new IllegalStateException("resize failed");
            }
        }
    }


    public final int getRows() {
        return nativeGetRows(mNativePtr);
    }

    public final int getCols() {
        return nativeGetCols(mNativePtr);
    }

    public final int getScrollRows() {
        return nativeGetScrollRows(mNativePtr);
    }

    public final int getScrollCurRows() {
        return nativeGetScrollCur(mNativePtr);
    }

    public boolean isAltScreen() {
        return mAltScreen;
    }

    public final void getCellRun(int row, int col, @NonNull ScreenCell run) {
        if (nativeGetCellRun(mNativePtr, row, col, run) != 0) {
            throw new IllegalStateException("getCell failed");
        }
    }

    public final boolean getCursorVisible() {
        return mCursorVisible;
    }

    public final int getCursorRow() {
        return mCursorRow;
    }

    public final int getCursorCol() {
        return mCursorCol;
    }


    @NonNull
    public final String getText(int startRow, int endRow, int startCol, int endCol) {
        if (startRow < -getScrollCurRows()) {
            startRow = -getScrollCurRows();
        }
        if (endRow >= getRows()) {
            endRow = getRows() - 1;

        }
        final int cols = getCols();
        if (endCol > cols) {
            endCol = cols;
        }
        synchronized (this) {
            final char[] chars = new char[2];
            final StringBuilder sb = new StringBuilder();
            for (int row = startRow; row <= endRow; row++) {
                int col1 = 0;
                if (row == startRow) {
                    col1 = startCol;
                }
                int col2 = (row == endRow) ? endCol : cols;

                int size = col2 - col1;
                if (size <= 0) {
                    continue;
                }
                final int[] codePoints = new int[size];
                final int count = nativeGetLineText(mNativePtr, row, col1, col2, codePoints);
                for (int i = 0; i < count; i++) {
                    final int len = Character.toChars(codePoints[i], chars, 0);
                    sb.append(chars, 0, len);
                }
                if (col2 == cols) {
                    sb.append('\n');
                }
            }
            return sb.toString();
        }
    }


    public void mouseMove(int row, int col, int mod) {
        nativeMouseMove(mNativePtr, row, col, mod);
    }

    public void mouseButton(int button, boolean pressed, int mod) {
        nativeMouseButton(mNativePtr, button, pressed, mod);
    }

    final int getValidCol(int row, int col) {
        synchronized (this) {
            return nativeGetValidCol(mNativePtr, row, col);
        }
    }

    @Size(2)
    public final int[] getDefaultColors() {
        synchronized (this) {
            int[] colors = new int[2];
            nativeGetDefaultColors(mNativePtr, colors);
            return colors;
        }
    }

    public final void setDefaultColors(@ColorInt int fg, @ColorInt int bg) {
        synchronized (this) {
            int[] colors = {fg, bg};
            nativeSetDefaultColors(mNativePtr, colors);
        }
    }


    protected final boolean dispatchKey(int modifiers, int key) {
        synchronized (this) {
            return nativeDispatchKey(mNativePtr, modifiers, key);
        }
    }

    protected final boolean dispatchCharacter(int modifiers, int character) {
        synchronized (this) {
            return nativeDispatchCharacter(mNativePtr, modifiers, character);
        }
    }

    protected int inputWrite(byte[] data, int off, int len) {
        synchronized (this) {
            return nativeInputWrite(mNativePtr, data, off, len);
        }
    }

    public int wordOffset(int row, int col, int dir) {
        return nativeWordOffset(mNativePtr, row, col, dir);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractTerminal that = (AbstractTerminal) o;

        return getKey().equals(that.getKey());
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Keep
    private static native long nativeInit(TerminalCallbacks callbacks, OutputCallback outputCallback,
                                          int row, int cols, int scrollRows, @ColorInt int fg, @ColorInt int bg);

    @Keep
    private static native int nativeDestroy(long ptr);

    @Keep
    private static native int nativeResize(long ptr, int rows, int cols, int scrollRows);

    @Keep
    private static native int nativeGetCellRun(long ptr, int row, int col, ScreenCell run);

    @Keep
    private static native int nativeGetRows(long ptr);

    @Keep
    private static native int nativeGetCols(long ptr);

    @Keep
    private static native int nativeGetScrollRows(long ptr);

    @Keep
    private static native int nativeGetScrollCur(long ptr);

    @Keep
    private static native boolean nativeDispatchKey(long ptr, int modifiers, int key);

    @Keep
    private static native boolean nativeDispatchCharacter(long ptr, int modifiers, int character);

    @Keep
    private static native int nativeGetLineText(long ptr, int row, int startCol, int endCol, int[] out);

    @Keep
    private static native void nativeMouseMove(long ptr, int row, int col, int mod);

    @Keep
    private static native void nativeMouseButton(long ptr, int button, boolean pressed, int mod);

    @Keep
    private static native int nativeGetValidCol(long ptr, int row, int col);

    @Keep
    private static native void nativeSetDefaultColors(long ptr, @Size(2) int[] colors);

    @Keep
    private static native void nativeGetDefaultColors(long ptr, @Size(2) int[] colors);

    @Keep
    private static native int nativeInputWrite(long ptr, byte[] data, int off, int len);

    @Keep
    private static native int nativeWordOffset(long ptr, int row, int col, int dir);

    public interface DestroyCallback {
        @WorkerThread
        void onDestroy(AbstractTerminal terminal, int exitCode);
    }
}

