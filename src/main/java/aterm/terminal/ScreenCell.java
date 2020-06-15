package aterm.terminal;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.Keep;

/**
 * Represents a run of one or more {@code VTermScreenCell} which all have
 * the same formatting.
 */

@Keep
class ScreenCell {
    int[] data;   //code point
    byte[] widths;//char wcwidth 0,1,2..
    int dataSize;
    int colSize;

    //attrs
    boolean bold;
    boolean underline;
    //    boolean italic;
//    boolean blink;
//    boolean reverse;
    boolean strike;
//    int font;

    @ColorInt
    int fg = Color.CYAN;
    @ColorInt
    int bg = Color.DKGRAY;
}

