
package aterm.terminal;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import aterm.terminalview.BuildConfig;
import aterm.terminalview.R;

import static aterm.terminal.AbstractTerminal.TAG;


/**
 * Rendered contents of a {@link AbstractTerminal} session.
 */
public class TerminalView extends View {
    private static final boolean DEBUG_IME = false;
    private static final boolean DEBUG = BuildConfig.DEBUG;


    private static final int SELECT_TEXT_OFFSET_Y = -40;

    private static final int ID_COPY = android.R.id.copy;
    private static final int ID_PASTE = android.R.id.paste;

    private static final int MENU_ITEM_ORDER_PASTE = 0;
    private static final int MENU_ITEM_ORDER_COPY = 1;

    private AbstractTerminal mTerm;

    int mScrollY;

    final Editable editable = new SpannableStringBuilder("");

    private final TerminalMetrics mMetrics = new TerminalMetrics();
    private final TerminalKeys mTermKeys = new TerminalKeys();

    private final TerminalRect mSelRect = new TerminalRect();

    private int mBackgroundAlpha = 0xff;


    private final int mTopOfScreenMargin;
    private final int mBottomOfScreenMargin;
    private final int mLeftOfScreenMargin;
    private final int mRightOfScreenMargin;

    @ColorInt
    private int defaultBg;


    Drawable mSelectHandleLeft;
    Drawable mSelectHandleRight;
    final int[] mTempCoords = new int[2];
    Rect mTempRect;
    private SelectionModifierCursorController mSelectionModifierCursorController;
    private boolean mIsInTextSelectionMode = false;
    ActionMode mTextActionMode;
    private ActionMode.Callback mCustomSelectionActionModeCallback;


    private int mModifiers;
    private ModifiersChangedListener mModifiersChangedListener;

    private FastScroller mFastScroller;


    private static final int MSG_DAMAGE = 1;
    private static final int MSG_MOVERECT = 2;
    private static final int MSG_MOVECURSOR = 3;
    private static final int MSG_BELL = 4;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DAMAGE:
                case MSG_MOVERECT:
                case MSG_MOVECURSOR: {
                    invalidate();
                    if (mUpdateCallback != null) mUpdateCallback.onUpdate();
                    break;
                }
                case MSG_BELL: {
                    invalidate();
                    if (mUpdateCallback != null) mUpdateCallback.onBell();
                    break;
                }
            }
        }
    };

    private final TerminalClient mClient = new TerminalClient() {
        @Override
        public void onDamage(final int startRow, final int endRow, int startCol, int endCol) {
            mHandler.sendEmptyMessage(MSG_DAMAGE);
        }

        @Override
        public void onMoveRect(int destStartRow, int destEndRow, int destStartCol, int destEndCol,
                               int srcStartRow, int srcEndRow, int srcStartCol, int srcEndCol) {
            mHandler.sendEmptyMessage(MSG_MOVERECT);
        }

        @Override
        public void onMoveCursor(int posRow, int posCol, int oldPosRow, int oldPosCol, int visible) {
            mHandler.sendEmptyMessage(MSG_MOVECURSOR);
        }

        @Override
        public void onBell() {
            mHandler.sendEmptyMessage(MSG_BELL);
        }
    };

    private UpdateCallback mUpdateCallback;

    private final GestureDetector mGestureDetector;
    private int mMaxScreenRows;
    private int mMaxScreenCols;

    public TerminalView(Context context) {
        this(context, null);
    }

    public TerminalView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TerminalView(final Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setBackground(null);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.TerminalView, defStyle, 0);
        mTopOfScreenMargin = a.getDimensionPixelSize(R.styleable.TerminalView_screenMarginTop, 0);
        mBottomOfScreenMargin = a.getDimensionPixelSize(R.styleable.TerminalView_screenMarginBottom, 0);
        mLeftOfScreenMargin = a.getDimensionPixelSize(R.styleable.TerminalView_screenMarginLeft, 0);
        mRightOfScreenMargin = a.getDimensionPixelSize(R.styleable.TerminalView_screenMarginRight, 0);
        a.recycle();


        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }


            @Override
            public void onLongPress(MotionEvent e) {
                if (mTerm == null) {
                    return;
                }
                mSelRect.startRow = mSelRect.endRow = getCursorY(e.getY());
                mSelRect.startCol = mTerm.wordOffset(mSelRect.startRow, getCursorX(e.getX()), -1);
                mSelRect.endCol = mTerm.wordOffset(mSelRect.startRow, getCursorX(e.getX()) + 1, 1);

                startTextSelectionMode();

            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (mTerm == null) return true;
                if (mIsInTextSelectionMode) {
                    stopTextSelectionMode();
                    return true;
                }
                if (getResources().getConfiguration().hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(TerminalView.this, InputMethodManager.SHOW_IMPLICIT);
                        return true;
                    }
                }
                return false;
            }

        });
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        calcSize(getWidth(), getHeight());
    }

    public boolean isAltScreen() {
        return mTerm != null && mTerm.isAltScreen();
    }

    float altScreenScroll(float deltaY) {
        stopTextSelectionMode();
        final boolean down = deltaY > 0;
        final int count = (int) Math.abs(deltaY / mMetrics.charHeight);
        for (int i = 0; i < count; i++) {
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, down ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
            mTermKeys.onKey(this, event.getKeyCode(), event, 0);
        }
        return down ? count * mMetrics.charHeight : -count * mMetrics.charHeight;
    }

    public void myScrollTo(int scrollY) {
        if (DEBUG) Log.d(TAG, "myScrollTo: " + scrollY);
        if (mScrollY != scrollY) {
            mScrollY = scrollY;
            if (mFastScroller != null && mTerm != null) {
                mFastScroller.onScroll(this, mScrollY, getTotalHeight());
            }
            postInvalidate();
        }
    }

    int getScrollCurRows() {
        if (mTerm != null) {
            return mTerm.getScrollCurRows();
        }
        return 0;
    }

    int getTotalHeight() {
        if (mTerm == null) {
            return getHeight();
        }
        return (mTerm.getScrollCurRows()) * mMetrics.charHeight;
    }

    public void setUpdateCallback(UpdateCallback updateCallback) {
        this.mUpdateCallback = updateCallback;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (mFastScroller != null) {
            boolean intercepted = mFastScroller.onInterceptTouchEvent(event);
            if (intercepted) {
                Touch.cancelFling(this, editable);
                return true;
            }
            intercepted = mFastScroller.onTouchEvent(event);
            if (intercepted) {
                Touch.cancelFling(this, editable);
                return true;
            }
        }

        final boolean superResult = super.onTouchEvent(event);

        boolean handle = doTouch(event);
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        if (handle) {
            return true;
        }


        return superResult;
    }

    private boolean doTouch(MotionEvent event) {

        boolean handle = Touch.onTouchEvent(this, event);

        if (isFocused()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP: {
                    return true;
                }
            }
        }
        return handle;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        long start = SystemClock.currentThreadTimeMillis();
        final AbstractTerminal term = this.mTerm;
        if (term == null) {
            return;
        }
        final int cols = term.getCols();
        final int rows = term.getRows();
        final TerminalMetrics metrics = this.mMetrics;
        final int charHeight = metrics.charHeight;
        final int backgroundAlpha = this.mBackgroundAlpha;

        final boolean cursorVisible = term.getCursorVisible();
        final int cursorRow = term.getCursorRow();
        final int cursorCol = term.getCursorCol();

        final int startRow = mScrollY / charHeight /*-1*/;
        final int endRow = startRow + rows /*+ 1*/;
//        final int dy = 0;

        final TerminalRect selRect = this.mSelRect;
        canvas.save();
        canvas.translate(mLeftOfScreenMargin, mTopOfScreenMargin);


        float top = 0;
        for (int row = startRow; row < endRow; row++) {

            int selCol1 = -1;
            int selCol2 = -1;
            if (row >= selRect.startRow && row <= selRect.endRow) {
                if (row == selRect.startRow) {
                    selCol1 = selRect.startCol;
                } else {
                    selCol1 = 0;
                }
                if (row == selRect.endRow) {
                    selCol2 = selRect.endCol;
                } else {
                    selCol2 = cols;
                }
            }
            ScreenLine.drawLine(canvas, term, metrics, top, row, cols,
                    cursorVisible, cursorRow, cursorCol,
                    selCol1, selCol2, backgroundAlpha);
            top += charHeight;
        }

        metrics.bgPaint.setColor((defaultBg & 0xffffff) | (backgroundAlpha << 24));
        canvas.clipRect(0, 0, cols * metrics.charWidth, top, Region.Op.DIFFERENCE);
        canvas.drawPaint(metrics.bgPaint);

        canvas.restore();


        if (mSelectionModifierCursorController != null &&
                mSelectionModifierCursorController.isActive()) {
            mSelectionModifierCursorController.updatePosition();
        }

        long end = SystemClock.currentThreadTimeMillis();
        if (DEBUG) Log.d(TAG, "onDraw: " + startRow + "  " + charHeight + "  " + (end - start));

        if (mFastScroller != null) mFastScroller.draw(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        calcSize(w, h);

        if (mTerm != null) {
            mTerm.resize(mMaxScreenCols, mMaxScreenRows);
        }

        if (mFastScroller != null) {
            mFastScroller.onSizeChanged(w, h, oldw, oldh);
        }
        if (DEBUG)
            Log.d(TAG, "onSizeChanged: " + mMaxScreenRows + "   " + mMaxScreenCols);
        resetStatus();

    }

    private void calcSize(int w, int h) {
        mMaxScreenRows = (h - (mTopOfScreenMargin + mBottomOfScreenMargin)) / mMetrics.charHeight;
        mMaxScreenCols = (w - (mLeftOfScreenMargin + mRightOfScreenMargin)) / mMetrics.charWidth;
    }


    public void setTerminal(AbstractTerminal term) {
        final AbstractTerminal orig = mTerm;
        if (orig == term) {
            return;
        }
        if (orig != null) {
            orig.setClient(null);
            orig.setDestroyCallback(null);
        }
        mTerm = term;

        if (term != null) {
            if (mMaxScreenRows != 0 && mMaxScreenCols != 0) {
                term.resize(mMaxScreenCols, mMaxScreenRows);
            }
            term.setClient(mClient);
            mTermKeys.setTerminal(term);

            int[] colors = term.getDefaultColors();
            int fg = colors[0];
            defaultBg = colors[1];
//            Log.d(TAG, "setTerminal: " + Integer.toHexString(colors[0]) + "   " + Integer.toHexString(defaultBg));

            setFastScrollEnabled(true, fg);

            mSelRect.reset();
            mScrollY = 0;

            // Populate any current settings
            invalidate();
        }
    }

    public void detachCurrentTerminal() {
        if (mTerm != null) {
            mTerm.setClient(null);
            mTerm.setDestroyCallback(null);
        }
    }

    public void setDefaultColor(@ColorInt int fg, @ColorInt int bg) {
        this.defaultBg = bg;
        if (mTerm != null) mTerm.setDefaultColors(fg, bg);

        setFastScrollEnabled(true, fg);
    }


    public int getBackgroundAlpha() {
        return mBackgroundAlpha;
    }

    public void setBackgroundAlpha(int backgroundAlpha) {
        this.mBackgroundAlpha = backgroundAlpha;
    }

    public AbstractTerminal getTerminal() {
        return mTerm;
    }

    public void setTextSize(Typeface typeface, float textSize) {
        mMetrics.setTextSize(typeface, textSize);

        // Layout will kick off terminal resize when needed
        requestLayout();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.imeOptions |=
                EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                        EditorInfo.IME_FLAG_NO_FULLSCREEN |
                        EditorInfo.IME_FLAG_NO_ENTER_ACTION |
                        EditorInfo.IME_ACTION_NONE;
        outAttrs.inputType = EditorInfo.TYPE_NULL;
        return new BaseInputConnection(this, true) {

//            @Override
//            public Editable getEditable() {
//                return editable;//返回自定义编辑对象，后续绘制输入法联想状态等
//            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (DEBUG_IME) Log.d(TAG, "commitText: " + text);
                super.commitText(text, newCursorPosition);
                sendText(getEditable());
                getEditable().clear();
                return true;
            }


            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (DEBUG_IME) {
                    Log.d(TAG, "deleteSurroundingText: " + leftLength + "  " + rightLength + "   " + getComposingSpanStart(getEditable()) + "  " + getComposingSpanEnd(getEditable()));
                }

                KeyEvent k = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
                for (int i = 0; i < leftLength; i++) {
                    this.sendKeyEvent(k);
                }
                KeyEvent k1 = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL);
                for (int i = 0; i < rightLength; i++) {
                    this.sendKeyEvent(k1);
                }
                return super.deleteSurroundingText(leftLength, rightLength);
            }


            @Override
            public boolean finishComposingText() {
                super.finishComposingText();
                if (DEBUG_IME) {
                    Log.d(TAG, "finishComposingText: " + getEditable() + "  " + getComposingSpanStart(getEditable()) + "  " + getComposingSpanEnd(getEditable()));
                }
                sendText(getEditable());
                getEditable().clear();


                return true;
            }
        };
    }

    private boolean sendText(CharSequence text) {
        stopTextSelectionMode();
        if (TextUtils.isEmpty(text) || mTerm == null) {
            return false;
        }
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c1 = text.charAt(i);

            int codePoint = c1;
            if (Character.isHighSurrogate(c1) && i + 1 < length) {
                char c2 = text.charAt(i + 1);
                if (Character.isLowSurrogate(c2)) {
                    codePoint = Character.toCodePoint(c1, c2);
                    i++;
                }
            }
            mTerm.dispatchCharacter(getKeyModifiers(), codePoint);
        }
        setModifiers(0);
        mTerm.flushToPty();

        resetStatus();
        return true;
    }

    private int getKeyModifiers() {
        return mModifiers;
    }


    public int getModifiers() {
        return mModifiers;
    }

    public void setModifiers(int modifiers) {
        if (this.mModifiers != modifiers) {
            this.mModifiers = modifiers;
            if (mModifiersChangedListener != null) mModifiersChangedListener.onChanged(mModifiers);
        }
    }

    public ModifiersChangedListener getModifiersChangedListener() {
        return mModifiersChangedListener;
    }

    public void setModifiersChangedListener(ModifiersChangedListener modifiersChangedListener) {
        this.mModifiersChangedListener = modifiersChangedListener;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (DEBUG) Log.d(TAG, "onKeyDown: " + keyCode);
        resetStatus();
        return mTermKeys.onKey(this, keyCode, event, getKeyModifiers());
    }

    private void resetStatus() {
        Touch.cancelFling(TerminalView.this, editable);
        myScrollTo(0);
        stopTextSelectionMode();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean result = mTermKeys.onKey(this, keyCode, event, getKeyModifiers());
        setModifiers(0);
        if (mTerm != null) mTerm.flushToPty();
        return result;
    }

    public void setFastScrollEnabled(boolean enabled, @ColorInt int fore) {
        if (mFastScroller != null) {
            mFastScroller.stop();
            mFastScroller = null;
        }
        if (enabled) {
            mFastScroller = new FastScroller(this, fore);
        }
    }

    private class HandleView extends View {
        private Drawable mDrawable;
        private PopupWindow mContainer;
        private int mPointX;
        private int mPointY;
        private SelectionModifierCursorController mController;
        private boolean mIsDragging;
        private float mTouchToWindowOffsetX;
        private float mTouchToWindowOffsetY;
        private int mHotspotX;
        private int mHotspotY;
        private float mTouchOffsetY;
        private int mLastParentX;
        private int mLastParentY;


        private int mHandleWidth;
        private int mHandleHeight;

//        private long mLastTime;


        public static final int LEFT = 0;
        public static final int RIGHT = 2;

        public HandleView(SelectionModifierCursorController controller, int orientation) {
            super(TerminalView.this.getContext());
            mController = controller;
            mContainer = new PopupWindow(TerminalView.this.getContext(), null,
                    android.R.attr.textSelectHandleWindowStyle);
            mContainer.setSplitTouchEnabled(true);
            mContainer.setClippingEnabled(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mContainer.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
            }
            mContainer.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            mContainer.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

            setOrientation(orientation);
        }

        public void setOrientation(int orientation) {
            int handleWidth = 0;
            switch (orientation) {
                case LEFT: {
                    if (mSelectHandleLeft == null) {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mSelectHandleLeft = getContext().getDrawable(
                                    R.drawable.text_select_handle_left_material);
                        } else {
                            mSelectHandleLeft = getContext().getResources().getDrawable(
                                    R.drawable.text_select_handle_left_material);

                        }
                    }
                    //
                    mDrawable = mSelectHandleLeft;
                    handleWidth = mDrawable.getIntrinsicWidth();
                    mHotspotX = (handleWidth * 3) / 4;
                    break;
                }

                case RIGHT: {
                    if (mSelectHandleRight == null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mSelectHandleRight = getContext().getDrawable(
                                    R.drawable.text_select_handle_right_material);
                        } else {
                            mSelectHandleRight = getContext().getResources().getDrawable(
                                    R.drawable.text_select_handle_right_material);
                        }
                    }
                    mDrawable = mSelectHandleRight;
                    handleWidth = mDrawable.getIntrinsicWidth();
                    mHotspotX = handleWidth / 4;
                    break;
                }

            }

            mHandleHeight = mDrawable.getIntrinsicHeight();

            mHandleWidth = handleWidth;
            mTouchOffsetY = -mHandleHeight * 0.3f;
            mHotspotY = 0;
            invalidate();
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(mDrawable.getIntrinsicWidth(),
                    mDrawable.getIntrinsicHeight());
        }

        public void show() {
            if (!isPositionVisible()) {
                hide();
                return;
            }
//            checkChangedOrientation();
            mContainer.setContentView(this);
            final int[] coords = mTempCoords;
            TerminalView.this.getLocationInWindow(coords);
            coords[0] += mPointX;
            coords[1] += mPointY;
            mContainer.showAtLocation(TerminalView.this, 0, coords[0], coords[1]);

        }

        public void hide() {
            mIsDragging = false;
            mContainer.dismiss();
        }

        public boolean isShowing() {
            return mContainer.isShowing();
        }


        private boolean isPositionVisible() {
            // Always show a dragging handle.
            if (mIsDragging) {
                return true;
            }

            final TerminalView hostView = TerminalView.this;
            final int left = 0;
            final int right = hostView.getRight();
            final int top = 0;
            final int bottom = hostView.getBottom();

            if (mTempRect == null) {
                mTempRect = new Rect();
            }
            final Rect clip = mTempRect;
            clip.left = left + hostView.getPaddingLeft();
            clip.top = top + hostView.getPaddingTop();
            clip.right = right - hostView.getPaddingRight();
            clip.bottom = bottom - hostView.getPaddingBottom();

            final ViewParent parent = hostView.getParent();
            if (parent == null || !parent.getChildVisibleRect(hostView, clip, null)) {
                return false;
            }

            final int[] coords = mTempCoords;
            hostView.getLocationInWindow(coords);
            final int posX = coords[0] + mPointX + (int) mHotspotX;
            final int posY = coords[1] + mPointY + (int) mHotspotY;

            return posX >= clip.left && posX <= clip.right &&
                    posY >= clip.top && posY <= clip.bottom;
        }

        private void moveTo(int x, int y) {
            mPointX = x;
            mPointY = y;
            if (DEBUG) Log.d(TAG, "moveTo: " + x + "   " + y);
            if (isPositionVisible()) {
                int[] coords = null;
                if (mContainer.isShowing()) {
//                    if (mIsDragging) {
//                        checkChangedOrientation();
//                    }
                    coords = mTempCoords;
                    TerminalView.this.getLocationInWindow(coords);
                    int x1 = coords[0] + mPointX;
                    int y1 = coords[1] + mPointY;
                    mContainer.update(x1, y1,
                            getWidth(), getHeight());
                } else {
                    show();
                }

                if (mIsDragging) {
                    if (coords == null) {
                        coords = mTempCoords;
                        TerminalView.this.getLocationInWindow(coords);
                    }
                    if (coords[0] != mLastParentX || coords[1] != mLastParentY) {
                        mTouchToWindowOffsetX += coords[0] - mLastParentX;
                        mTouchToWindowOffsetY += coords[1] - mLastParentY;
                        mLastParentX = coords[0];
                        mLastParentY = coords[1];
                    }
                }
            } else {
                if (isShowing()) {
                    hide();
                }
            }
        }

        @Override
        public void onDraw(Canvas c) {
            mDrawable.setBounds(0, 0, mHandleWidth, mHandleHeight);
            mDrawable.draw(c);

        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            updateFloatingToolbarVisibility(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    final float rawX = ev.getRawX();
                    final float rawY = ev.getRawY();
                    mTouchToWindowOffsetX = rawX - mPointX;
                    mTouchToWindowOffsetY = rawY - mPointY;
                    final int[] coords = mTempCoords;
                    TerminalView.this.getLocationInWindow(coords);
                    mLastParentX = coords[0];
                    mLastParentY = coords[1];
                    mIsDragging = true;
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    final float rawX = ev.getRawX();
                    final float rawY = ev.getRawY();

                    final float newPosX = rawX - mTouchToWindowOffsetX + mHotspotX;
                    final float newPosY = rawY - mTouchToWindowOffsetY + mHotspotY + mTouchOffsetY;

                    mController.updatePosition(this, Math.round(newPosX), Math.round(newPosY));


                    break;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mIsDragging = false;
            }
            return true;
        }


        public boolean isDragging() {
            return mIsDragging;
        }

        void positionAtCursor(final int cx, final int cy) {
            if (DEBUG) Log.d(TAG, "positionAtCursor: " + cx + "   " + cy);
            int left = getPointX(cx) - mHotspotX;
            int bottom = getPointY(cy + 1);
            moveTo(left, bottom);
        }
    }

    private class SelectionModifierCursorController implements ViewTreeObserver.OnTouchModeChangeListener {
        // The cursor controller images
        private HandleView mStartHandle, mEndHandle;
        // Whether selection anchors are active
        private boolean mIsShowing;
        private final int mHandleHeight;

        SelectionModifierCursorController() {
            mStartHandle = new HandleView(this, HandleView.LEFT);
            mEndHandle = new HandleView(this, HandleView.RIGHT);
            mHandleHeight = Math.max(mStartHandle.mHandleHeight, mEndHandle.mHandleHeight);
        }

        public void show() {
            mIsShowing = true;
            updatePosition();
            mStartHandle.show();
            mEndHandle.show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                final TextActionModeCallback callback = new TextActionModeCallback();
                mTextActionMode = startActionMode(new ActionMode.Callback2() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        return callback.onCreateActionMode(mode, menu);
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return callback.onPrepareActionMode(mode, menu);
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        return callback.onActionItemClicked(mode, item);
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        callback.onDestroyActionMode(mode);
                    }

                    @Override
                    public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                        int x1 = getPointX(mSelRect.startCol);
                        int x2 = getPointX(mSelRect.endCol);
                        int y1 = getPointY(mSelRect.startRow);
                        int y2 = getPointY(mSelRect.endRow + 1);
                        if (x1 > x2) {
                            int tmp = x1;
                            x1 = x2;
                            x2 = tmp;
                        }
                        outRect.set(x1, y1, x2, y2 + mHandleHeight);
                    }
                }, ActionMode.TYPE_FLOATING);
            } else {
                mTextActionMode = startActionMode(new TextActionModeCallback());
            }
        }

        public void hide() {
            mStartHandle.hide();
            mEndHandle.hide();
            mIsShowing = false;
            stopTextActionMode();

        }

        public boolean isActive() {
            return mIsShowing;
        }

        public void updatePosition(HandleView handle, int x, int y) {
            if (DEBUG) Log.d(TAG, "updatePosition: " + x + "   " + y);
            final AbstractTerminal terminal = TerminalView.this.mTerm;
            if (terminal == null) {
                return;
            }
            final int charHeight = mMetrics.charHeight;
            int scrollY = mScrollY;
            final int rows = terminal.getRows();
            final int scrollCurRows = terminal.getScrollCurRows();
            final boolean altScreen = terminal.isAltScreen();

            int scrollRow = (scrollY - mTopOfScreenMargin) / charHeight + mMaxScreenRows;

            if (handle == mStartHandle) {
                mSelRect.startCol = getCursorX(x);
                mSelRect.startRow = getCursorY(y);
                if (DEBUG) Log.d(TAG, "updatePosition: startRow " + mSelRect.startRow);
                if (mSelRect.startCol < 0) {
                    mSelRect.startCol = 0;
                }

                if (mSelRect.startRow < -scrollCurRows) {
                    mSelRect.startRow = -scrollCurRows;

                } else if (mSelRect.startRow > rows - 1) {
                    mSelRect.startRow = rows - 1;

                }

                if (mSelRect.startRow > scrollRow) {
                    mSelRect.startRow = scrollRow;
                }
                if (altScreen) {
                    if (mSelRect.startRow < 0) {
                        mSelRect.startRow = 0;
                    }
                }


                if (mSelRect.startRow > mSelRect.endRow) {
                    mSelRect.startRow = mSelRect.endRow;
                }
                if (mSelRect.startRow == mSelRect.endRow && mSelRect.startCol > mSelRect.endCol) {
                    mSelRect.startCol = mSelRect.endCol;
                }

                if (!altScreen) {
                    if (mSelRect.startRow * charHeight <= scrollY) {
                        scrollY = mSelRect.startRow * charHeight;
                    } else if ((mSelRect.startRow - rows) * charHeight >= scrollY) {
                        scrollY += charHeight;
                    }
                }


                mSelRect.startCol = terminal.getValidCol(mSelRect.startRow, mSelRect.startCol);

                if (DEBUG)
                    Log.d(TAG, "updatePosition: left " + mSelRect.startCol + "   " + mSelRect.startRow);
            } else {
                mSelRect.endCol = getCursorX(x);
                mSelRect.endRow = getCursorY(y);
                if (mSelRect.endCol < 0) {
                    mSelRect.endCol = 0;
                }


                if (mSelRect.endRow < -scrollCurRows) {
                    mSelRect.endRow = -scrollCurRows;
                } else if (mSelRect.endRow > rows - 1) {
                    mSelRect.endRow = rows - 1;
                }

                if (mSelRect.endRow > scrollRow) {
                    mSelRect.endRow = scrollRow;

                }

                if (mSelRect.startRow > mSelRect.endRow) {
                    mSelRect.endRow = mSelRect.startRow;
                }
                if (mSelRect.startRow == mSelRect.endRow && mSelRect.startCol > mSelRect.endCol) {
                    mSelRect.endCol = mSelRect.startCol;
                }

                if (!altScreen) {
                    if (mSelRect.endRow * charHeight <= scrollY) {//终端滑动
                        scrollY = mSelRect.endRow * charHeight;
                    } else if ((mSelRect.endRow - rows) * charHeight >= scrollY) {
                        scrollY += charHeight;
                    }
                }

                mSelRect.endCol = terminal.getValidCol(mSelRect.endRow, mSelRect.endCol);
            }
            if (DEBUG)
                Log.d(TAG, "updatePosition: selx1= " + mSelRect.startCol + "  selx2 = " + mSelRect.endCol);

            if (scrollY != mScrollY) {
                myScrollTo(scrollY);
            } else {
                invalidate();
            }

        }


        public void updatePosition() {
            if (!isActive()) {
                return;
            }

            mStartHandle.positionAtCursor(mSelRect.startCol, mSelRect.startRow);

            mEndHandle.positionAtCursor(mSelRect.endCol, mSelRect.endRow);

            if (mTextActionMode != null) {
                mTextActionMode.invalidate();
            }

        }

        public boolean onTouchEvent(MotionEvent event) {

            return false;
        }


        /**
         * @return true iff this controller is currently used to move the selection start.
         */
        public boolean isSelectionStartDragged() {
            return mStartHandle.isDragging();
        }

        public boolean isSelectionEndDragged() {
            return mEndHandle.isDragging();
        }

        public void onTouchModeChanged(boolean isInTouchMode) {
            if (!isInTouchMode) {
                hide();
            }
        }

        public void onDetached() {
        }
    }


    SelectionModifierCursorController getSelectionController() {


        if (mSelectionModifierCursorController == null) {
            mSelectionModifierCursorController = new SelectionModifierCursorController();

            final ViewTreeObserver observer = getViewTreeObserver();
            if (observer != null) {
                observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
            }
        }

        return mSelectionModifierCursorController;
    }

    private void hideSelectionModifierCursorController() {
        if (mSelectionModifierCursorController != null && mSelectionModifierCursorController.isActive()) {
            mSelectionModifierCursorController.hide();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mSelectionModifierCursorController != null) {
            getViewTreeObserver().addOnTouchModeChangeListener(mSelectionModifierCursorController);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mSelectionModifierCursorController != null) {
            getViewTreeObserver().removeOnTouchModeChangeListener(mSelectionModifierCursorController);
            mSelectionModifierCursorController.onDetached();
        }
    }

    private void startTextSelectionMode() {
        if (!requestFocus()) {
            return;
        }

        getSelectionController().show();

        mIsInTextSelectionMode = true;
        invalidate();
    }

    private void stopTextSelectionMode() {
        if (mIsInTextSelectionMode) {
            hideSelectionModifierCursorController();
            mSelRect.reset();
            mIsInTextSelectionMode = false;
            invalidate();
        }
    }

    protected void stopTextActionMode() {
        if (mTextActionMode != null) {
            // This will hide the mSelectionModifierCursorController
            mTextActionMode.finish();
        }
    }

    private boolean canPaste() {
        return ((ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE))
                .hasPrimaryClip();
    }

    private class TextActionModeCallback implements ActionMode.Callback {

        TextActionModeCallback() {
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {

            mode.setTitle(null);
            mode.setSubtitle(null);
            mode.setTitleOptionalHint(true);

            menu.add(Menu.NONE, ID_COPY, MENU_ITEM_ORDER_COPY,
                    R.string.copy)
                    .setAlphabeticShortcut('c')
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            if (canPaste()) {
                menu.add(Menu.NONE, ID_PASTE, MENU_ITEM_ORDER_PASTE,
                        R.string.paste)
                        .setAlphabeticShortcut('v')
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            ActionMode.Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                if (!customCallback.onCreateActionMode(mode, menu)) {
                    stopTextSelectionMode();
                    return false;
                }
            }

            return true;
        }

        private ActionMode.Callback getCustomCallback() {
            return mCustomSelectionActionModeCallback;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {

            ActionMode.Callback customCallback = getCustomCallback();
            if (customCallback != null) {
                return customCallback.onPrepareActionMode(mode, menu);
            }
            return true;
        }


        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

            ActionMode.Callback customCallback = getCustomCallback();
            if (customCallback != null && customCallback.onActionItemClicked(mode, item)) {
                return true;
            }

            return onTextContextMenuItem(item.getItemId());
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mTextActionMode = null;
        }

    }

    /**
     * If provided, this ActionMode.Callback will be used to create the ActionMode when text
     * selection is initiated in this View.
     *
     * <p>The standard implementation populates the menu with a subset of Select All, Cut, Copy,
     * Paste, Replace and Share actions, depending on what this View supports.
     *
     * <p>A custom implementation can add new entries in the default menu in its
     * {@link ActionMode.Callback#onPrepareActionMode(ActionMode, Menu)}
     * method. The default actions can also be removed from the menu using
     * {@link Menu#removeItem(int)} and passing {@link android.R.id#selectAll},
     * {@link android.R.id#cut}, {@link android.R.id#copy}, {@link android.R.id#paste},
     * {@link android.R.id#replaceText} or {@link android.R.id#shareText} ids as parameters.
     *
     * <p>Returning false from
     * {@link ActionMode.Callback#onCreateActionMode(ActionMode, Menu)}
     * will prevent the action mode from being started.
     *
     * <p>Action click events should be handled by the custom implementation of
     * {@link ActionMode.Callback#onActionItemClicked(ActionMode,
     * MenuItem)}.
     *
     * <p>Note that text selection mode is not started when a TextView receives focus and the
     * {@link android.R.attr#selectAllOnFocus} flag has been set. The content is highlighted in
     * that case, to allow for quick replacement.
     */
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        mCustomSelectionActionModeCallback = actionModeCallback;
    }

    public boolean onTextContextMenuItem(int id) {
        if (id == ID_COPY) {
            final String selectedText = getSelectedText();
            if (selectedText.length() > 99 * 1024) {
                Toast.makeText(getContext(), R.string.toast_overflow_of_limit, Toast.LENGTH_LONG).show();
            } else {
                final ClipData plainText = ClipData.newPlainText("text", selectedText);
                try {
                    ClipboardManager clipboard =
                            (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(plainText);
                } catch (Throwable ignored) {
                }
                stopTextSelectionMode();
                Toast.makeText(getContext(), R.string.copied, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == ID_PASTE) {
            stopTextSelectionMode();
            final ClipboardManager clipboard =
                    (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return true;
            }
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0) {
                CharSequence text = clipData.getItemAt(0).coerceToText(getContext());
                sendText(text);
            }
            return true;
        }

        return false;
    }

    @NonNull
    private String getSelectedText() {
        if (mTerm == null || mSelRect.isInvalid()) {
            return "";
        }
        return mTerm.getText(mSelRect.startRow, mSelRect.endRow, mSelRect.startCol, mSelRect.endCol);
    }

    private final Runnable mShowFloatingToolbar = new Runnable() {
        @Override
        public void run() {
            if (mTextActionMode != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mTextActionMode.hide(0);  // hide off.
                }
            }
        }
    };

    void hideFloatingToolbar(int duration) {
        if (mTextActionMode != null) {
            removeCallbacks(mShowFloatingToolbar);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mTextActionMode.hide(duration);
            }
        }
    }

    private void showFloatingToolbar() {
        if (mTextActionMode != null) {
            int delay = ViewConfiguration.getDoubleTapTimeout();
            postDelayed(mShowFloatingToolbar, delay);
        }
    }

    private void updateFloatingToolbarVisibility(MotionEvent event) {
        if (mTextActionMode != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    hideFloatingToolbar(-1);
                    break;
                case MotionEvent.ACTION_UP:  // fall through
                case MotionEvent.ACTION_CANCEL:
                    showFloatingToolbar();
            }
        }
    }

    private int getCursorX(float x) {
        return (int) Math.ceil((x - mLeftOfScreenMargin) / mMetrics.charWidth);
    }

    private int getCursorY(float y) {
        int charHeight = mMetrics.charHeight;
        return (int) Math.ceil((y - charHeight - mTopOfScreenMargin) / charHeight + mScrollY / charHeight);
    }

    private int getPointX(int cx) {
        if (mTerm == null) {
            return -1;
        }
        if (cx > mTerm.getCols()) {
            cx = mTerm.getCols();
        }
        return Math.round(cx * mMetrics.charWidth) + mLeftOfScreenMargin;
    }

    private int getPointY(int cy) {
        return Math.round((cy - (mScrollY / mMetrics.charHeight)) * mMetrics.charHeight) + mTopOfScreenMargin;
    }

    static class TerminalRect {
        int startRow;
        int endRow;
        int startCol;
        int endCol;

        TerminalRect() {
            reset();
        }

        boolean isInvalid() {
            return startRow == -1
                    || endRow == 1
                    || startCol == -1
                    || endCol == -1;
        }

        public void reset() {
            startRow = endRow = startCol = endCol = -1;
        }
    }

    public interface ModifiersChangedListener {
        void onChanged(int modifiers);
    }
}
