
package aterm.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.ColorInt;

import aterm.terminalview.R;


/**
 * Helper class for TerminalView to draw and control the Fast Scroll thumb
 */
class FastScroller {
    private static final String TAG = "FastScroller";

    // Scroll thumb not showing
    private static final int STATE_NONE = 0;
    // Not implemented yet - fade-in transition
    private static final int STATE_ENTER = 1;
    // Scroll thumb visible and moving along with the scrollbar
    private static final int STATE_VISIBLE = 2;
    // Scroll thumb being dragged by user
    private static final int STATE_DRAGGING = 3;
    // Scroll thumb fading out due to inactivity timeout
    private static final int STATE_EXIT = 4;

    private final TerminalView mTerminalView;
    private final Drawable mThumbDrawable;
    private final Drawable mTrackDrawable;

    private final int mThumbH;
    private final int mThumbW;
    private final int mTrackW;

    private final ScrollFade mScrollFade;

    private int mThumbY;


    private int mScrollY;

    private int mTotalHeight = -1;
    private boolean mCanScrollable;


    private int mState;

    private Handler mHandler = new Handler();


    private boolean mChangedBounds;

    private long mLastEventTime = 0;
    private static int[] STATESET_EMPTY = {android.R.attr.state_empty};
    private static int[] STATESET_PRESSED = {android.R.attr.state_pressed, android.R.attr.state_focused};

    FastScroller(TerminalView view, @ColorInt int fg) {
        mTerminalView = view;
        Context context = view.getContext();
        // Get both the scrollbar states drawables
        mThumbDrawable = context.getDrawable(
                R.drawable.fastscroll_thumb_material);
        Drawable drawable = context.getDrawable(R.drawable.ic_fastscroll_thumb);
        drawable.setTint(fg);
        ((StateListDrawable) mThumbDrawable).addState(new int[]{-android.R.attr.state_pressed}, drawable);

        mTrackDrawable = context.getDrawable(R.drawable.fastscroll_track_material);
        mTrackDrawable.setTint(fg);

        mThumbW = mThumbDrawable.getIntrinsicWidth();
        mThumbH = mThumbDrawable.getIntrinsicHeight();

        mTrackW = mTrackDrawable.getIntrinsicWidth();

        mChangedBounds = true;

        mScrollFade = new ScrollFade();

        mState = STATE_NONE;
    }

    public void setState(int state) {
        switch (state) {
            case STATE_NONE:
                mHandler.removeCallbacks(mScrollFade);
                mTerminalView.invalidate();
                break;
            case STATE_VISIBLE:
                if (mState != STATE_VISIBLE) { // Optimization
                    resetThumbPos();
                }
                // Fall through
            case STATE_DRAGGING:
                mHandler.removeCallbacks(mScrollFade);
                break;
            case STATE_EXIT:
                mTerminalView.invalidate();
                break;
        }
        mState = state;
    }

    public int getState() {
        return mState;
    }

    private void resetThumbPos() {
        final int viewWidth = mTerminalView.getWidth();
        // Bounds are always top right. Y coordinate get's translated during draw
        mThumbDrawable.setBounds(viewWidth - mThumbW, 0, viewWidth, mThumbH);
        mThumbDrawable.setAlpha(ScrollFade.ALPHA_MAX);
        mThumbDrawable.setState(STATESET_EMPTY);

        mTrackDrawable.setBounds(viewWidth - mTrackW, 0, viewWidth, mTerminalView.getHeight());
        mTrackDrawable.setAlpha(ScrollFade.ALPHA_MAX);
    }

    void stop() {
        setState(STATE_NONE);
    }

    boolean isVisible() {
        return !(mState == STATE_NONE);
    }

    public void draw(Canvas canvas) {

        if (mState == STATE_NONE) {
            // No need to draw anything
            return;
        }

        final int viewHeight = mTerminalView.getHeight();
        final int viewWidth = mTerminalView.getWidth();

        final int y = Math.min(mThumbY, viewHeight - mThumbH);


        int alpha = -1;
        if (mState == STATE_EXIT) {
            alpha = mScrollFade.getAlpha();
            if (alpha < ScrollFade.ALPHA_MAX / 2) {
                mThumbDrawable.setAlpha(alpha * 2);
                mTrackDrawable.setAlpha(alpha * 2);
            }
            int left = viewWidth - (mThumbW * alpha) / ScrollFade.ALPHA_MAX;
            mThumbDrawable.setBounds(left, 0, viewWidth, mThumbH);

            int tleft = viewWidth - (mTrackW * alpha) / ScrollFade.ALPHA_MAX;
            mTrackDrawable.setBounds(tleft, 0, viewWidth, viewHeight);

            mChangedBounds = true;
        }

        //draw track
        mTrackDrawable.draw(canvas);

        canvas.translate(0, y);
        mThumbDrawable.draw(canvas);
        canvas.translate(0, -y);

        // If user is dragging the scroll bar, draw the alphabet overlay
        if (alpha == 0) { // Done with exit
            setState(STATE_NONE);
        } else {
            mTerminalView.invalidate();
        }
    }

    void onSizeChanged(int w, int h, int oldw, int oldh) {
        if ((oldh - mThumbH) != 0) {//大小改变，原本的滑块也需要按比例放大或缩小
            mThumbY = (int) ((mThumbY * (double) (h - mThumbH)) / (oldh - mThumbH));
        }
        if (mThumbDrawable != null) {
            mThumbDrawable.setBounds(w - mThumbW, 0, w, mThumbH);
        }
        //修复滑轨高度不随view改变
        if (mTrackDrawable != null) {
            mTrackDrawable.setBounds(w - mTrackW, 0, w, h);
        }
    }

    void onScroll(TerminalView view, int scrollY, int totalHeight) {
        if (mTotalHeight != totalHeight) {
            mTotalHeight = totalHeight;
            mCanScrollable = mTotalHeight > 0;
        }
        if (!mCanScrollable) {
            if (mState != STATE_NONE) {
                setState(STATE_NONE);
            }
            return;
        }
        if (totalHeight > 0 && mState != STATE_DRAGGING) {
            double p = ((totalHeight + scrollY) / (double) totalHeight);
            mThumbY = (int) ((view.getHeight() - mThumbH) * p);
//            Log.d(TAG, "onScroll: " + p + "  " + totalHeight + "  " + scrollY);

            if (mChangedBounds) {
                resetThumbPos();
                mChangedBounds = false;
            }
        }
        if (scrollY == 0 || totalHeight == 0) {
            mThumbY = view.getHeight() - mThumbH;
        }

        if (scrollY == mScrollY) {
            return;
        }
        mScrollY = scrollY;
        if (mState != STATE_DRAGGING) {
            setState(STATE_VISIBLE);
            mHandler.postDelayed(mScrollFade, 1500);
        }
    }


    private void scrollTo(double position) {
        position = Math.min(position, 1.0f);
        int height = mTerminalView.getTotalHeight();
        mTerminalView.myScrollTo(-(int) (height * position));
    }

    private void cancelFling() {
        // Cancel the list fling
        MotionEvent cancelFling = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        mTerminalView.onTouchEvent(cancelFling);
        cancelFling.recycle();
    }

    boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mState > STATE_NONE && ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (isPointInside(ev.getX(), ev.getY())) {
                mThumbDrawable.setState(STATESET_PRESSED);
                setState(STATE_DRAGGING);
                return true;
            }
        }
        return false;
    }

    boolean onTouchEvent(MotionEvent me) {
        if (mState == STATE_NONE) {
            return false;
        }

        final int action = me.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            if (isPointInside(me.getX(), me.getY())) {
                mThumbDrawable.setState(STATESET_PRESSED);
                setState(STATE_DRAGGING);

                cancelFling();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) { // don't add ACTION_CANCEL here
            if (mState == STATE_DRAGGING) {
                mThumbDrawable.setState(STATESET_EMPTY);
                setState(STATE_VISIBLE);
                final Handler handler = mHandler;
                handler.removeCallbacks(mScrollFade);
                handler.postDelayed(mScrollFade, 1000);
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mState == STATE_DRAGGING) {
                long now = System.currentTimeMillis();
                if ((now - mLastEventTime) > 30) {
                    mLastEventTime = now;

                    final int viewHeight = mTerminalView.getHeight();

                    int newThumbY = (int) me.getY() - mThumbH / 2;
                    if (newThumbY < 0) {
                        newThumbY = 0;
                    } else if (newThumbY + mThumbH > viewHeight) {
                        newThumbY = viewHeight;
                    }
                    if (Math.abs(mThumbY - newThumbY) < 2) {
                        return true;
                    }
                    mThumbY = newThumbY;
                    scrollTo((viewHeight - mThumbY) / (double) (viewHeight - mThumbH));
                }
                return true;
            }
        }
        return false;
    }

    private boolean isPointInside(float x, float y) {
        return x > mTerminalView.getWidth() - mThumbW * 3 && y >= mThumbY && y <= mThumbY + mThumbH;
    }

    public class ScrollFade implements Runnable {

        long mStartTime;
        long mFadeDuration;
        static final int ALPHA_MAX = 250;
        static final long FADE_DURATION = 250;

        void startFade() {
            mFadeDuration = FADE_DURATION;
            mStartTime = SystemClock.uptimeMillis();
            setState(STATE_EXIT);
        }

        int getAlpha() {
            if (getState() != STATE_EXIT) {
                return ALPHA_MAX;
            }
            int alpha;
            long now = SystemClock.uptimeMillis();
            if (now > mStartTime + mFadeDuration) {
                alpha = 0;
            } else {
                alpha = (int) (ALPHA_MAX - ((now - mStartTime) * ALPHA_MAX) / mFadeDuration);
            }
            return alpha;
        }

        public void run() {
            if (getState() != STATE_EXIT) {
                startFade();
                return;
            }

            if (getAlpha() > 0) {
                mTerminalView.invalidate();
            } else {
                setState(STATE_NONE);
            }
        }
    }
}
