#include "ppu_interpreter.hpp"
#include "guest_pipeline.hpp"
#include "rsx_command_processor.hpp"
#include "spu_interpreter.hpp"

#include <cstdint>
#include <iostream>
#include <vector>

namespace {
void putBe16(std::vector<std::uint8_t>& bytes, std::size_t offset, std::uint16_t value) {
    bytes[offset] = static_cast<std::uint8_t>(value >> 8u);
    bytes[offset + 1u] = static_cast<std::uint8_t>(value);
}
void putBe32(std::vector<std::uint8_t>& bytes, std::size_t offset, std::uint32_t value) {
    bytes[offset] = static_cast<std::uint8_t>(value >> 24u);
    bytes[offset + 1u] = static_cast<std::uint8_t>(value >> 16u);
    bytes[offset + 2u] = static_cast<std::uint8_t>(value >> 8u);
    bytes[offset + 3u] = static_cast<std::uint8_t>(value);
}
void putBe64(std::vector<std::uint8_t>& bytes, std::size_t offset, std::uint64_t value) {
    for (std::size_t i = 0; i < 8u; ++i)
        bytes[offset + i] = static_cast<std::uint8_t>(value >> (56u - static_cast<unsigned>(i * 8u)));
}

bool testPpc64ElfLoader() {
    using touchstation::ps3::PpuInterpreter;
    std::vector<std::uint8_t> elf(128u, 0u);
    elf[0] = 0x7fu; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F';
    elf[4] = 2u; elf[5] = 2u; elf[6] = 1u;
    putBe16(elf, 16u, 2u);
    putBe16(elf, 18u, 21u);
    putBe32(elf, 20u, 1u);
    putBe64(elf, 24u, 0x10000u);
    putBe64(elf, 32u, 64u);
    putBe16(elf, 52u, 64u);
    putBe16(elf, 54u, 56u);
    putBe16(elf, 56u, 1u);
    putBe32(elf, 64u, 1u);
    putBe32(elf, 68u, 5u);
    putBe64(elf, 72u, 120u);
    putBe64(elf, 80u, 0x10000u);
    putBe64(elf, 88u, 0x10000u);
    putBe64(elf, 96u, 8u);
    putBe64(elf, 104u, 8u);
    putBe64(elf, 112u, 4u);
    putBe32(elf, 120u, PpuInterpreter::encodeAddi(3u, 0u, 42));
    putBe32(elf, 124u, PpuInterpreter::encodeAddi(4u, 0u, 99));

    PpuInterpreter cpu;
    return cpu.loadPpc64Elf(elf) && cpu.state().pc == 0x10000u &&
           cpu.run(1u) == 1u && cpu.state().gpr[3] == 42u && !cpu.state().halted;
}
} // namespace

int main() {
    const auto ppu = touchstation::ps3::runPpuSelfTest();
    const auto spu = touchstation::ps3::runSpuSelfTest();
    const auto rsx = touchstation::ps3::runRsxSelfTest();
    const bool elf = testPpc64ElfLoader();
    const auto pipeline = touchstation::ps3::GuestPipeline().runDemo(320u, 180u);
    std::cout << "PPU: " << ppu.message << "\n";
    std::cout << "ELF64 loader: " << (elf ? "PASS: big-endian PPC64 PT_LOAD segment mapped and executed." : "FAIL: ELF loader test failed.") << "\n";
    std::cout << "SPU: " << spu.message << "\n";
    std::cout << "RSX: " << rsx.message << "\n";
    std::cout << "Guest pipeline: " << pipeline.message << "\n";
    return (ppu.passed && elf && spu.passed && rsx.passed && pipeline.passed) ? 0 : 1;
}
