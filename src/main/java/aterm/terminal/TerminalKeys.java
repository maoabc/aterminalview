
package aterm.terminal;

import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Keep;

import aterm.terminalview.BuildConfig;

public class TerminalKeys {
    private static final String TAG = "TerminalKeys";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    // A hack to avoid these constants being inlined by javac...
    private static int placeholder() {
        return 0;
    }

    @Keep
    public static final int VTERM_KEY_NONE = placeholder();
    @Keep
    public static final int VTERM_KEY_ENTER = placeholder();
    @Keep
    public static final int VTERM_KEY_TAB = placeholder();
    @Keep
    public static final int VTERM_KEY_BACKSPACE = placeholder();
    @Keep
    public static final int VTERM_KEY_ESCAPE = placeholder();
    @Keep
    public static final int VTERM_KEY_UP = placeholder();
    @Keep
    public static final int VTERM_KEY_DOWN = placeholder();
    @Keep
    public static final int VTERM_KEY_LEFT = placeholder();
    @Keep
    public static final int VTERM_KEY_RIGHT = placeholder();
    @Keep
    public static final int VTERM_KEY_INS = placeholder();
    @Keep
    public static final int VTERM_KEY_DEL = placeholder();
    @Keep
    public static final int VTERM_KEY_HOME = placeholder();
    @Keep
    public static final int VTERM_KEY_END = placeholder();
    @Keep
    public static final int VTERM_KEY_PAGEUP = placeholder();
    @Keep
    public static final int VTERM_KEY_PAGEDOWN = placeholder();

    @Keep
    public static final int VTERM_KEY_FUNCTION_0 = placeholder();
    @Keep
    public static final int VTERM_KEY_FUNCTION_MAX = placeholder();

    @Keep
    public static final int VTERM_KEY_KP_0 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_1 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_2 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_3 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_4 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_5 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_6 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_7 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_8 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_9 = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_MULT = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_PLUS = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_COMMA = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_MINUS = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_PERIOD = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_DIVIDE = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_ENTER = placeholder();
    @Keep
    public static final int VTERM_KEY_KP_EQUAL = placeholder();

    @Keep
    public static final int VTERM_MOD_NONE = placeholder();
    @Keep
    public static final int VTERM_MOD_SHIFT = placeholder();
    @Keep
    public static final int VTERM_MOD_ALT = placeholder();
    @Keep
    public static final int VTERM_MOD_CTRL = placeholder();

    private AbstractTerminal mTerm;

    public static int getModifiers(KeyEvent event) {
        int mod = 0;
        if (event.isCtrlPressed()) {
            mod |= VTERM_MOD_CTRL;
        }
        if (event.isAltPressed()) {
            mod |= VTERM_MOD_ALT;
        }
        if (event.isShiftPressed()) {
            mod |= VTERM_MOD_SHIFT;
        }
        return mod;
    }

    public static int getKey(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_ENTER:
                return VTERM_KEY_ENTER;
            case KeyEvent.KEYCODE_TAB:
                return VTERM_KEY_TAB;
            case KeyEvent.KEYCODE_DEL:
                return VTERM_KEY_BACKSPACE;
            case KeyEvent.KEYCODE_ESCAPE:
                return VTERM_KEY_ESCAPE;
            case KeyEvent.KEYCODE_DPAD_UP:
                return VTERM_KEY_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return VTERM_KEY_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return VTERM_KEY_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return VTERM_KEY_RIGHT;
            case KeyEvent.KEYCODE_INSERT:
                return VTERM_KEY_INS;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return VTERM_KEY_DEL;
            case KeyEvent.KEYCODE_MOVE_HOME:
                return VTERM_KEY_HOME;
            case KeyEvent.KEYCODE_MOVE_END:
                return VTERM_KEY_END;
            case KeyEvent.KEYCODE_PAGE_UP:
                return VTERM_KEY_PAGEUP;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return VTERM_KEY_PAGEDOWN;
            default:
                return 0;
        }
    }

    public static String getKeyName(int key) {
        if (key == VTERM_KEY_ENTER) {
            return "VTERM_KEY_ENTER";
        } else if (key == VTERM_KEY_TAB) {
            return "VTERM_KEY_TAB";
        } else if (key == VTERM_KEY_BACKSPACE) {
            return "VTERM_KEY_BACKSPACE";
        } else if (key == VTERM_KEY_ESCAPE) {
            return "VTERM_KEY_ESCAPE";
        } else if (key == VTERM_KEY_UP) {
            return "VTERM_KEY_UP";
        } else if (key == VTERM_KEY_DOWN) {
            return "VTERM_KEY_DOWN";
        } else if (key == VTERM_KEY_LEFT) {
            return "VTERM_KEY_LEFT";
        } else if (key == VTERM_KEY_RIGHT) {
            return "VTERM_KEY_RIGHT";
        } else if (key == VTERM_KEY_INS) {
            return "VTERM_KEY_INS";
        } else if (key == VTERM_KEY_DEL) {
            return "VTERM_KEY_DEL";
        } else if (key == VTERM_KEY_HOME) {
            return "VTERM_KEY_HOME";
        } else if (key == VTERM_KEY_END) {
            return "VTERM_KEY_END";
        } else if (key == VTERM_KEY_PAGEUP) {
            return "VTERM_KEY_PAGEUP";
        } else if (key == VTERM_KEY_PAGEDOWN) {
            return "VTERM_KEY_PAGEDOWN";
        } else if (key == VTERM_KEY_NONE) {
            return "VTERM_KEY_NONE";
        } else {
            return "UNKNOWN KEY";
        }
    }

    public int getCharacter(KeyEvent event) {
        int c = event.getUnicodeChar();
        // TODO: Actually support dead keys
        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            Log.w(TAG, "Received dead key, ignoring");
            return 0;
        }
        return c;
    }

    public boolean onKey(View v, int keyCode, KeyEvent event, int terminalModifiers) {
        if (mTerm == null || event.getAction() == KeyEvent.ACTION_UP) return false;

        int modifiers = getModifiers(event) | terminalModifiers;

        int c = getKey(event);
        if (c != 0) {
            if (DEBUG) {
                Log.d(TAG, "dispatched key event: " +
                        "mod=" + modifiers + ", " +
                        "keys=" + getKeyName(c));
            }
            boolean b = mTerm.dispatchKey(modifiers, c);
            mTerm.flushToPty();
            return b;
        }

        c = getCharacter(event);
        if (c != 0) {
            if (DEBUG) {
                Log.d(TAG, "dispatched key event: " +
                        "mod=" + modifiers + ", " +
                        "character='" + new String(Character.toChars(c)) + "'");
            }
            boolean b = mTerm.dispatchCharacter(modifiers, c);
            mTerm.flushToPty();
            return b;
        }

        return false;
    }

    public void setTerminal(AbstractTerminal term) {
        mTerm = term;
    }
}
