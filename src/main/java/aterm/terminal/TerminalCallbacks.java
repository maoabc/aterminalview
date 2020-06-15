
package aterm.terminal;

import androidx.annotation.Keep;

@Keep
public abstract class TerminalCallbacks {
    public int damage(int startRow, int endRow, int startCol, int endCol) {
        return 1;
    }

    public int moveRect(int destStartRow, int destEndRow, int destStartCol, int destEndCol,
            int srcStartRow, int srcEndRow, int srcStartCol, int srcEndCol) {
        return 1;
    }

    public int moveCursor(int posRow, int posCol, int oldPosRow, int oldPosCol, int visible) {
        return 1;
    }

    public int setTermPropBoolean(int prop, boolean value) {
        return 1;
    }

    public int setTermPropInt(int prop, int value) {
        return 1;
    }

    public int setTermPropString(int prop, String value) {
        return 1;
    }

    public int setTermPropColor(int prop, int red, int green, int blue) {
        return 1;
    }

    public int bell() {
        return 1;
    }
}
