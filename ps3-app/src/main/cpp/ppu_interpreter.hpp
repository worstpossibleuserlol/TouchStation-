#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace touchstation::ps3 {

/**
 * Deliberately small PowerPC 64-bit PPU interpreter prototype.
 * This is an instruction-decoder foundation, NOT a complete PS3 CPU or system.
 * Guest memory is big-endian. Unsupported instructions stop execution safely.
 */
class PpuInterpreter {
public:
    static constexpr std::size_t kRegisterCount = 32;
    static constexpr std::size_t kDefaultMemorySize = 2u * 1024u * 1024u;

    struct State {
        std::array<std::uint64_t, kRegisterCount> gpr{};
        std::uint64_t pc = 0;
        std::uint64_t lr = 0;
        std::uint64_t ctr = 0;
        std::uint32_t cr = 0;
        std::uint64_t instructions_executed = 0;
        bool halted = false;
        std::string fault;
    };

    explicit PpuInterpreter(std::size_t memory_size = kDefaultMemorySize);
    State& state() noexcept { return state_; }
    const State& state() const noexcept { return state_; }
    std::size_t memorySize() const noexcept { return memory_.size(); }

    bool write8(std::uint64_t address, std::uint8_t value);
    bool write32(std::uint64_t address, std::uint32_t value);
    bool write64(std::uint64_t address, std::uint64_t value);
    bool read8(std::uint64_t address, std::uint8_t& value) const;
    bool read32(std::uint64_t address, std::uint32_t& value) const;
    bool read64(std::uint64_t address, std::uint64_t& value) const;

    bool loadWords(const std::vector<std::uint32_t>& words, std::uint64_t address = 0);
    bool step();
    std::uint64_t run(std::uint64_t instruction_budget);

    static std::uint32_t encodeAddi(unsigned rt, unsigned ra, std::int16_t imm);
    static std::uint32_t encodeAdd(unsigned rt, unsigned ra, unsigned rb);
    static std::uint32_t encodeStw(unsigned rs, unsigned ra, std::int16_t disp);
    static std::uint32_t encodeLwz(unsigned rt, unsigned ra, std::int16_t disp);
    static std::uint32_t encodeOri(unsigned ra, unsigned rs, std::uint16_t imm);

private:
    bool fail(const char* reason);
    bool effectiveAddress(unsigned ra, std::int16_t displacement, std::uint64_t& result) const;
    bool execute(std::uint32_t instruction);

    State state_{};
    std::vector<std::uint8_t> memory_;
};

struct SelfTestResult {
    bool passed = false;
    std::string message;
    std::uint64_t instructions = 0;
    std::uint64_t result = 0;
};

SelfTestResult runPpuSelfTest();

} // namespace touchstation::ps3
