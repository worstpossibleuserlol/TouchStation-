#include <jni.h>
#include <cstdint>
#include <limits>
#include <string>
#include <vector>
#include "ppu_interpreter.hpp"
#include "rsx_command_processor.hpp"
#include "spu_interpreter.hpp"
#include "guest_pipeline.hpp"

namespace {
jstring toJavaString(JNIEnv* env, const std::string& message) {
    return env->NewStringUTF(message.c_str());
}
std::string runAllDiagnostics() {
    const auto ppu = touchstation::ps3::runPpuSelfTest();
    const auto spu = touchstation::ps3::runSpuSelfTest();
    const auto rsx = touchstation::ps3::runRsxSelfTest();
    std::string report = "PPU: " + ppu.message + "\nSPU: " + spu.message + "\nRSX: " + rsx.message;
    report += "\nPPC64 ELF loader: guarded PT_LOAD validation is available.";
    return report;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_touchstation_ps3_PpuRuntime_nativeSelfTest(JNIEnv* env, jobject /* thiz */) {
    return toJavaString(env, runAllDiagnostics());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_touchstation_ps3_PpuRuntime_nativeBootPpc64Elf(JNIEnv* env, jobject /* thiz */,
                                                        jbyteArray image) {
    if (!image) return toJavaString(env, "Boot rejected: no game file was supplied.");
    const jsize length = env->GetArrayLength(image);
    constexpr jsize kMaximumElfBytes = 64 * 1024 * 1024;
    if (length < 64) return toJavaString(env, "Boot rejected: file is too small to be an ELF64 executable.");
    if (length > kMaximumElfBytes)
        return toJavaString(env, "Boot rejected: prototype ELF import limit is 64 MiB.");

    std::vector<std::uint8_t> bytes(static_cast<std::size_t>(length));
    env->GetByteArrayRegion(image, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return toJavaString(env, "Boot rejected: Android could not read the selected file.");
    }

    touchstation::ps3::PpuInterpreter cpu;
    if (!cpu.loadPpc64Elf(bytes))
        return toJavaString(env, std::string("ELF loader rejected this file: ") + cpu.state().fault);

    constexpr std::uint64_t kInstructionBudget = 100000u;
    std::uint64_t executed = 0;
    while (executed < kInstructionBudget && !cpu.state().halted &&
           !cpu.state().host_call_pending) {
        if (!cpu.step()) break;
        ++executed;
    }
    std::string report = "PPC64 ELF segments mapped successfully.\\n";
    report += "PPU instructions executed: " + std::to_string(executed) + ".\\n";
    report += "Guest PC: 0x";
    static constexpr char hex[] = "0123456789abcdef";
    std::uint64_t pc = cpu.state().pc;
    std::string digits;
    do {
        digits.push_back(hex[pc & 0xfu]);
        pc >>= 4u;
    } while (pc != 0u);
    for (auto it = digits.rbegin(); it != digits.rend(); ++it) report.push_back(*it);

    if (cpu.state().host_call_pending) {
        report += "\\nTouchStation development host-call encountered: ";
        report += std::to_string(cpu.state().host_call_number);
        report += ". This ABI is not the PS3 LV2 syscall interface.";
    } else if (cpu.state().halted) {
        report += "\\nPPU stopped: ";
        report += cpu.state().fault;
    } else {
        report += "\\nInstruction budget reached or guest yielded. This is not proof that the title booted.";
    }
    report += "\\nThe bundled demo is the only guest currently wired into the RSX frame and SPU PCM sinks.";
    return toJavaString(env, report);
}


extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_touchstation_ps3_PpuRuntime_nativeRunPipelineFrame(JNIEnv* env, jobject /* thiz */,
                                                            jint width, jint height) {
    if (width <= 0 || height <= 0 || width > 2048 || height > 2048) {
        return env->NewByteArray(0);
    }
    const auto result = touchstation::ps3::GuestPipeline().runDemo(
        static_cast<std::uint32_t>(width), static_cast<std::uint32_t>(height));
    if (!result.passed || result.rgba.empty() ||
        result.rgba.size() > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
        return env->NewByteArray(0);
    }

    const auto length = static_cast<jsize>(result.rgba.size());
    jbyteArray output = env->NewByteArray(length);
    if (!output) return nullptr;
    env->SetByteArrayRegion(output, 0, length,
        reinterpret_cast<const jbyte*>(result.rgba.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return env->NewByteArray(0);
    }
    return output;
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_touchstation_ps3_PpuRuntime_nativeRunPipelineAudio(JNIEnv* env, jobject /* thiz */) {
    const auto result = touchstation::ps3::GuestPipeline().runDemo(64u, 64u);
    if (!result.passed || result.pcm.empty() ||
        result.pcm.size() > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
        return env->NewShortArray(0);
    }

    const auto length = static_cast<jsize>(result.pcm.size());
    jshortArray output = env->NewShortArray(length);
    if (!output) return nullptr;
    env->SetShortArrayRegion(output, 0, length,
        reinterpret_cast<const jshort*>(result.pcm.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return env->NewShortArray(0);
    }
    return output;
}
