


#include "jni.h"
#include "utils/log.h"

#define LOG_TAG "Terminal"

extern int register_aterm_terminal_Terminal(JNIEnv *env);


JavaVM *javaVM;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    javaVM = vm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed");
        return -1;
    }

    register_aterm_terminal_Terminal(env);

    return JNI_VERSION_1_6;
}
