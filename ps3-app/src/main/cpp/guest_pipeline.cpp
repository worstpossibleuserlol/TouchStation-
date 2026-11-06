#include "guest_pipeline.hpp"

#include "ppu_interpreter.hpp"
#include "rsx_command_processor.hpp"
#include "spu_interpreter.hpp"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace touchstation::ps3 {
namespace {

constexpr std::uint32_t kMethodSetScissorHorizontal = 0x000008c0u;
constexpr std::uint32_t kMethodSetScissorVertical = 0x000008c4u;
constexpr std::uint32_t kMethodSetColorClearValue = 0x00001d90u;
constexpr std::uint32_t kMethodClearSurface = 0x00001d94u;

bool submitClear(RsxCommandProcessor& rsx, std::uint32_t color,
                 std::uint32_t width, std::uint32_t height) {
    const std::vector<std::uint32_t> commands = {
        RsxCommandProcessor::encodeIncrementPacket(kMethodSetColorClearValue, 1u),
        color,
        RsxCommandProcessor::encodeIncrementPacket(kMethodSetScissorHorizontal, 2u),
        (width << 16u),
        (height << 16u),
        RsxCommandProcessor::encodeNonIncrementPacket(kMethodClearSurface, 1u),
        0x000000f0u
    };
    return rsx.process(commands);
}

bool executeSpuAudio(SpuInterpreter& spu, std::uint64_t& instruction_count) {
    std::vector<std::uint32_t> program;
    // 250 square-wave cycles at 250 Hz, emitted by SPU WRCH
    // writes into the documented TouchStation audio sample channel (0x28).
    // Each WRCH forwards four signed 16-bit lanes to the PCM sink.
    constexpr std::uint32_t kCycles = 250u;
    constexpr std::uint32_t kBlocksPerHalfCycle = 24u;
    constexpr std::int16_t kAmplitude = 8192;

    program.reserve(static_cast<std::size_t>(kCycles) *
                    kBlocksPerHalfCycle * 4u + 1u);
    for (std::uint32_t cycle = 0; cycle < kCycles; ++cycle) {
        for (std::uint32_t block = 0; block < kBlocksPerHalfCycle; ++block) {
            program.push_back(SpuInterpreter::encodeIl(3u, kAmplitude));
            program.push_back(SpuInterpreter::encodeWrch(0x28u, 3u));
        }
        for (std::uint32_t block = 0; block < kBlocksPerHalfCycle; ++block) {
            program.push_back(SpuInterpreter::encodeIl(3u, static_cast<std::int16_t>(-kAmplitude)));
            program.push_back(SpuInterpreter::encodeWrch(0x28u, 3u));
        }
    }
    program.push_back(SpuInterpreter::encodeStop(0x123u));

    if (program.size() * sizeof(std::uint32_t) > SpuInterpreter::kLocalStoreSize)
        return false;
    if (!spu.loadWords(program)) return false;

    instruction_count = spu.run(static_cast<std::uint64_t>(program.size()) + 1u);
    return spu.state().stopped && spu.state().fault.empty() &&
           spu.state().stop_code == 0x123u &&
           !spu.state().audio_pcm.empty();
}

} // namespace

GuestPipelineResult GuestPipeline::runDemo(std::uint32_t width,
                                            std::uint32_t height) const {
    GuestPipelineResult result;
    if (width == 0u || height == 0u || width > 2048u || height > 2048u) {
        result.message = "Frame dimensions must be between 1 and 2048 pixels.";
        return result;
    }

    PpuInterpreter ppu;
    SpuInterpreter spu;
    RsxCommandProcessor rsx;

    // PPU program: request an RSX clear, request SPU audio, then halt.
    // The color is encoded using PPU addis + ori instructions, not assigned by the host.
    const std::vector<std::uint32_t> guest_program = {
        PpuInterpreter::encodeAddi(0u, 0u, 1),       // host call 1: RSX clear
        PpuInterpreter::encodeAddis(3u, 0u, -224),   // r3 high half = 0xff200000
        PpuInterpreter::encodeOri(3u, 3u, 0x60c0u), // r3 = 0xff2060c0 (ARGB)
        PpuInterpreter::encodeSc(),
        PpuInterpreter::encodeAddi(0u, 0u, 2),       // host call 2: execute SPU PCM guest
        PpuInterpreter::encodeSc(),
        PpuInterpreter::encodeAddi(0u, 0u, 0),       // host call 0: halt this guest
        PpuInterpreter::encodeSc()
    };

    if (!ppu.loadWords(guest_program)) {
        result.message = "PPU guest program could not be loaded: " + ppu.state().fault;
        return result;
    }

    bool rsx_submitted = false;
    bool spu_executed = false;
    constexpr std::uint64_t kPpuInstructionBudget = 64u;

    for (std::uint64_t i = 0; i < kPpuInstructionBudget && !ppu.state().halted; ++i) {
        if (!ppu.step()) {
            result.message = "PPU guest halted: " + ppu.state().fault;
            return result;
        }

        if (!ppu.state().host_call_pending) continue;
        const std::uint64_t service = ppu.state().host_call_number;

        if (service == 1u) {
            const auto color = static_cast<std::uint32_t>(ppu.state().gpr[3]);
            if (!submitClear(rsx, color, width, height)) {
                result.message = "PPU-to-RSX bridge rejected the guest command: " + rsx.fault();
                return result;
            }
            rsx_submitted = true;
        } else if (service == 2u) {
            if (!executeSpuAudio(spu, result.spu_instructions)) {
                result.message = "PPU-to-SPU bridge failed: " + spu.state().fault;
                return result;
            }
            result.pcm = spu.state().audio_pcm;
            spu_executed = true;
        } else if (service == 0u) {
            ppu.state().halted = true;
        } else {
            result.message = "Unsupported TouchStation demo host-call number.";
            return result;
        }
        ppu.state().host_call_pending = false;
    }

    result.ppu_instructions = ppu.state().instructions_executed;
    result.rsx_method_writes = rsx.stats().method_writes;
    if (!ppu.state().halted || !rsx_submitted || !spu_executed) {
        result.message = "Guest did not complete all PPU/SPU/RSX stages within the instruction budget.";
        return result;
    }

    result.rgba = rsx.renderClearRgba(width, height);
    const std::size_t expected_bytes = static_cast<std::size_t>(width) *
                                       static_cast<std::size_t>(height) * 4u;
    const bool pixel_matches = result.rgba.size() >= 4u &&
        result.rgba[0] == 0x20u && result.rgba[1] == 0x60u &&
        result.rgba[2] == 0xc0u && result.rgba[3] == 0xffu;
    result.passed = result.rgba.size() == expected_bytes && pixel_matches &&
                    result.pcm.size() == 48000u && result.rsx_method_writes == 4u;
    result.message = result.passed
        ? "PASS: PPU guest requested RSX frame output; SPU WRCH samples reached the PCM queue."
        : "FAIL: integrated PPU/SPU/RSX output validation failed.";
    return result;
}

} // namespace touchstation::ps3
