#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace touchstation::ps3 {

class RsxCommandProcessor {
public:
    static constexpr std::size_t kRegisterCount = 0x10000u / 4u;

    struct Stats {
        std::uint64_t packets = 0;
        std::uint64_t method_writes = 0;
        std::uint64_t draw_calls = 0;
        std::uint64_t clear_calls = 0;
    };

    bool process(const std::vector<std::uint32_t>& command_words);
    const Stats& stats() const noexcept { return stats_; }
    const std::string& fault() const noexcept { return fault_; }
    std::uint32_t registerValue(std::uint32_t byte_offset) const noexcept;
    bool hasPendingClear() const noexcept { return clear_pending_; }

    // A small CPU reference path for RSX CLEAR_SURFACE + scissor state.
    // This is useful for testing the command parser but is not a 3D RSX renderer.
    std::vector<std::uint8_t> renderClearRgba(std::uint32_t width, std::uint32_t height) const;

    static std::uint32_t encodeIncrementPacket(std::uint32_t method, std::uint32_t count,
                                                std::uint32_t subchannel = 0);
    static std::uint32_t encodeNonIncrementPacket(std::uint32_t method, std::uint32_t count,
                                                   std::uint32_t subchannel = 0);

private:
    bool writeMethod(std::uint32_t byte_offset, std::uint32_t value);
    bool fail(const char* reason);

    std::array<std::uint32_t, kRegisterCount> registers_{};
    Stats stats_{};
    std::string fault_;
    std::uint32_t clear_color_ = 0;
    std::uint32_t scissor_horizontal_ = 0;
    std::uint32_t scissor_vertical_ = 0;
    bool clear_pending_ = false;
};

struct RsxSelfTestResult {
    bool passed = false;
    std::uint64_t writes = 0;
    std::string message;
};

RsxSelfTestResult runRsxSelfTest();

} // namespace touchstation::ps3
