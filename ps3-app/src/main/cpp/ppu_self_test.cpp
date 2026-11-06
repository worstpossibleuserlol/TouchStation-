#include "ppu_interpreter.hpp"
#include <iostream>
int main() {
    const auto result = touchstation::ps3::runPpuSelfTest();
    std::cout << result.message << "\n";
    if (!result.passed) return 1;
    if (result.instructions != 6u || result.result != 0x100cu) return 2;
    return 0;
}
