
package aterm.terminal;

import android.content.Context;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.Scroller;


public class Touch {
    public static final String TAG = "Touch";


    private Touch() {
    }


    /**
     * Handles touch events for dragging.  You may want to do other actions
     * like moving the cursor on touch as well.
     */
    public static boolean onTouchEvent(TerminalView widget, MotionEvent event) {
        DragState[] ds;
        Spannable buffer = widget.editable;

        ds = buffer.getSpans(0, buffer.length(), DragState.class);
//        Log.d(TAG, "onTouchEvent: " + ds);

        if (ds.length > 0) {
            if (ds[0].mVelocityTracker == null) {
                ds[0].mVelocityTracker = VelocityTracker.obtain();
            }
            ds[0].mVelocityTracker.addMovement(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (ds.length > 0) {
                    if (ds[0].mFlingRunnable != null) {
                        ds[0].mFlingRunnable.endFling();
                        widget.cancelLongPress();
                    }
                }
                for (int i = 0; i < ds.length; i++) {
                    buffer.removeSpan(ds[i]);
                }

                DragState dragState = new DragState(event.getX(), event.getY(),
                        widget.mScrollY);
                dragState.mIncY = 0;
                buffer.setSpan(dragState,
                        0, 0, Spannable.SPAN_MARK_MARK);

                return true;

            case MotionEvent.ACTION_UP: {
                boolean result = false;

                if (ds.length > 0 && ds[0].mUsed) {
                    result = true;
                    final VelocityTracker velocityTracker = ds[0].mVelocityTracker;
                    int mMinimumVelocity = ViewConfiguration.get(widget.getContext()).getScaledMinimumFlingVelocity();
                    int mMaximumVelocity = ViewConfiguration.get(widget.getContext()).getScaledMaximumFlingVelocity();
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    final int initialVelocityY = (int) velocityTracker.getYVelocity();

                    if (Math.abs(initialVelocityY) > mMinimumVelocity) {
                        if (ds[0].mFlingRunnable == null) {
                            ds[0].mFlingRunnable = new FlingRunnable(widget.getContext());
                        }

                        ds[0].mFlingRunnable.start(widget, -initialVelocityY);
                    }
                }

                if (ds[0].mVelocityTracker != null) {
                    ds[0].mVelocityTracker.recycle();
                    ds[0].mVelocityTracker = null;
                }

                return result;
            }
            case MotionEvent.ACTION_MOVE:

                if (ds.length > 0) {
                    if (!ds[0].mFarEnough) {
                        int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();

                        if (Math.abs(event.getX() - ds[0].mX) >= slop ||
                                Math.abs(event.getY() - ds[0].mY) >= slop) {
                            ds[0].mFarEnough = true;
                        }
                    }

                    if (ds[0].mFarEnough) {
                        ds[0].mUsed = true;
                        float dy;
                        dy = ds[0].mY - event.getY();
                        ds[0].mIncY += dy;
                        ds[0].mX = event.getX();
                        ds[0].mY = event.getY();

                        if (widget.isAltScreen()) {
//                            Log.d(TAG, "onTouchEvent: dy " + ds[0].mIncY);
                            ds[0].mIncY -= widget.altScreenScroll((int) (ds[0].mIncY));
                        } else {
                            int ny = widget.mScrollY + (int) dy;


                            ny = Math.max(-widget.getTotalHeight(), Math.min(0, ny));

                            int oldY = widget.mScrollY;


                            widget.myScrollTo(ny);

                            // If we actually scrolled, then cancel the up action.
                            if (oldY != widget.mScrollY) {
                                widget.cancelLongPress();
                            }
                        }

                        return true;
                    }
                }
        }

        return false;
    }


    public static long getInitialScrollY(TerminalView widget, Spannable buffer) {
        DragState[] ds = buffer.getSpans(0, buffer.length(), DragState.class);
        return ds.length > 0 ? ds[0].mScrollY : -1;
    }


    public static void cancelFling(TerminalView widget, Spannable buffer) {
        DragState[] ds;

        ds = buffer.getSpans(0, buffer.length(), DragState.class);

        if (ds.length > 0) {
            if (ds[0].mFlingRunnable != null) {
                ds[0].mFlingRunnable.endFling();
                widget.cancelLongPress();
            }
        }

    }


    private static class DragState implements NoCopySpan {
        float mX;
        float mY;
        int mScrollY;
        boolean mFarEnough;
        boolean mUsed;
        VelocityTracker mVelocityTracker;
        FlingRunnable mFlingRunnable;
        float mIncY;

        DragState(float x, float y, int scrollY) {
            mX = x;
            mY = y;
            mScrollY = scrollY;
            mVelocityTracker = null;
            mFlingRunnable = null;
        }
    }

    private static class FlingRunnable implements Runnable {

        static final int TOUCH_MODE_REST = -1;
        static final int TOUCH_MODE_FLING = 3;

        int mTouchMode = TOUCH_MODE_REST;

        /**
         * Tracks the decay of a fling scroll
         */
        private final Scroller mScroller;

        /**
         * Y value reported by mScroller on the previous fling
         */
        private int mLastFlingY;


        private TerminalView mWidget = null;

        FlingRunnable(Context context) {
            mScroller = new Scroller(context);
        }

        void start(TerminalView parent, int initialVelocityY) {
            mWidget = parent;

            int initialY = parent.isAltScreen() ? Integer.MIN_VALUE / 2 : parent.mScrollY; //initialVelocity < 0 ? Integer.MAX_VALUE : 0;

//            System.out.println("fling start " + initialY + "   " + initialVelocityY);
            mLastFlingY = initialY;
            mScroller.fling(0, initialY, 0, initialVelocityY,
                    Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0);
            mTouchMode = TOUCH_MODE_FLING;

            mWidget.post(this);

        }

//        void startScroll(int distance, int duration) {
//            int initialY = distance < 0 ? Integer.MAX_VALUE : 0;
//            mLastFlingY = initialY;
//            mScroller.startScroll(0, initialY, 0, distance, duration);
//            mTouchMode = TOUCH_MODE_FLING;
//            post(this);
//        }

        private void endFling() {
            mTouchMode = TOUCH_MODE_REST;

            if (mWidget != null) {
                mWidget.removeCallbacks(this);
                mWidget = null;
            }

        }

        public void run() {
            switch (mTouchMode) {
                default:
                    return;

                case TOUCH_MODE_FLING: {
                    final TerminalView widget = this.mWidget;

                    final Scroller scroller = mScroller;
                    int y = scroller.getCurrY();
                    boolean more = scroller.computeScrollOffset();
                    int deltaY = mLastFlingY - y;
                    if (y != 0) {//防止滑动时突然回到初始位置，导致闪屏



                        if (widget.isAltScreen()) {
//                        widget.doVerticalScroll(y);

                        } else {

                            y = Math.max(-widget.getTotalHeight(), Math.min(0, y));


                            widget.myScrollTo(y);
                        }

                    }

                    if (more && deltaY != 0) {
                        widget.invalidate();
                        mLastFlingY = y;
                        widget.post(this);
                    } else {
                        endFling();
                    }
                    break;
                }
            }

        }
    }

}
