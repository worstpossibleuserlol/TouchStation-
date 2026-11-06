#include "rsx_command_processor.hpp"

#include <algorithm>
#include <limits>

namespace touchstation::ps3 {
namespace {

constexpr std::uint32_t kCommandClassMask = 0xe0030003u;
constexpr std::uint32_t kIncrementCommand = 0x00000000u;
constexpr std::uint32_t kNonIncrementCommand = 0x40000000u;
constexpr std::uint32_t kOldJumpCommand = 0x20000000u;
constexpr std::uint32_t kNewJumpCommand = 0x00000001u;
constexpr std::uint32_t kCallCommand = 0x00000002u;
constexpr std::uint32_t kReturnCommand = 0x00020000u;
constexpr std::uint32_t kReturnMask = 0xffff0003u;

constexpr std::uint32_t kColorClearValue = 0x00001d90u;
constexpr std::uint32_t kSurfaceClipHorizontal = 0x00000200u;
constexpr std::uint32_t kSurfaceClipVertical = 0x00000204u;
constexpr std::uint32_t kScissorHorizontal = 0x000008c0u;
constexpr std::uint32_t kScissorVertical = 0x000008c4u;
constexpr std::uint32_t kBeginEnd = 0x00001808u;
constexpr std::uint32_t kDrawArrays = 0x00001814u;
constexpr std::uint32_t kClearSurface = 0x00001d94u;

std::uint32_t low16(std::uint32_t value) { return value & 0xffffu; }
std::uint32_t high16(std::uint32_t value) { return (value >> 16u) & 0xffffu; }

} // namespace

bool RsxCommandProcessor::fail(const char* reason) {
    fault_ = reason ? reason : "unknown RSX command error";
    return false;
}

std::uint32_t RsxCommandProcessor::registerValue(std::uint32_t byte_offset) const noexcept {
    if ((byte_offset & 3u) != 0u || byte_offset >= 0x10000u) return 0u;
    return registers_[byte_offset / 4u];
}

bool RsxCommandProcessor::writeMethod(std::uint32_t byte_offset, std::uint32_t value) {
    if ((byte_offset & 3u) != 0u || byte_offset >= 0x10000u)
        return fail("RSX method register is unaligned or outside the 64 KiB method window");
    registers_[byte_offset / 4u] = value;
    ++stats_.method_writes;

    switch (byte_offset) {
        case kColorClearValue:
            clear_color_ = value;
            break;
        case kScissorHorizontal:
            scissor_horizontal_ = value;
            break;
        case kScissorVertical:
            scissor_vertical_ = value;
            break;
        case kDrawArrays:
            ++stats_.draw_calls;
            break;
        case kClearSurface:
            if ((value & 0xf3u) != 0u) {
                clear_pending_ = true;
                ++stats_.clear_calls;
            }
            break;
        case kBeginEnd:
        case kSurfaceClipHorizontal:
        case kSurfaceClipVertical:
        default:
            break;
    }
    return true;
}

bool RsxCommandProcessor::process(const std::vector<std::uint32_t>& command_words) {
    fault_.clear();
    std::size_t cursor = 0;

    while (cursor < command_words.size()) {
        const std::uint32_t header = command_words[cursor++];
        const std::uint32_t command_class = header & kCommandClassMask;

        if (command_class == kOldJumpCommand ||
            command_class == kNewJumpCommand ||
            (header & 0x00000003u) == kCallCommand ||
            (header & kReturnMask) == kReturnCommand) {
            return fail("RSX FIFO jump/call/return needs a mapped guest command buffer and is not followed by this parser");
        }

        const bool incrementing = command_class == kIncrementCommand;
        const bool non_incrementing = command_class == kNonIncrementCommand;
        if (!incrementing && !non_incrementing)
            return fail("unsupported or malformed RSX FIFO command header");

        const std::uint32_t count = (header >> 18u) & 0x7ffu;
        const std::uint32_t subchannel = (header >> 13u) & 0x7u;
        (void)subchannel; // Kept in the packet layout; method state is currently shared.
        std::uint32_t method = header & 0x0000fffcu;

        if (count == 0u) {
            ++stats_.packets;
            continue;
        }
        if (static_cast<std::size_t>(count) > command_words.size() - cursor)
            return fail("RSX FIFO packet is truncated before all method arguments");

        ++stats_.packets;
        for (std::uint32_t i = 0; i < count; ++i) {
            if (!writeMethod(method, command_words[cursor++])) return false;
            if (incrementing) {
                if (method > 0x0000fff8u && i + 1u < count)
                    return fail("RSX incrementing method packet crosses the method-register window");
                method += 4u;
            }
        }
    }
    return true;
}

std::vector<std::uint8_t> RsxCommandProcessor::renderClearRgba(std::uint32_t width,
                                                               std::uint32_t height) const {
    if (width == 0u || height == 0u || width > 4096u || height > 4096u) return {};
    const std::uint64_t byte_count = static_cast<std::uint64_t>(width) * height * 4u;
    if (byte_count > 64u * 1024u * 1024u) return {};

    std::vector<std::uint8_t> pixels(static_cast<std::size_t>(byte_count), 0u);
    if (!clear_pending_) return pixels;

    std::uint32_t x = low16(scissor_horizontal_);
    std::uint32_t y = low16(scissor_vertical_);
    std::uint32_t w = high16(scissor_horizontal_);
    std::uint32_t h = high16(scissor_vertical_);
    if (w == 0u || h == 0u) {
        x = 0u; y = 0u; w = width; h = height;
    }

    const std::uint32_t x_end = std::min(width, x > width ? width : x + std::min(w, width));
    const std::uint32_t y_end = std::min(height, y > height ? height : y + std::min(h, height));
    x = std::min(x, width);
    y = std::min(y, height);

    const std::uint8_t alpha = static_cast<std::uint8_t>((clear_color_ >> 24u) & 0xffu);
    const std::uint8_t red = static_cast<std::uint8_t>((clear_color_ >> 16u) & 0xffu);
    const std::uint8_t green = static_cast<std::uint8_t>((clear_color_ >> 8u) & 0xffu);
    const std::uint8_t blue = static_cast<std::uint8_t>(clear_color_ & 0xffu);

    for (std::uint32_t row = y; row < y_end; ++row) {
        for (std::uint32_t col = x; col < x_end; ++col) {
            const std::size_t pixel = (static_cast<std::size_t>(row) * width + col) * 4u;
            pixels[pixel] = red;
            pixels[pixel + 1u] = green;
            pixels[pixel + 2u] = blue;
            pixels[pixel + 3u] = alpha;
        }
    }
    return pixels;
}

std::uint32_t RsxCommandProcessor::encodeIncrementPacket(std::uint32_t method,
                                                         std::uint32_t count,
                                                         std::uint32_t subchannel) {
    return ((count & 0x7ffu) << 18u) | ((subchannel & 0x7u) << 13u) | (method & 0xfffcu);
}

std::uint32_t RsxCommandProcessor::encodeNonIncrementPacket(std::uint32_t method,
                                                            std::uint32_t count,
                                                            std::uint32_t subchannel) {
    return kNonIncrementCommand | ((count & 0x7ffu) << 18u) |
           ((subchannel & 0x7u) << 13u) | (method & 0xfffcu);
}

RsxSelfTestResult runRsxSelfTest() {
    RsxSelfTestResult result;
    RsxCommandProcessor rsx;
    const std::vector<std::uint32_t> commands = {
        RsxCommandProcessor::encodeIncrementPacket(kColorClearValue, 1u), 0xff204080u,
        RsxCommandProcessor::encodeIncrementPacket(kScissorHorizontal, 2u), 0x00020001u, 0x00010001u,
        RsxCommandProcessor::encodeNonIncrementPacket(kClearSurface, 1u), 0x000000f0u
    };
    if (!rsx.process(commands)) {
        result.message = "FAIL: RSX command parsing: " + rsx.fault();
        return result;
    }

    const auto frame = rsx.renderClearRgba(4u, 3u);
    const auto pixel = [&frame](std::size_t x, std::size_t y) {
        const std::size_t offset = (y * 4u + x) * 4u;
        if (offset + 3u >= frame.size()) return std::array<std::uint8_t, 4>{};
        return std::array<std::uint8_t, 4>{frame[offset], frame[offset + 1u],
                                           frame[offset + 2u], frame[offset + 3u]};
    };
    const auto inside = pixel(1u, 1u);
    const auto outside = pixel(0u, 0u);

    result.writes = rsx.stats().method_writes;
    result.passed = rsx.fault().empty() && rsx.stats().packets == 3u &&
                    rsx.stats().method_writes == 4u && rsx.stats().clear_calls == 1u &&
                    frame.size() == 48u && inside[0] == 0x20u &&
                    inside[1] == 0x40u && inside[2] == 0x80u && inside[3] == 0xffu &&
                    outside[3] == 0u;
    result.message = result.passed
        ? "PASS: RSX FIFO packet decode, method state, clear color and scissor reference path passed."
        : "FAIL: RSX FIFO or software clear reference test mismatch.";
    if (!result.passed) return result;

    RsxCommandProcessor truncated;
    const std::vector<std::uint32_t> malformed = {
        RsxCommandProcessor::encodeIncrementPacket(kColorClearValue, 2u),
        0xff112233u
    };
    if (truncated.process(malformed) || truncated.fault().empty()) {
        result.passed = false;
        result.message = "FAIL: RSX FIFO parser did not reject a truncated method packet.";
        return result;
    }

    RsxCommandProcessor jump;
    if (jump.process({0x20000000u}) || jump.fault().empty()) {
        result.passed = false;
        result.message = "FAIL: RSX parser did not safely reject an unmapped FIFO jump.";
        return result;
    }

    result.message += " Truncated-packet and unmapped-jump rejection tests passed.";
    return result;
}

} // namespace touchstation::ps3
