#include <jni.h>
#include <android/log.h>
#include <cstdarg>
#include <cstdio>

#include "libcpuminer/libcpuminer.h"

#define Log(...) \
    ((void)__android_log_print(ANDROID_LOG_INFO, "sugarminer", __VA_ARGS__))

static JavaVM *g_jvm = nullptr;
static jclass g_class = nullptr;
static jmethodID g_output_method = nullptr;

// Miner logs are not guaranteed to be valid modified UTF-8; keep them ASCII-safe
// before handing them to NewStringUTF.
static void sanitize_log(char *message) {
    for (char *p = message; *p != '\0'; ++p) {
        if (static_cast<unsigned char>(*p) > 0x7f) {
            *p = '?';
        }
    }
}

extern "C"
void vprintf_app(const char *format, va_list arg) {
    if (g_jvm == nullptr || g_class == nullptr || g_output_method == nullptr) {
        return;
    }

    char message[1024];
    vsnprintf(message, sizeof(message), format, arg);
    message[sizeof(message) - 1] = '\0';
    sanitize_log(message);

    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return;
        }
        attached = true;
    }

    jstring log_message = env->NewStringUTF(message);
    if (log_message != nullptr) {
        env->CallStaticVoidMethod(g_class, g_output_method, log_message);
        env->DeleteLocalRef(log_message);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    Log("JNI_OnLoad");
    g_jvm = vm;

    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    jclass clazz = env->FindClass("com/nugetzrul3/minersworldcoinmininglibrary/SugarMiner");
    if (clazz == nullptr) {
        return -1;
    }
    g_class = static_cast<jclass>(env->NewGlobalRef(clazz));
    env->DeleteLocalRef(clazz);
    if (g_class == nullptr) {
        return -1;
    }

    g_output_method = env->GetStaticMethodID(g_class, "output", "(Ljava/lang/String;)V");
    if (g_output_method == nullptr) {
        return -1;
    }

    init(vprintf_app);
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nugetzrul3_minersworldcoinmininglibrary_SugarMiner_initMining(
        JNIEnv * /*env*/, jobject /*this*/) {
    Log("initMining");
    return init(vprintf_app);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nugetzrul3_minersworldcoinmininglibrary_SugarMiner_startMining(
        JNIEnv *env, jobject /*this*/,
        jstring url, jstring user, jstring password, jint n_threads, jint algo) {
    Log("startMining");

    if (url == nullptr || user == nullptr || password == nullptr) {
        return -1;
    }

    const char *c_url = env->GetStringUTFChars(url, nullptr);
    const char *c_user = env->GetStringUTFChars(user, nullptr);
    const char *c_password = env->GetStringUTFChars(password, nullptr);
    if (c_url == nullptr || c_user == nullptr || c_password == nullptr) {
        if (c_url != nullptr) env->ReleaseStringUTFChars(url, c_url);
        if (c_user != nullptr) env->ReleaseStringUTFChars(user, c_user);
        if (c_password != nullptr) env->ReleaseStringUTFChars(password, c_password);
        return -1;
    }

    int result = start(c_url, c_user, c_password, n_threads, algo);

    env->ReleaseStringUTFChars(url, c_url);
    env->ReleaseStringUTFChars(user, c_user);
    env->ReleaseStringUTFChars(password, c_password);
    return result;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nugetzrul3_minersworldcoinmininglibrary_SugarMiner_isMining(
        JNIEnv * /*env*/, jobject /*this*/) {
    return static_cast<jboolean>(is_running());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_nugetzrul3_minersworldcoinmininglibrary_SugarMiner_stopMining(
        JNIEnv * /*env*/, jobject /*this*/) {
    Log("stopMining");
    return stop();
}
