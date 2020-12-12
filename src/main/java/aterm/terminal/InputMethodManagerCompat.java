package aterm.terminal;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

class InputMethodManagerCompat {

    private static InputMethodManager manager;

    static void initInstance(final View view) {
        manager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    static InputMethodManager peekInstance(final View view) {
        return manager;
    }
}
