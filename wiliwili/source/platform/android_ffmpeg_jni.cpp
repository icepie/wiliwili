#ifdef ANDROID

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

namespace {

constexpr const char* TAG = "wiliwili-ffmpeg-jni";

using AvJniSetJavaVmFunc = int (*)(void* vm, void* log_ctx);
using AvJniSetAndroidAppCtxFunc = int (*)(void* app_ctx, void* log_ctx);

JavaVM* gJavaVm         = nullptr;
jobject gAndroidAppCtx = nullptr;
void* gAvcodec         = nullptr;

void logInfo(const char* message) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "%s", message);
}

void logWarn(const char* message) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "%s", message);
}

void* getAvcodecHandle() {
    if (!gAvcodec) {
        gAvcodec = dlopen("libavcodec.so", RTLD_NOW | RTLD_LOCAL);
        if (!gAvcodec) {
            __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to open libavcodec.so: %s", dlerror());
        }
    }
    return gAvcodec;
}

template <typename Func>
Func getAvcodecSymbol(const char* name) {
    void* handle = getAvcodecHandle();
    if (!handle) {
        return nullptr;
    }

    dlerror();
    auto* symbol = dlsym(handle, name);
    const char* error = dlerror();
    if (error) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to find %s: %s", name, error);
        return nullptr;
    }

    return reinterpret_cast<Func>(symbol);
}

void setJavaVm() {
    if (!gJavaVm) {
        logWarn("JavaVM is not available");
        return;
    }

    auto avJniSetJavaVm = getAvcodecSymbol<AvJniSetJavaVmFunc>("av_jni_set_java_vm");
    if (!avJniSetJavaVm) {
        return;
    }

    int result = avJniSetJavaVm(gJavaVm, nullptr);
    __android_log_print(ANDROID_LOG_INFO, TAG, "av_jni_set_java_vm returned %d", result);
}

void setAndroidAppContext(JNIEnv* env, jobject context) {
    if (!env || !context) {
        logWarn("Android context is not available");
        return;
    }

    auto avJniSetAndroidAppCtx =
        getAvcodecSymbol<AvJniSetAndroidAppCtxFunc>("av_jni_set_android_app_ctx");
    if (!avJniSetAndroidAppCtx) {
        return;
    }

    if (gAndroidAppCtx) {
        env->DeleteGlobalRef(gAndroidAppCtx);
    }
    gAndroidAppCtx = env->NewGlobalRef(context);

    int result = avJniSetAndroidAppCtx(gAndroidAppCtx, nullptr);
    __android_log_print(ANDROID_LOG_INFO, TAG, "av_jni_set_android_app_ctx returned %d", result);
}

}  // namespace

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    gJavaVm = vm;
    setJavaVm();
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_cn_xfangfang_wiliwili_WiliwiliActivity_nativeInitFFmpegAndroid(JNIEnv* env, jobject, jobject context) {
    setJavaVm();
    setAndroidAppContext(env, context);
    logInfo("FFmpeg Android JNI context initialized");
}

#endif
