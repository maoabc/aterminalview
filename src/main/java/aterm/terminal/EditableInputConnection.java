package aterm.terminal;

import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

import aterm.terminalview.BuildConfig;


public class EditableInputConnection extends BaseInputConnection {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String TAG = "EditableInputConnection";
    static final int END_UNDO_ACTION = 3865;//防止和其他消息混一起
    static final Object MSG_OBJ = new Object();//加上唯一对象标识，防止消息混乱

    final TerminalView mTextView;

    public EditableInputConnection(TerminalView textview) {
        super(textview, true);
        mTextView = textview;
    }

//    public Editable getEditable() {
//        TextView tv = mTextView;
//        if (tv != null) {
//            return tv.getEditableText();
//        }
//        return null;
//    }


    public boolean beginBatchEdit() {
        if (DEBUG) Log.d(TAG, "beginBatchEdit: ");
//        mTextView.beginBatchEdit();
        return true;
    }

    public boolean endBatchEdit() {
        if (DEBUG) Log.d(TAG, "endBatchEdit: ");
//        mTextView.endBatchEdit();
        return true;
    }

    @Override
    public CharSequence getTextAfterCursor(int length, int flags) {
        if (DEBUG) Log.d(TAG, "getTextAfterCursor: " + length);
        if (mTextView == null) {
            return "";
        }
        CharSequence textAfterCursor = super.getTextAfterCursor(length, flags);
        if (DEBUG) Log.d(TAG, "getTextAfterCursor: " + textAfterCursor);
        return textAfterCursor;
    }


    @Override
    public CharSequence getTextBeforeCursor(int length, int flags) {
        if (DEBUG) Log.d(TAG, "getTextBeforeCursor: " + length);
        if (mTextView == null) {
            return "";
        }
        CharSequence textBeforeCursor = super.getTextBeforeCursor(length, flags);
        if (DEBUG) Log.d(TAG, "getTextBeforeCursor: " + textBeforeCursor);
        return textBeforeCursor;
    }

    public boolean clearMetaKeyStates(int states) {
        if (DEBUG) Log.d(TAG, "clearMetaKeyStates " + states);

        return super.clearMetaKeyStates(states);
    }

    public boolean commitCompletion(CompletionInfo text) {
        if (DEBUG) Log.d(TAG, "commitCompletion " + text);
//        mTextView.beginBatchEdit();
//        mTextView.onCommitCompletion(text);
//        mTextView.endBatchEdit();
        return false;
    }

    public boolean performEditorAction(int actionCode) {
        if (DEBUG) Log.d(TAG, "performEditorAction " + actionCode);
//        mTextView.onEditorAction(actionCode);
        return true;
    }

    public boolean performContextMenuAction(int id) {
        if (DEBUG) Log.d(TAG, "performContextMenuAction " + id + "  ");
        return false;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (DEBUG) Log.d(TAG, "setComposingText: " + text);
        return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingRegion(int start, int end) {
        if (DEBUG) Log.d(TAG, "setComposingRegion: " + start + "  " + end);
        return super.setComposingRegion(start, end);
    }


    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (DEBUG) Log.d(TAG, "commitText: " + text);

        return super.commitText(text, newCursorPosition);
    }


    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (DEBUG) Log.d(TAG, "sendKeyEvent: " + event.getKeyCode());
        return super.sendKeyEvent(event);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean finishComposingText() {
        if (DEBUG) Log.d(TAG, "finishComposingText: ");
        return super.finishComposingText();
    }

    public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
        if (DEBUG) Log.d(TAG, "getExtracted " + flags);
//        if (mTextView != null) {
//            ExtractedText et = new ExtractedText();
//            if (mTextView.extractText(request, et)) {
//                if ((flags & GET_EXTRACTED_TEXT_MONITOR) != 0) {
//                    mTextView.setExtracting(request);
//                }
//                return et;
//            }
//        }
        return null;
    }

    @Override
    public boolean setSelection(int start, int end) {
        if (mTextView == null) {
            return false;
        }


        return super.setSelection(start, end);
    }

}
