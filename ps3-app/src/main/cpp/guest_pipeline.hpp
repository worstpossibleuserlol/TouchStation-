#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace touchstation::ps3 {

struct GuestPipelineResult {
    bool passed = false;
    std::vector<std::uint8_t> rgba;
    std::vector<std::int16_t> pcm;
    std::uint64_t ppu_instructions = 0;
    std::uint64_t spu_instructions = 0;
    std::uint64_t rsx_method_writes = 0;
    std::string message;
};

/**
 * Deterministic TouchStation homebrew vertical-slice guest.
 *
 * This is a documented development ABI for bundled test/demo code, not a PS3 LV2
 * syscall ABI. It proves PPU -> host bridge -> RSX and PPU -> SPU -> PCM plumbing.
 * It is not evidence of retail PS3 firmware, game, or ELF ABI compatibility.
 */
class GuestPipeline {
public:
    GuestPipelineResult runDemo(std::uint32_t width, std::uint32_t height) const;
};

} // namespace touchstation::ps3
