#include "spu_interpreter.hpp"

#include <algorithm>
#include <limits>

namespace touchstation::ps3 {
namespace {

constexpr std::uint32_t kLocalStoreMask = 0x3fffc;
constexpr std::uint32_t kQuadwordMask = 0x3fff0;

std::int32_t signExtend10(std::uint32_t value) {
    value &= 0x3ffu;
    return (value & 0x200u) != 0u
        ? static_cast<std::int32_t>(value) - 0x400
        : static_cast<std::int32_t>(value);
}

std::int32_t signExtend16(std::uint32_t value) {
    value &= 0xffffu;
    return (value & 0x8000u) != 0u
        ? static_cast<std::int32_t>(value) - 0x10000
        : static_cast<std::int32_t>(value);
}

std::uint32_t branchTarget(std::uint32_t pc, std::int32_t immediate_words) {
    const std::int64_t byte_offset = static_cast<std::int64_t>(immediate_words) * 4;
    return static_cast<std::uint32_t>(static_cast<std::int64_t>(pc) + byte_offset) & kLocalStoreMask;
}

} // namespace

SpuInterpreter::SpuInterpreter()
    : local_store_(kLocalStoreSize, 0u) {}

bool SpuInterpreter::fail(const char* reason) {
    state_.fault = reason ? reason : "unknown SPU fault";
    state_.stopped = true;
    return false;
}

bool SpuInterpreter::readWord(std::uint32_t address, std::uint32_t& value) const {
    if ((address & 3u) != 0u || address > kLocalStoreSize - 4u) return false;
    const auto a = static_cast<std::size_t>(address);
    value = (static_cast<std::uint32_t>(local_store_[a]) << 24u) |
            (static_cast<std::uint32_t>(local_store_[a + 1u]) << 16u) |
            (static_cast<std::uint32_t>(local_store_[a + 2u]) << 8u) |
            static_cast<std::uint32_t>(local_store_[a + 3u]);
    return true;
}

bool SpuInterpreter::readQuad(std::uint32_t address, SpuVector& value) const {
    if ((address & 0xfu) != 0u || address > kLocalStoreSize - 16u) return false;
    for (std::size_t i = 0; i < 4u; ++i) {
        if (!readWord(address + static_cast<std::uint32_t>(i * 4u), value.word[i])) return false;
    }
    return true;
}

bool SpuInterpreter::writeQuad(std::uint32_t address, const SpuVector& value) {
    if ((address & 0xfu) != 0u || address > kLocalStoreSize - 16u)
        return fail("SPU quadword store out of local-store bounds");
    const auto base = static_cast<std::size_t>(address);
    for (std::size_t i = 0; i < 4u; ++i) {
        const std::uint32_t word = value.word[i];
        const auto a = base + i * 4u;
        local_store_[a] = static_cast<std::uint8_t>(word >> 24u);
        local_store_[a + 1u] = static_cast<std::uint8_t>(word >> 16u);
        local_store_[a + 2u] = static_cast<std::uint8_t>(word >> 8u);
        local_store_[a + 3u] = static_cast<std::uint8_t>(word);
    }
    return true;
}

bool SpuInterpreter::loadWords(const std::vector<std::uint32_t>& words, std::uint32_t address) {
    if ((address & 3u) != 0u) return fail("SPU program address is not word aligned");
    if (words.size() > (kLocalStoreSize / 4u) ||
        static_cast<std::size_t>(address) > kLocalStoreSize ||
        words.size() > (kLocalStoreSize - static_cast<std::size_t>(address)) / 4u)
        return fail("SPU program does not fit local store");

    std::fill(local_store_.begin(), local_store_.end(), 0u);
    for (std::size_t i = 0; i < words.size(); ++i) {
        const std::uint32_t word = words[i];
        const auto a = static_cast<std::size_t>(address) + i * 4u;
        local_store_[a] = static_cast<std::uint8_t>(word >> 24u);
        local_store_[a + 1u] = static_cast<std::uint8_t>(word >> 16u);
        local_store_[a + 2u] = static_cast<std::uint8_t>(word >> 8u);
        local_store_[a + 3u] = static_cast<std::uint8_t>(word);
    }
    state_ = {};
    state_.pc = address & kLocalStoreMask;
    return true;
}

bool SpuInterpreter::loadBytes(const std::vector<std::uint8_t>& bytes, std::uint32_t address) {
    if ((address & 3u) != 0u) return fail("SPU byte program address is not word aligned");
    if (static_cast<std::size_t>(address) > kLocalStoreSize ||
        bytes.size() > kLocalStoreSize - static_cast<std::size_t>(address))
        return fail("SPU byte program does not fit local store");

    std::fill(local_store_.begin(), local_store_.end(), 0u);
    std::copy(bytes.begin(), bytes.end(), local_store_.begin() + static_cast<std::ptrdiff_t>(address));
    state_ = {};
    state_.pc = address & kLocalStoreMask;
    return true;
}

bool SpuInterpreter::execute(std::uint32_t instruction) {
    const std::uint32_t op = instruction >> 21u;
    const unsigned rt = instruction & 0x7fu;
    const unsigned ra = (instruction >> 7u) & 0x7fu;
    const unsigned rb = (instruction >> 14u) & 0x7fu;
    const std::int32_t si10 = signExtend10(instruction >> 14u);
    const std::int32_t si16 = signExtend16(instruction >> 7u);
    const std::uint16_t i16 = static_cast<std::uint16_t>((instruction >> 7u) & 0xffffu);

    switch (op) {
        case 0x000u: // STOP
            state_.stop_code = instruction & 0x3fffu;
            state_.stopped = true;
            return true;
        case 0x001u: // LNOP
        case 0x002u: // SYNC
        case 0x003u: // DSYNC
        case 0x201u: // NOP
            return true;
        case 0x10du: { // WRCH: expose the guest audio mailbox channel to the PCM sink.
            constexpr unsigned kAudioSampleChannel = 0x28u;
            constexpr std::size_t kMaxAudioSamples = 192000u;
            if (ra != kAudioSampleChannel) return fail("unsupported SPU channel write");
            if (state_.audio_pcm.size() > kMaxAudioSamples - 4u)
                return fail("SPU audio mailbox exceeded its bounded PCM queue");
            for (std::size_t lane = 0; lane < 4u; ++lane) {
                state_.audio_pcm.push_back(static_cast<std::int16_t>(
                    state_.gpr[rt].word[lane] & 0xffffu));
            }
            return true;
        }
        case 0x040u: // SF: rb - ra
            for (std::size_t i = 0; i < 4u; ++i)
                state_.gpr[rt].word[i] = state_.gpr[rb].word[i] - state_.gpr[ra].word[i];
            return true;
        case 0x041u: // OR
            for (std::size_t i = 0; i < 4u; ++i)
                state_.gpr[rt].word[i] = state_.gpr[ra].word[i] | state_.gpr[rb].word[i];
            return true;
        case 0x049u: // NOR
            for (std::size_t i = 0; i < 4u; ++i)
                state_.gpr[rt].word[i] = ~(state_.gpr[ra].word[i] | state_.gpr[rb].word[i]);
            return true;
        case 0x0c0u: // A
            for (std::size_t i = 0; i < 4u; ++i)
                state_.gpr[rt].word[i] = state_.gpr[ra].word[i] + state_.gpr[rb].word[i];
            return true;
        case 0x0c1u: // AND
            for (std::size_t i = 0; i < 4u; ++i)
                state_.gpr[rt].word[i] = state_.gpr[ra].word[i] & state_.gpr[rb].word[i];
            return true;
        case 0x241u: // XOR
            for (std::size_t i = 0; i < 4u; ++i)
                state_.gpr[rt].word[i] = state_.gpr[ra].word[i] ^ state_.gpr[rb].word[i];
            return true;
        case 0x249u: // EQV
            for (std::size_t i = 0; i < 4u; ++i)
                state_.gpr[rt].word[i] = ~(state_.gpr[ra].word[i] ^ state_.gpr[rb].word[i]);
            return true;
        case 0x144u: { // STQX
            const std::uint32_t address = (state_.gpr[ra].word[3] + state_.gpr[rb].word[3]) & kQuadwordMask;
            return writeQuad(address, state_.gpr[rt]);
        }
        case 0x1c4u: { // LQX
            const std::uint32_t address = (state_.gpr[ra].word[3] + state_.gpr[rb].word[3]) & kQuadwordMask;
            if (!readQuad(address, state_.gpr[rt])) return fail("SPU quadword load out of local-store bounds");
            return true;
        }
        default:
            break;
    }

    // Immediate-format instructions have shorter primary opcodes. These tests mirror
    // the SPU ISA's decoder prefix lengths (2, 3, or 4 high opcode bits).
    const std::uint32_t prefix9 = op >> 2u;
    const std::uint32_t prefix8 = op >> 3u;
    const std::uint32_t prefix7 = op >> 4u;
    (void)prefix7;

    if (prefix9 == 0x081u) { // IL
        const auto value = static_cast<std::uint32_t>(static_cast<std::int32_t>(si16));
        state_.gpr[rt].word.fill(value);
        return true;
    }
    if (prefix9 == 0x082u) { // ILHU
        const auto value = static_cast<std::uint32_t>(i16) << 16u;
        state_.gpr[rt].word.fill(value);
        return true;
    }
    if (prefix9 == 0x060u) { // BRA
        state_.pc = branchTarget(0u, si16);
        return true;
    }
    if (prefix9 == 0x064u) { // BR
        state_.pc = branchTarget(instruction_pc_, si16);
        return true;
    }
    if (prefix9 == 0x062u) { // BRASL
        state_.gpr[rt].word.fill((instruction_pc_ + 4u) & kLocalStoreMask);
        state_.pc = branchTarget(0u, si16);
        return true;
    }
    if (prefix9 == 0x066u) { // BRSL
        state_.gpr[rt].word.fill((instruction_pc_ + 4u) & kLocalStoreMask);
        state_.pc = branchTarget(instruction_pc_, si16);
        return true;
    }
    if (prefix9 == 0x040u) { // BRZ
        if (state_.gpr[rt].word[3] == 0u) state_.pc = branchTarget(instruction_pc_, si16);
        return true;
    }
    if (prefix9 == 0x042u) { // BRNZ
        if (state_.gpr[rt].word[3] != 0u) state_.pc = branchTarget(instruction_pc_, si16);
        return true;
    }
    if (prefix9 == 0x061u) { // LQA
        const std::uint32_t address = branchTarget(0u, si16) & kQuadwordMask;
        if (!readQuad(address, state_.gpr[rt])) return fail("SPU LQA address out of bounds");
        return true;
    }
    if (prefix9 == 0x041u) { // STQA
        const std::uint32_t address = branchTarget(0u, si16) & kQuadwordMask;
        return writeQuad(address, state_.gpr[rt]);
    }
    if (prefix9 == 0x067u) { // LQR
        const std::uint32_t address = branchTarget(instruction_pc_, si16) & kQuadwordMask;
        if (!readQuad(address, state_.gpr[rt])) return fail("SPU LQR address out of bounds");
        return true;
    }
    if (prefix9 == 0x047u) { // STQR
        const std::uint32_t address = branchTarget(instruction_pc_, si16) & kQuadwordMask;
        return writeQuad(address, state_.gpr[rt]);
    }
    if (prefix8 == 0x01cu) { // AI
        for (std::size_t i = 0; i < 4u; ++i)
            state_.gpr[rt].word[i] = state_.gpr[ra].word[i] + static_cast<std::uint32_t>(si10);
        return true;
    }
    if (prefix8 == 0x004u) { // ORI
        for (std::size_t i = 0; i < 4u; ++i)
            state_.gpr[rt].word[i] = state_.gpr[ra].word[i] | static_cast<std::uint32_t>(si10);
        return true;
    }
    if (prefix8 == 0x044u) { // XORI
        for (std::size_t i = 0; i < 4u; ++i)
            state_.gpr[rt].word[i] = state_.gpr[ra].word[i] ^ static_cast<std::uint32_t>(si10);
        return true;
    }
    if (prefix8 == 0x014u) { // ANDI
        for (std::size_t i = 0; i < 4u; ++i)
            state_.gpr[rt].word[i] = state_.gpr[ra].word[i] & static_cast<std::uint32_t>(si10);
        return true;
    }
    if (prefix8 == 0x024u) { // STQD
        const std::int64_t effective = static_cast<std::int64_t>(state_.gpr[ra].word[3]) +
                                       static_cast<std::int64_t>(si10) * 16;
        return writeQuad(static_cast<std::uint32_t>(effective) & kQuadwordMask, state_.gpr[rt]);
    }
    if (prefix8 == 0x034u) { // LQD
        const std::int64_t effective = static_cast<std::int64_t>(state_.gpr[ra].word[3]) +
                                       static_cast<std::int64_t>(si10) * 16;
        if (!readQuad(static_cast<std::uint32_t>(effective) & kQuadwordMask, state_.gpr[rt]))
            return fail("SPU LQD address out of bounds");
        return true;
    }

    return fail("unsupported SPU instruction");
}

bool SpuInterpreter::step() {
    if (state_.stopped || !state_.fault.empty()) return false;
    std::uint32_t instruction = 0;
    if (!readWord(state_.pc, instruction)) return fail("SPU instruction fetch out of local-store bounds");

    instruction_pc_ = state_.pc;
    state_.pc = (state_.pc + 4u) & kLocalStoreMask;
    if (!execute(instruction)) return false;
    ++state_.instructions_executed;
    return true;
}

std::uint64_t SpuInterpreter::run(std::uint64_t instruction_budget) {
    std::uint64_t executed = 0;
    while (executed < instruction_budget && !state_.stopped && state_.fault.empty()) {
        if (!step()) break;
        ++executed;
    }
    return executed;
}

std::uint32_t SpuInterpreter::encodeStop(std::uint16_t code) {
    return static_cast<std::uint32_t>(code) & 0x3fffu;
}
std::uint32_t SpuInterpreter::encodeWrch(unsigned channel, unsigned rt) {
    return (0x10du << 21u) | (rt & 0x7fu) | ((channel & 0x7fu) << 7u);
}
std::uint32_t SpuInterpreter::encodeIl(unsigned rt, std::int16_t immediate) {
    return (0x081u << 23u) | (rt & 0x7fu) |
           (static_cast<std::uint32_t>(static_cast<std::uint16_t>(immediate)) << 7u);
}
std::uint32_t SpuInterpreter::encodeAi(unsigned rt, unsigned ra, std::int16_t immediate) {
    return (0x0e0u << 21u) | (rt & 0x7fu) | ((ra & 0x7fu) << 7u) |
           ((static_cast<std::uint32_t>(immediate) & 0x3ffu) << 14u);
}
std::uint32_t SpuInterpreter::encodeA(unsigned rt, unsigned ra, unsigned rb) {
    return (0x0c0u << 21u) | (rt & 0x7fu) | ((ra & 0x7fu) << 7u) | ((rb & 0x7fu) << 14u);
}
std::uint32_t SpuInterpreter::encodeStqd(unsigned rt, unsigned ra, std::int16_t byte_offset) {
    const std::int32_t scaled = static_cast<std::int32_t>(byte_offset) / 16;
    return (0x120u << 21u) | (rt & 0x7fu) | ((ra & 0x7fu) << 7u) |
           ((static_cast<std::uint32_t>(scaled) & 0x3ffu) << 14u);
}
std::uint32_t SpuInterpreter::encodeLqd(unsigned rt, unsigned ra, std::int16_t byte_offset) {
    const std::int32_t scaled = static_cast<std::int32_t>(byte_offset) / 16;
    return (0x1a0u << 21u) | (rt & 0x7fu) | ((ra & 0x7fu) << 7u) |
           ((static_cast<std::uint32_t>(scaled) & 0x3ffu) << 14u);
}

SpuSelfTestResult runSpuSelfTest() {
    SpuSelfTestResult result;
    SpuInterpreter spu;
    const std::vector<std::uint32_t> program = {
        SpuInterpreter::encodeIl(3u, 5),
        SpuInterpreter::encodeAi(4u, 3u, 7),
        SpuInterpreter::encodeStqd(4u, 0u, 0x100),
        SpuInterpreter::encodeLqd(5u, 0u, 0x100),
        SpuInterpreter::encodeStop(0x123u)
    };
    if (!spu.loadWords(program)) {
        result.message = "FAIL: SPU program did not load: " + spu.state().fault;
        return result;
    }
    result.instructions = spu.run(32u);
    result.result = spu.state().gpr[5].word[3];
    result.passed = spu.state().stopped && spu.state().fault.empty() &&
                    spu.state().stop_code == 0x123u && result.instructions == program.size() &&
                    spu.state().gpr[4].word[3] == 12u && result.result == 12u;
    result.message = result.passed
        ? "PASS: SPU vector integer subset, big-endian local store and STOP passed."
        : "FAIL: SPU arithmetic, local-store or control test failed. " + spu.state().fault;
    if (!result.passed) return result;

    // Relative BR at PC 4 skips the IL at PC 8 and lands on AI at PC 12.
    SpuInterpreter branch;
    const std::uint32_t branch_plus_two_words =
        (0x064u << 23u) | (2u << 7u);
    const std::vector<std::uint32_t> branch_program = {
        SpuInterpreter::encodeIl(3u, 1),
        branch_plus_two_words,
        SpuInterpreter::encodeIl(4u, 99),
        SpuInterpreter::encodeAi(4u, 3u, 4),
        SpuInterpreter::encodeStop(0x55u)
    };
    if (!branch.loadWords(branch_program) || branch.run(8u) != 4u ||
        branch.state().gpr[4].word[3] != 5u || branch.state().stop_code != 0x55u ||
        !branch.state().stopped || !branch.state().fault.empty()) {
        result.passed = false;
        result.message = "FAIL: SPU relative branch or source-register test failed. " + branch.state().fault;
        return result;
    }

    SpuInterpreter bad_load;
    if (bad_load.loadWords({SpuInterpreter::encodeStop(0u)}, 0x40000u)) {
        result.passed = false;
        result.message = "FAIL: SPU local-store loader accepted a segment beyond 256 KiB.";
        return result;
    }

    SpuInterpreter audio_spu;
    const std::vector<std::uint32_t> audio_program = {
        SpuInterpreter::encodeIl(7u, 8192),
        SpuInterpreter::encodeWrch(0x28u, 7u),
        SpuInterpreter::encodeIl(7u, -8192),
        SpuInterpreter::encodeWrch(0x28u, 7u),
        SpuInterpreter::encodeStop(0u)
    };
    if (!audio_spu.loadWords(audio_program) || audio_spu.run(16u) != audio_program.size() ||
        audio_spu.state().audio_pcm.size() != 8u ||
        audio_spu.state().audio_pcm[0] != 8192 ||
        audio_spu.state().audio_pcm[4] != -8192) {
        result.passed = false;
        result.message = "FAIL: SPU WRCH audio mailbox sample forwarding failed. " + audio_spu.state().fault;
        return result;
    }

    result.message += " Relative branch, source-register, local-store boundary and audio-mailbox tests passed.";
    return result;
}

} // namespace touchstation::ps3
