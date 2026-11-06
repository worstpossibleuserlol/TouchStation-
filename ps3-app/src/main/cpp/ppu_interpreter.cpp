#include "ppu_interpreter.hpp"

#include <algorithm>
#include <limits>
#include <cstring>

namespace touchstation::ps3 {

namespace {
bool readBe16(const std::vector<std::uint8_t>& bytes, std::size_t offset, std::uint16_t& value) {
    if (offset > bytes.size() || bytes.size() - offset < 2u) return false;
    value = static_cast<std::uint16_t>((static_cast<std::uint16_t>(bytes[offset]) << 8u) |
                                      static_cast<std::uint16_t>(bytes[offset + 1u]));
    return true;
}
bool readBe32(const std::vector<std::uint8_t>& bytes, std::size_t offset, std::uint32_t& value) {
    if (offset > bytes.size() || bytes.size() - offset < 4u) return false;
    value = (static_cast<std::uint32_t>(bytes[offset]) << 24u) |
            (static_cast<std::uint32_t>(bytes[offset + 1u]) << 16u) |
            (static_cast<std::uint32_t>(bytes[offset + 2u]) << 8u) |
            static_cast<std::uint32_t>(bytes[offset + 3u]);
    return true;
}
bool readBe64(const std::vector<std::uint8_t>& bytes, std::size_t offset, std::uint64_t& value) {
    if (offset > bytes.size() || bytes.size() - offset < 8u) return false;
    value = 0;
    for (std::size_t i = 0; i < 8u; ++i) value = (value << 8u) | bytes[offset + i];
    return true;
}
}


PpuInterpreter::PpuInterpreter(std::size_t memory_size)
    : memory_(std::max<std::size_t>(memory_size, 4u), 0u) {}

bool PpuInterpreter::fail(const char* reason) {
    state_.halted = true;
    state_.fault = reason ? reason : "unknown PPU fault";
    return false;
}

bool PpuInterpreter::write8(std::uint64_t address, std::uint8_t value) {
    if (address >= memory_.size()) return fail("guest write out of bounds");
    memory_[static_cast<std::size_t>(address)] = value;
    return true;
}

bool PpuInterpreter::write32(std::uint64_t address, std::uint32_t value) {
    if (address > memory_.size() || memory_.size() - static_cast<std::size_t>(address) < 4u)
        return fail("guest 32-bit write out of bounds");
    const auto a = static_cast<std::size_t>(address);
    memory_[a] = static_cast<std::uint8_t>(value >> 24u);
    memory_[a + 1u] = static_cast<std::uint8_t>(value >> 16u);
    memory_[a + 2u] = static_cast<std::uint8_t>(value >> 8u);
    memory_[a + 3u] = static_cast<std::uint8_t>(value);
    return true;
}

bool PpuInterpreter::write64(std::uint64_t address, std::uint64_t value) {
    if (address > memory_.size() || memory_.size() - static_cast<std::size_t>(address) < 8u)
        return fail("guest 64-bit write out of bounds");
    const auto a = static_cast<std::size_t>(address);
    for (unsigned i = 0; i < 8u; ++i)
        memory_[a + i] = static_cast<std::uint8_t>(value >> (56u - i * 8u));
    return true;
}

bool PpuInterpreter::read8(std::uint64_t address, std::uint8_t& value) const {
    if (address >= memory_.size()) return false;
    value = memory_[static_cast<std::size_t>(address)];
    return true;
}

bool PpuInterpreter::read32(std::uint64_t address, std::uint32_t& value) const {
    if (address > memory_.size() || memory_.size() - static_cast<std::size_t>(address) < 4u)
        return false;
    const auto a = static_cast<std::size_t>(address);
    value = (static_cast<std::uint32_t>(memory_[a]) << 24u) |
            (static_cast<std::uint32_t>(memory_[a + 1u]) << 16u) |
            (static_cast<std::uint32_t>(memory_[a + 2u]) << 8u) |
            static_cast<std::uint32_t>(memory_[a + 3u]);
    return true;
}

bool PpuInterpreter::read64(std::uint64_t address, std::uint64_t& value) const {
    if (address > memory_.size() || memory_.size() - static_cast<std::size_t>(address) < 8u)
        return false;
    const auto a = static_cast<std::size_t>(address);
    value = 0;
    for (unsigned i = 0; i < 8u; ++i)
        value = (value << 8u) | memory_[a + i];
    return true;
}

bool PpuInterpreter::loadWords(const std::vector<std::uint32_t>& words, std::uint64_t address) {
    if ((address & 3u) != 0u) return fail("unaligned program load");
    if (words.size() > (std::numeric_limits<std::size_t>::max() / 4u))
        return fail("program size overflow");
    const std::size_t bytes = words.size() * 4u;
    if (address > memory_.size() || bytes > memory_.size() - static_cast<std::size_t>(address))
        return fail("program does not fit guest memory");
    for (std::size_t i = 0; i < words.size(); ++i) {
        if (!write32(address + i * 4u, words[i])) return false;
    }
    state_.gpr.fill(0u);
    state_.pc = address;
    state_.lr = 0u;
    state_.ctr = 0u;
    state_.cr = 0u;
    state_.instructions_executed = 0u;
    state_.halted = false;
    state_.fault.clear();
    return true;
}


bool PpuInterpreter::loadPpc64Elf(const std::vector<std::uint8_t>& file) {
    constexpr std::uint64_t kMaxGuestAddress = 128ull * 1024ull * 1024ull;
    constexpr std::uint32_t kPtLoad = 1u;
    if (file.size() < 64u) return fail("ELF file is shorter than an ELF64 header");
    if (file[0] != 0x7fu || file[1] != 'E' || file[2] != 'L' || file[3] != 'F')
        return fail("file is not an ELF image (encrypted SELF and disc images are not supported)");
    if (file[4] != 2u || file[5] != 2u)
        return fail("only big-endian ELF64 executables are supported by the PPU boot prototype");

    std::uint16_t file_type = 0, machine = 0, header_size = 0, program_header_size = 0, program_header_count = 0;
    std::uint64_t entry = 0, program_header_offset = 0;
    if (!readBe16(file, 16u, file_type) || !readBe16(file, 18u, machine) || !readBe64(file, 24u, entry) ||
        !readBe64(file, 32u, program_header_offset) || !readBe16(file, 52u, header_size) ||
        !readBe16(file, 54u, program_header_size) || !readBe16(file, 56u, program_header_count))
        return fail("ELF header is truncated");
    if (file_type != 2u) return fail("only fixed-address ELF64 executables are supported (ET_EXEC)");
    if (machine != 21u) return fail("ELF machine type is not PowerPC64");
    if (header_size < 64u || program_header_size < 56u || program_header_count == 0u ||
        program_header_count > 1024u)
        return fail("ELF program-header layout is invalid or unsupported");
    const std::uint64_t table_bytes = static_cast<std::uint64_t>(program_header_size) * program_header_count;
    if (program_header_offset > file.size() || table_bytes > file.size() - program_header_offset)
        return fail("ELF program-header table extends beyond the imported file");

    struct Segment {
        std::uint64_t file_offset;
        std::uint64_t virtual_address;
        std::uint64_t file_size;
        std::uint64_t memory_size;
        std::uint32_t flags;
    };
    std::vector<Segment> segments;
    std::uint64_t required_memory = 4u;
    bool entry_is_executable = false;

    for (std::uint16_t i = 0; i < program_header_count; ++i) {
        const std::uint64_t offset64 = program_header_offset +
            static_cast<std::uint64_t>(i) * program_header_size;
        if (offset64 > std::numeric_limits<std::size_t>::max())
            return fail("ELF program-header offset exceeds host address range");
        const auto offset = static_cast<std::size_t>(offset64);
        std::uint32_t type = 0, flags = 0;
        std::uint64_t file_offset = 0, virtual_address = 0, file_size = 0, memory_size = 0;
        if (!readBe32(file, offset, type) || !readBe32(file, offset + 4u, flags) ||
            !readBe64(file, offset + 8u, file_offset) ||
            !readBe64(file, offset + 16u, virtual_address) ||
            !readBe64(file, offset + 32u, file_size) ||
            !readBe64(file, offset + 40u, memory_size))
            return fail("ELF load segment header is truncated");
        if (type != kPtLoad) continue;
        if (file_size > memory_size) return fail("ELF load segment file size exceeds memory size");
        if (file_offset > file.size() || file_size > file.size() - file_offset)
            return fail("ELF load segment points outside the imported file");
        if (virtual_address > kMaxGuestAddress || memory_size > kMaxGuestAddress - virtual_address)
            return fail("ELF load segment exceeds the prototype's 128 MiB guest address window");
        if (memory_size == 0u) continue;

        segments.push_back({file_offset, virtual_address, file_size, memory_size, flags});
        required_memory = std::max(required_memory, virtual_address + memory_size);
        if ((flags & 1u) != 0u && entry >= virtual_address &&
            entry < virtual_address + memory_size) entry_is_executable = true;
    }

    if (segments.empty()) return fail("ELF contains no PT_LOAD segments");
    if (!entry_is_executable || (entry & 3u) != 0u)
        return fail("ELF entry point is not aligned inside an executable load segment");

    memory_.assign(static_cast<std::size_t>(required_memory), 0u);
    for (const auto& segment : segments) {
        const auto destination = static_cast<std::size_t>(segment.virtual_address);
        const auto source = static_cast<std::size_t>(segment.file_offset);
        const auto length = static_cast<std::size_t>(segment.file_size);
        if (length != 0u) std::memcpy(memory_.data() + destination, file.data() + source, length);
        // memory_size - file_size is BSS and remains zero from assign().
    }

    state_ = {};
    state_.pc = entry;
    return true;
}

bool PpuInterpreter::effectiveAddress(unsigned ra, std::int16_t displacement,
                                      std::uint64_t& result) const {
    if (ra >= kRegisterCount) return false;
    const std::uint64_t base = ra == 0u ? 0u : state_.gpr[ra];
    result = base + static_cast<std::int64_t>(displacement);
    return true;
}

bool PpuInterpreter::execute(std::uint32_t instruction) {
    const unsigned opcode = instruction >> 26u;
    const unsigned rt = (instruction >> 21u) & 31u;
    const unsigned ra = (instruction >> 16u) & 31u;
    const unsigned rb = (instruction >> 11u) & 31u;
    const auto simm = static_cast<std::int16_t>(instruction & 0xffffu);
    const auto uimm = static_cast<std::uint16_t>(instruction & 0xffffu);
    std::uint64_t address = 0;

    switch (opcode) {
        case 14u: // addi
            state_.gpr[rt] = (ra == 0u ? 0u : state_.gpr[ra]) + static_cast<std::int64_t>(simm);
            return true;
        case 15u: // addis
            state_.gpr[rt] = (ra == 0u ? 0u : state_.gpr[ra]) +
                              (static_cast<std::int64_t>(simm) * 65536ll);
            return true;
        case 24u: // ori
            state_.gpr[ra] = state_.gpr[rt] | uimm;
            return true;
        case 25u: // oris
            state_.gpr[ra] = state_.gpr[rt] | (static_cast<std::uint64_t>(uimm) << 16u);
            return true;
        case 26u: // xori
            state_.gpr[ra] = state_.gpr[rt] ^ uimm;
            return true;
        case 28u: { // andi. (record form updates CR0)
            const std::uint64_t value = state_.gpr[rt] & uimm;
            state_.gpr[ra] = value;
            const std::uint32_t relation = value == 0u ? 0x2u : 0x4u;
            state_.cr = (state_.cr & 0x0fffffffu) | (relation << 28u);
            return true;
        }
        case 11u: { // cmpwi: signed 32-bit compare into selected CR field
            const unsigned bf = (instruction >> 23u) & 7u;
            const auto lhs = static_cast<std::int32_t>(state_.gpr[ra]);
            const auto rhs = static_cast<std::int32_t>(simm);
            const std::uint32_t relation = lhs < rhs ? 0x8u : (lhs > rhs ? 0x4u : 0x2u);
            const unsigned shift = (7u - bf) * 4u;
            const std::uint32_t mask = 0x0fu << shift;
            state_.cr = (state_.cr & ~mask) | (relation << shift);
            return true;
        }
        case 32u: { // lwz
            if (!effectiveAddress(ra, simm, address)) return fail("invalid lwz register");
            if ((address & 3u) != 0u) return fail("unaligned lwz guest read");
            std::uint32_t value = 0;
            if (!read32(address, value)) return fail("lwz guest read out of bounds");
            state_.gpr[rt] = value;
            return true;
        }
        case 36u: // stw
            if (!effectiveAddress(ra, simm, address)) return fail("invalid stw register");
            if ((address & 3u) != 0u) return fail("unaligned stw guest write");
            return write32(address, static_cast<std::uint32_t>(state_.gpr[rt]));
        case 18u: { // b (relative/absolute, optional link)
            std::int64_t displacement = static_cast<std::int64_t>(instruction & 0x03fffffcu);
            if ((displacement & (1ll << 25u)) != 0) displacement |= ~0x03ffffffll;
            const bool absolute = (instruction & 2u) != 0u;
            const bool link = (instruction & 1u) != 0u;
            if (link) state_.lr = state_.pc + 4u;
            const std::uint64_t branch_pc = state_.pc - 4u;
            state_.pc = absolute ? static_cast<std::uint64_t>(displacement)
                                 : branch_pc + static_cast<std::uint64_t>(displacement);
            return true;
        }
        case 31u: { // selected X-form integer operations
            const unsigned xo = (instruction >> 1u) & 0x3ffu;
            switch (xo) {
                case 266u: state_.gpr[rt] = state_.gpr[ra] + state_.gpr[rb]; return true; // add
                case 40u:  state_.gpr[rt] = state_.gpr[rb] - state_.gpr[ra]; return true; // subf
                case 444u: state_.gpr[ra] = state_.gpr[rt] | state_.gpr[rb]; return true; // or
                case 316u: state_.gpr[ra] = state_.gpr[rt] ^ state_.gpr[rb]; return true; // xor
                case 28u:  state_.gpr[ra] = state_.gpr[rt] & state_.gpr[rb]; return true; // and
                default: return fail("unsupported PPU X-form instruction");
            }
        }
        default:
            return fail("unsupported PPU instruction");
    }
}

bool PpuInterpreter::step() {
    if (state_.halted) return false;
    if ((state_.pc & 3u) != 0u) return fail("unaligned PPU instruction address");
    std::uint32_t instruction = 0;
    if (!read32(state_.pc, instruction)) return fail("PPU instruction fetch out of bounds");
    state_.pc += 4u;
    if (!execute(instruction)) return false;
    ++state_.instructions_executed;
    return true;
}

std::uint64_t PpuInterpreter::run(std::uint64_t instruction_budget) {
    std::uint64_t executed = 0;
    while (executed < instruction_budget && step()) ++executed;
    return executed;
}

std::uint32_t PpuInterpreter::encodeAddi(unsigned rt, unsigned ra, std::int16_t imm) {
    return (14u << 26u) | ((rt & 31u) << 21u) | ((ra & 31u) << 16u) |
           static_cast<std::uint16_t>(imm);
}
std::uint32_t PpuInterpreter::encodeAdd(unsigned rt, unsigned ra, unsigned rb) {
    return (31u << 26u) | ((rt & 31u) << 21u) | ((ra & 31u) << 16u) |
           ((rb & 31u) << 11u) | (266u << 1u);
}
std::uint32_t PpuInterpreter::encodeStw(unsigned rs, unsigned ra, std::int16_t disp) {
    return (36u << 26u) | ((rs & 31u) << 21u) | ((ra & 31u) << 16u) |
           static_cast<std::uint16_t>(disp);
}
std::uint32_t PpuInterpreter::encodeLwz(unsigned rt, unsigned ra, std::int16_t disp) {
    return (32u << 26u) | ((rt & 31u) << 21u) | ((ra & 31u) << 16u) |
           static_cast<std::uint16_t>(disp);
}
std::uint32_t PpuInterpreter::encodeOri(unsigned ra, unsigned rs, std::uint16_t imm) {
    return (24u << 26u) | ((rs & 31u) << 21u) | ((ra & 31u) << 16u) | imm;
}

SelfTestResult runPpuSelfTest() {
    SelfTestResult result;
    PpuInterpreter cpu;
    const std::vector<std::uint32_t> program = {
        PpuInterpreter::encodeAddi(3u, 0u, 7),
        PpuInterpreter::encodeAddi(4u, 0u, 5),
        PpuInterpreter::encodeAdd(5u, 3u, 4u),
        PpuInterpreter::encodeStw(5u, 0u, 0x100),
        PpuInterpreter::encodeLwz(6u, 0u, 0x100),
        PpuInterpreter::encodeOri(7u, 6u, 0x1000u),
    };
    if (!cpu.loadWords(program)) {
        result.message = "FAIL: could not load PPU test program: " + cpu.state().fault;
        return result;
    }
    result.instructions = cpu.run(program.size());
    std::uint32_t memory_value = 0;
    const bool memory_ok = cpu.read32(0x100u, memory_value);
    result.result = cpu.state().gpr[7];
    result.passed = !cpu.state().halted && result.instructions == program.size() &&
                    cpu.state().gpr[5] == 12u && cpu.state().gpr[6] == 12u &&
                    memory_ok && memory_value == 12u && result.result == 0x100cu;
    if (!result.passed) {
        result.message = "FAIL: PPU prototype self-test mismatch. " + cpu.state().fault;
        return result;
    }

    PpuInterpreter branch_cpu;
    const std::uint32_t branch_plus_8 = (18u << 26u) | 8u;
    const std::vector<std::uint32_t> branch_program = {
        PpuInterpreter::encodeAddi(3u, 0u, 1),
        branch_plus_8,
        PpuInterpreter::encodeAddi(3u, 0u, 99),
        PpuInterpreter::encodeAddi(3u, 3u, 2),
    };
    if (!branch_cpu.loadWords(branch_program) || branch_cpu.run(3u) != 3u ||
        branch_cpu.state().gpr[3] != 3u || branch_cpu.state().halted) {
        result.passed = false;
        result.message = "FAIL: PPU relative branch test failed. " + branch_cpu.state().fault;
        return result;
    }

    PpuInterpreter bounds_cpu(16u);
    if (bounds_cpu.write32(14u, 0x12345678u) || !bounds_cpu.state().halted ||
        bounds_cpu.state().fault.empty()) {
        result.passed = false;
        result.message = "FAIL: guest memory bounds check did not trap the invalid store.";
        return result;
    }

    result.message = "PASS: PPU subset, big-endian memory, relative branch and memory bounds checks passed.";
    return result;
}

} // namespace touchstation::ps3
