#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdint.h>

/* libpalm_sdk.so keeps these in C++ PalmConfig, not in the Java setters.
 * Factory values on the X60 build: quality 70, brightness 70, pose 20 degrees,
 * palm size 210. A normal infrared palm never clears that, so enroll sits on 352. */
#define PALM_TAG "PalmTune"

static void *palm_symbol(void *lib, const char *name) {
    void *sym = dlsym(lib, name);
    if (sym == NULL) {
        __android_log_print(ANDROID_LOG_WARN, PALM_TAG, "missing %s", name);
    }
    return sym;
}

static void write_int(void *lib, const char *name, int value) {
    int *slot = (int *)palm_symbol(lib, name);
    if (slot != NULL) *slot = value;
}

static void write_float(void *lib, const char *name, float value) {
    float *slot = (float *)palm_symbol(lib, name);
    if (slot != NULL) *slot = value;
}

JNIEXPORT jint JNICALL
Java_com_xander_pos_palm_PalmTune_nativeApply(JNIEnv *env, jobject thiz) {
    void *lib;
    int *quality;
    int previous;
    (void)env;
    (void)thiz;
    lib = dlopen("libpalm_sdk.so", RTLD_NOW | RTLD_NOLOAD);
    if (lib == NULL) lib = dlopen("libpalm_sdk.so", RTLD_NOW);
    if (lib == NULL) {
        __android_log_print(ANDROID_LOG_WARN, PALM_TAG, "dlopen %s", dlerror());
        return -1;
    }
    quality = (int *)palm_symbol(lib, "_ZN10PalmConfig16minEnrollQualityE");
    if (quality == NULL) return -1;
    previous = *quality;
    *quality = 1;
    write_int(lib, "_ZN10PalmConfig19minEnrollBrightnessE", 1);
    write_int(lib, "_ZN10PalmConfig19maxEnrollBrightnessE", 255);
    write_int(lib, "_ZN10PalmConfig13maxEnrollRollE", 90);
    write_int(lib, "_ZN10PalmConfig14maxEnrollPitchE", 90);
    write_int(lib, "_ZN10PalmConfig12maxEnrollYawE", 90);
    write_int(lib, "_ZN10PalmConfig13minEnrollSizeE", 20);
    write_float(lib, "_ZN10PalmConfig20minEnrollOccupyRatioE", 0.02f);
    write_float(lib, "_ZN10PalmConfig22minEnrollPointResponseE", 0.01f);
    write_int(lib, "_ZN10PalmConfig16minVerifyQualityE", 1);
    write_int(lib, "_ZN10PalmConfig19minVerifyBrightnessE", 1);
    write_int(lib, "_ZN10PalmConfig19maxVerifyBrightnessE", 255);
    write_int(lib, "_ZN10PalmConfig13maxVerifyRollE", 360);
    write_int(lib, "_ZN10PalmConfig14maxVerifyPitchE", 90);
    write_int(lib, "_ZN10PalmConfig12maxVerifyYawE", 90);
    write_int(lib, "_ZN10PalmConfig13minVerifySizeE", 20);
    write_float(lib, "_ZN10PalmConfig20minVerifyOccupyRatioE", 0.02f);
    __android_log_print(ANDROID_LOG_INFO, PALM_TAG, "native quality %d -> 1", previous);
    return previous;
}
