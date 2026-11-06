#include <jni.h>
#include <cstdint>
#include <string>
#include <vector>
#include "ppu_interpreter.hpp"
#include "rsx_command_processor.hpp"
#include "spu_interpreter.hpp"

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
    const std::uint64_t executed = cpu.run(kInstructionBudget);
    std::string report = "PPC64 ELF segments mapped successfully.\n";
    report += "PPU instructions executed: " + std::to_string(executed) + ".\n";
    report += "Guest PC: 0x";
    static constexpr char hex[] = "0123456789abcdef";
    std::uint64_t pc = cpu.state().pc;
    std::string digits;
    do {
        digits.push_back(hex[pc & 0xfu]);
        pc >>= 4u;
    } while (pc != 0u);
    for (auto it = digits.rbegin(); it != digits.rend(); ++it) report.push_back(*it);

    if (cpu.state().halted) {
        report += "\nPPU stopped: ";
        report += cpu.state().fault;
    } else {
        report += "\nInstruction budget reached or guest yielded. This is not proof that the title booted.";
    }
    report += "\nRSX frame output and PS3 firmware services are not connected to this PPU prototype.";
    return toJavaString(env, report);
}
