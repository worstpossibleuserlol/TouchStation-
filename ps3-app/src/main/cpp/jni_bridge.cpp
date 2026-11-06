#include <jni.h>
#include <cstdio>
#include <string>
#include "ppu_interpreter.hpp"

extern "C" JNIEXPORT jstring JNICALL
Java_com_touchstation_ps3_PpuRuntime_nativeSelfTest(JNIEnv* env, jobject /* thiz */) {
    const auto result = touchstation::ps3::runPpuSelfTest();
    const std::string message = result.message + " Instructions=" +
        std::to_string(result.instructions) + " Result=0x";
    char value[32]{};
    std::snprintf(value, sizeof(value), "%llx",
                  static_cast<unsigned long long>(result.result));
    const std::string output = message + value;
    return env->NewStringUTF(output.c_str());
}
