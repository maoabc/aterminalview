package aterm.terminal;


import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * Metrics shared between all {@link ScreenLine} children. Locking
 * provided by main thread.
 */
class TerminalMetrics {
    private static final int MAX_RUN_LENGTH = 256;

    final Paint bgPaint = new Paint();
    final Paint textPaint = new Paint();
    final Paint cursorPaint = new Paint();

    /**
     * Run of cells used when drawing
     */
    final ScreenCell run;

    int charTop;
    int charWidth;
    int charHeight;

    TerminalMetrics() {
        run = new ScreenCell();
        run.data = new int[MAX_RUN_LENGTH];
        run.widths = new byte[MAX_RUN_LENGTH];

        setTextSize(Typeface.MONOSPACE, 35);
    }

    void setTextSize(Typeface typeface, float textSize) {
        textPaint.setTypeface(typeface);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(textSize);

        // Read metrics to get exact pixel dimensions
        final Paint.FontMetrics fm = textPaint.getFontMetrics();
        charTop = (int) Math.ceil(fm.top);

        final float[] widths = new float[1];
        textPaint.getTextWidths("X", widths);
        charWidth = (int) Math.ceil(widths[0]);
        charHeight = (int) Math.ceil(fm.descent - fm.top);

    }
}