package aterm.terminal;

import androidx.annotation.Keep;



/* Setting output callback will override the buffer logic */
//typedef void VTermOutputCallback(const char *s, size_t len, void *user);

@Keep
public interface OutputCallback {
    void writeToPty(byte[] bytes, int len);
}
