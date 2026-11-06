#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace touchstation::ps3 {

struct SpuVector {
    // SPU preferred scalar slot is word[3]; vector lanes are stored in architectural order.
    std::array<std::uint32_t, 4> word{};
};

class SpuInterpreter {
public:
    static constexpr std::size_t kRegisterCount = 128;
    static constexpr std::size_t kLocalStoreSize = 256u * 1024u;

    struct State {
        std::array<SpuVector, kRegisterCount> gpr{};
        std::uint32_t pc = 0;
        std::uint64_t instructions_executed = 0;
        std::uint32_t stop_code = 0;
        bool stopped = false;
        std::string fault;
    };

    SpuInterpreter();

    const State& state() const noexcept { return state_; }
    State& state() noexcept { return state_; }
    const std::vector<std::uint8_t>& localStore() const noexcept { return local_store_; }

    bool loadWords(const std::vector<std::uint32_t>& words, std::uint32_t address = 0);
    bool loadBytes(const std::vector<std::uint8_t>& bytes, std::uint32_t address = 0);
    bool step();
    std::uint64_t run(std::uint64_t instruction_budget);

    static std::uint32_t encodeStop(std::uint16_t code);
    static std::uint32_t encodeIl(unsigned rt, std::int16_t immediate);
    static std::uint32_t encodeAi(unsigned rt, unsigned ra, std::int16_t immediate);
    static std::uint32_t encodeA(unsigned rt, unsigned ra, unsigned rb);
    static std::uint32_t encodeStqd(unsigned rt, unsigned ra, std::int16_t byte_offset);
    static std::uint32_t encodeLqd(unsigned rt, unsigned ra, std::int16_t byte_offset);

private:
    std::uint32_t instruction_pc_ = 0;
    bool fail(const char* reason);
    bool readWord(std::uint32_t address, std::uint32_t& value) const;
    bool readQuad(std::uint32_t address, SpuVector& value) const;
    bool writeQuad(std::uint32_t address, const SpuVector& value);
    bool execute(std::uint32_t instruction);

    State state_{};
    std::vector<std::uint8_t> local_store_;
};

struct SpuSelfTestResult {
    bool passed = false;
    std::uint64_t instructions = 0;
    std::uint32_t result = 0;
    std::string message;
};

SpuSelfTestResult runSpuSelfTest();

} // namespace touchstation::ps3
