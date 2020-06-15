
package aterm.terminal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import androidx.annotation.NonNull;

import static aterm.terminal.AbstractTerminal.TAG;


/**
 * Rendered contents of a single line of a {@link AbstractTerminal} session.
 */
class ScreenLine {


    static void drawLine(@NonNull Canvas canvas,/*float dy,*/ final AbstractTerminal terminal,
                         @NonNull final TerminalMetrics metrics, final float top,
                         final int row, final int cols,
                         final boolean cursorVisible, final int cursorRow, final int cursorCol,
                         final int selCol1, final int selCol2,
                         int alpha) {
        if (terminal == null) {
            Log.w(TAG, "onDraw() without a terminal");
            canvas.drawColor(Color.MAGENTA);
            return;
        }
        final char[] chars = new char[2];

        final int charWidth = metrics.charWidth;
        final int charHeight = metrics.charHeight;
        final int charTop = metrics.charTop;

        final Paint bgPaint = metrics.bgPaint;
        final Paint textPaint = metrics.textPaint;
        final Paint cursorPaint = metrics.cursorPaint;

        final boolean selected = selCol1 != -1 && selCol2 != -1;


        for (int col = 0; col < cols; ) {
            final ScreenCell screenCell = metrics.run;
            terminal.getCellRun(row, col, screenCell);

            final int fg = screenCell.fg;
            final int bg = screenCell.bg;

            bgPaint.setColor((bg & 0xffffff) | (alpha << 24));

            textPaint.setColor(fg);
            textPaint.setFakeBoldText(screenCell.bold);
            textPaint.setUnderlineText(screenCell.underline);
            textPaint.setStrikeThruText(screenCell.strike);

            cursorPaint.setColor(fg);


            final int x = col * charWidth;

            canvas.save();
            canvas.translate(x, 0);

            final int colSize = screenCell.colSize;

            canvas.clipRect(0, top, colSize * charWidth, top + charHeight);

            canvas.drawPaint(bgPaint);


//            Log.d(TAG, "drawLine: " + col + "  " + cursorCol + "   " + colSize + "  "
//                    + dataSize+"   "+Integer.toHexString(screenCell.bg)+"   "+Integer.toHexString(screenCell.fg));

            final int[] data = screenCell.data;
            final byte[] widths = screenCell.widths;
            final int dataSize = screenCell.dataSize;

            int invertfg = -1;
            //draw cell
            int subCol = 0;
            for (int i = 0; i < dataSize; i++) {

                final int width = widths[i];
                boolean invert = false;

                final int currentCol = col + subCol;
                if (cursorVisible && cursorRow == row && cursorCol == currentCol) {
                    invert = true;
                    canvas.drawRect(subCol * charWidth, top, (subCol + width) * charWidth,
                            top + charHeight, cursorPaint);
                }
                if (selected && selCol1 <= currentCol && currentCol < selCol2) {
                    invert = true;
                    canvas.drawRect(subCol * charWidth, top, (subCol + width) * charWidth,
                            top + charHeight, cursorPaint);
                }

                final int cp = data[i];
//                    Log.d(TAG, "drawCell: " + i + "   " + width + "   " + cp + "  " + dataSize);
                if (Character.isDefined(cp)) {
                    if (invert) {
                        if (invertfg == -1) {
                            invertfg = bg;
                        }
                        textPaint.setColor(invertfg);
                    }
                    final int count = Character.toChars(cp, chars, 0);
                    canvas.drawText(chars, 0, count, charWidth * subCol, top - charTop, textPaint);

                    if (invert) {
                        textPaint.setColor(fg);
                    }
                }

                subCol += width;
            }

            canvas.restore();

            col += colSize;
        }

    }

    private static int invertedLight(int c) {
        int r = (c >> 16) & 0xff;
        int g = (c >> 8) & 0xff;
        int b = c & 0xff;
        final int l = (r + g + b) / 3;    // There is a better calculation for this that matches human eye
        final int il = 0xff - l;
        if (l == 0)
            return Color.argb(0xff, 0xff, 0xff, 0xff);
        r = r * il / l;
        g = g * il / l;
        b = b * il / l;
        return Color.argb(0xff, Math.min(r, 0xff), Math.min(g, 0xff), Math.min(b, 0xff));
    }
}
