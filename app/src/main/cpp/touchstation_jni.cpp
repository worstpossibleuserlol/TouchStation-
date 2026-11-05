#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "libretro.h"

#define LOG_TAG "TouchStationCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
void* gCoreHandle = nullptr;
bool gInitialized = false;
bool gGameLoaded = false;
std::string gSystemDirectory;
std::string gSaveDirectory;
std::string gContentDirectory;
std::string gCorePath;
std::unordered_map<std::string, std::string> gOptions;
bool gOptionsChanged = false;
unsigned gPixelFormat = RETRO_PIXEL_FORMAT_0RGB1555;
double gSampleRate = 44100.0;

std::mutex gSurfaceMutex;
ANativeWindow* gWindow = nullptr;

constexpr size_t kAudioCapacityFrames = 44100 * 2;
std::array<int16_t, kAudioCapacityFrames * 2> gAudio{};
std::atomic<size_t> gAudioRead{0};
std::atomic<size_t> gAudioWrite{0};
std::atomic<uint16_t> gJoypadMask{0};
std::array<std::array<std::atomic<int16_t>, 2>, 2> gAnalog{};

decltype(&retro_set_environment) pSetEnvironment = nullptr;
decltype(&retro_set_video_refresh) pSetVideoRefresh = nullptr;
decltype(&retro_set_audio_sample) pSetAudioSample = nullptr;
decltype(&retro_set_audio_sample_batch) pSetAudioSampleBatch = nullptr;
decltype(&retro_set_input_poll) pSetInputPoll = nullptr;
decltype(&retro_set_input_state) pSetInputState = nullptr;
decltype(&retro_init) pInit = nullptr;
decltype(&retro_deinit) pDeinit = nullptr;
decltype(&retro_load_game) pLoadGame = nullptr;
decltype(&retro_unload_game) pUnloadGame = nullptr;
decltype(&retro_run) pRun = nullptr;
decltype(&retro_reset) pReset = nullptr;
decltype(&retro_get_system_av_info) pGetAvInfo = nullptr;
decltype(&retro_serialize_size) pSerializeSize = nullptr;
decltype(&retro_serialize) pSerialize = nullptr;
decltype(&retro_unserialize) pUnserialize = nullptr;
decltype(&retro_set_controller_port_device) pSetController = nullptr;

template <typename T>
bool bindSymbol(T& target, const char* name) {
    void* symbol = dlsym(gCoreHandle, name);
    if (!symbol) {
        LOGE("Missing core symbol: %s", name);
        return false;
    }
    static_assert(sizeof(target) == sizeof(symbol), "Function pointer size differs from dlsym result");
    std::memcpy(&target, &symbol, sizeof(target));
    return true;
}

void resetAudio() {
    gAudioRead.store(0, std::memory_order_release);
    gAudioWrite.store(0, std::memory_order_release);
}

void pushAudio(int16_t left, int16_t right) {
    size_t write = gAudioWrite.load(std::memory_order_relaxed);
    size_t read = gAudioRead.load(std::memory_order_acquire);
    if (write - read >= kAudioCapacityFrames) {
        gAudioRead.store(write - kAudioCapacityFrames + 1, std::memory_order_release);
    }
    const size_t slot = (write % kAudioCapacityFrames) * 2;
    gAudio[slot] = left;
    gAudio[slot + 1] = right;
    gAudioWrite.store(write + 1, std::memory_order_release);
}

void audioSample(int16_t left, int16_t right) {
    pushAudio(left, right);
}

size_t audioBatch(const int16_t* data, size_t frames) {
    if (!data) return 0;
    for (size_t i = 0; i < frames; ++i) {
        pushAudio(data[i * 2], data[i * 2 + 1]);
    }
    return frames;
}

void videoRefresh(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0) return;

    std::lock_guard<std::mutex> lock(gSurfaceMutex);
    if (!gWindow) return;

    ANativeWindow_setBuffersGeometry(gWindow, static_cast<int32_t>(width),
                                     static_cast<int32_t>(height), WINDOW_FORMAT_RGB_565);
    ANativeWindow_Buffer buffer{};
    if (ANativeWindow_lock(gWindow, &buffer, nullptr) != 0) return;

    const unsigned copyWidth = std::min(width, static_cast<unsigned>(buffer.width));
    const unsigned copyHeight = std::min(height, static_cast<unsigned>(buffer.height));
    auto* destination = static_cast<uint16_t*>(buffer.bits);

    for (unsigned y = 0; y < copyHeight; ++y) {
        auto* out = reinterpret_cast<uint16_t*>(
            reinterpret_cast<uint8_t*>(destination) + static_cast<size_t>(y) * buffer.stride * 2);
        const auto* source = static_cast<const uint8_t*>(data) + static_cast<size_t>(y) * pitch;

        if (gPixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
            std::memcpy(out, source, static_cast<size_t>(copyWidth) * 2);
        } else if (gPixelFormat == RETRO_PIXEL_FORMAT_0RGB1555) {
            const auto* in = reinterpret_cast<const uint16_t*>(source);
            for (unsigned x = 0; x < copyWidth; ++x) {
                const uint16_t p = in[x];
                const uint16_t r = (p >> 10) & 0x1f;
                const uint16_t g = (p >> 5) & 0x1f;
                const uint16_t b = p & 0x1f;
                out[x] = static_cast<uint16_t>((r << 11) | (((g << 1) | (g >> 4)) << 5) | b);
            }
        } else {
            const auto* in = reinterpret_cast<const uint32_t*>(source);
            for (unsigned x = 0; x < copyWidth; ++x) {
                const uint32_t p = in[x];
                out[x] = static_cast<uint16_t>((((p >> 19) & 0x1f) << 11) |
                                                (((p >> 10) & 0x3f) << 5) |
                                                ((p >> 3) & 0x1f));
            }
        }
    }
    ANativeWindow_unlockAndPost(gWindow);
}

void inputPoll() {}

int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) return 0;
    if (device == RETRO_DEVICE_ANALOG && index < 2 && id < 2) {
        return gAnalog[index][id].load(std::memory_order_acquire);
    }
    if (device != RETRO_DEVICE_JOYPAD || index != 0) return 0;
    const uint16_t mask = gJoypadMask.load(std::memory_order_acquire);
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return static_cast<int16_t>(mask);
    if (id >= 16) return 0;
    return (mask & (1u << id)) ? 1 : 0;
}

void coreLog(enum retro_log_level, const char* format, ...) {
    char buffer[1024];
    va_list args;
    va_start(args, format);
    std::vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, buffer);
}

std::string trim(const std::string& value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return {};
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1);
}

bool environment(unsigned command, void* data) {
    switch (command) {
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            if (data) *static_cast<const char**>(data) = gSystemDirectory.c_str();
            return data != nullptr;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (data) *static_cast<const char**>(data) = gSaveDirectory.c_str();
            return data != nullptr;
#ifdef RETRO_ENVIRONMENT_GET_CONTENT_DIRECTORY
        case RETRO_ENVIRONMENT_GET_CONTENT_DIRECTORY:
            if (data) *static_cast<const char**>(data) = gContentDirectory.c_str();
            return data != nullptr;
#endif
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            if (!data) return false;
            gPixelFormat = *static_cast<unsigned*>(data);
            return gPixelFormat == RETRO_PIXEL_FORMAT_0RGB1555 ||
                   gPixelFormat == RETRO_PIXEL_FORMAT_RGB565 ||
                   gPixelFormat == RETRO_PIXEL_FORMAT_XRGB8888;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) *static_cast<bool*>(data) = true;
            return data != nullptr;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            if (data) *static_cast<bool*>(data) = true;
            return data != nullptr;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            if (data) *static_cast<unsigned*>(data) = 0;
            return data != nullptr;
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            if (data) {
                *static_cast<bool*>(data) = gOptionsChanged;
                gOptionsChanged = false;
            }
            return data != nullptr;
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            if (!data) return false;
            auto* variables = static_cast<retro_variable*>(data);
            for (size_t i = 0; variables[i].key != nullptr; ++i) {
                if (!variables[i].value) continue;
                const std::string raw = variables[i].value;
                const size_t separator = raw.find(';');
                if (separator == std::string::npos) continue;
                std::string choices = trim(raw.substr(separator + 1));
                const size_t next = choices.find('|');
                const std::string defaultValue = trim(choices.substr(0, next));
                if (gOptions.find(variables[i].key) == gOptions.end()) {
                    gOptions[variables[i].key] = defaultValue;
                }
            }
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            if (!data) return false;
            auto* variable = static_cast<retro_variable*>(data);
            if (!variable->key) return false;
            const auto found = gOptions.find(variable->key);
            if (found == gOptions.end()) return false;
            variable->value = found->second.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            if (data) static_cast<retro_log_callback*>(data)->log = coreLog;
            return data != nullptr;
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
            return true;
        default:
            return false;
    }
}

bool ensureCore() {
    if (!gCoreHandle) {
        gCoreHandle = dlopen("libpcsx_rearmed.so", RTLD_NOW);
        if (!gCoreHandle) {
            LOGE("dlopen failed: %s", dlerror() ? dlerror() : "unknown error");
            return false;
        }
    }

    bool ok = true;
    ok = bindSymbol(pSetEnvironment, "retro_set_environment") && ok;
    ok = bindSymbol(pSetVideoRefresh, "retro_set_video_refresh") && ok;
    ok = bindSymbol(pSetAudioSample, "retro_set_audio_sample") && ok;
    ok = bindSymbol(pSetAudioSampleBatch, "retro_set_audio_sample_batch") && ok;
    ok = bindSymbol(pSetInputPoll, "retro_set_input_poll") && ok;
    ok = bindSymbol(pSetInputState, "retro_set_input_state") && ok;
    ok = bindSymbol(pInit, "retro_init") && ok;
    ok = bindSymbol(pDeinit, "retro_deinit") && ok;
    ok = bindSymbol(pLoadGame, "retro_load_game") && ok;
    ok = bindSymbol(pUnloadGame, "retro_unload_game") && ok;
    ok = bindSymbol(pRun, "retro_run") && ok;
    ok = bindSymbol(pReset, "retro_reset") && ok;
    ok = bindSymbol(pGetAvInfo, "retro_get_system_av_info") && ok;
    ok = bindSymbol(pSerializeSize, "retro_serialize_size") && ok;
    ok = bindSymbol(pSerialize, "retro_serialize") && ok;
    ok = bindSymbol(pUnserialize, "retro_unserialize") && ok;

    void* controllerSymbol = dlsym(gCoreHandle, "retro_set_controller_port_device");
    if (controllerSymbol) {
        static_assert(sizeof(pSetController) == sizeof(controllerSymbol), "Function pointer size differs from dlsym result");
        std::memcpy(&pSetController, &controllerSymbol, sizeof(pSetController));
    }
    return ok;
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_touchstation_app_CoreBridge_nativeInitialize(JNIEnv* env, jobject,
                                                       jstring systemDirectory,
                                                       jstring saveDirectory) {
    const char* systemPath = env->GetStringUTFChars(systemDirectory, nullptr);
    const char* savePath = env->GetStringUTFChars(saveDirectory, nullptr);
    gSystemDirectory = systemPath ? systemPath : "";
    gSaveDirectory = savePath ? savePath : "";
    env->ReleaseStringUTFChars(systemDirectory, systemPath);
    env->ReleaseStringUTFChars(saveDirectory, savePath);
    gContentDirectory = gSystemDirectory;
    gCorePath = "libpcsx_rearmed.so";

    if (gInitialized) return JNI_TRUE;
    if (!ensureCore()) return JNI_FALSE;

    pSetEnvironment(environment);
    pSetVideoRefresh(videoRefresh);
    pSetAudioSample(audioSample);
    pSetAudioSampleBatch(audioBatch);
    pSetInputPoll(inputPoll);
    pSetInputState(inputState);
    pInit();
    if (pSetController) pSetController(0, RETRO_DEVICE_JOYPAD);

    gInitialized = true;
    gGameLoaded = false;
    resetAudio();
    LOGI("PCSX-ReARMed core initialized");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_touchstation_app_CoreBridge_nativeLoadGame(JNIEnv* env, jobject, jstring gamePath) {
    if (!gInitialized || !gamePath) return JNI_FALSE;
    if (gGameLoaded && pUnloadGame) {
        pUnloadGame();
        gGameLoaded = false;
    }

    const char* path = env->GetStringUTFChars(gamePath, nullptr);
    if (!path) return JNI_FALSE;
    const std::string fullPath(path);
    env->ReleaseStringUTFChars(gamePath, path);

    const size_t slash = fullPath.find_last_of('/');
    gContentDirectory = slash == std::string::npos ? gSystemDirectory : fullPath.substr(0, slash);

    retro_game_info info{};
    info.path = fullPath.c_str();
    const bool loaded = pLoadGame(&info);
    gGameLoaded = loaded;
    resetAudio();

    if (loaded && pGetAvInfo) {
        retro_system_av_info av{};
        pGetAvInfo(&av);
        if (av.timing.sample_rate > 0) gSampleRate = av.timing.sample_rate;
    }
    LOGI("Game load %s: %s", loaded ? "succeeded" : "failed", fullPath.c_str());
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_touchstation_app_CoreBridge_nativeRunFrame(JNIEnv*, jobject) {
    if (gInitialized && gGameLoaded && pRun) pRun();
}

extern "C" JNIEXPORT void JNICALL
Java_com_touchstation_app_CoreBridge_nativeSetSurface(JNIEnv* env, jobject, jobject surface) {
    std::lock_guard<std::mutex> lock(gSurfaceMutex);
    if (gWindow) {
        ANativeWindow_release(gWindow);
        gWindow = nullptr;
    }
    if (surface) gWindow = ANativeWindow_fromSurface(env, surface);
}

extern "C" JNIEXPORT void JNICALL
Java_com_touchstation_app_CoreBridge_nativeSetButton(JNIEnv*, jobject, jint id, jboolean pressed) {
    if (id < 0 || id >= 16) return;
    const uint16_t bit = static_cast<uint16_t>(1u << id);
    if (pressed) gJoypadMask.fetch_or(bit, std::memory_order_acq_rel);
    else gJoypadMask.fetch_and(static_cast<uint16_t>(~bit), std::memory_order_acq_rel);
}

extern "C" JNIEXPORT void JNICALL
Java_com_touchstation_app_CoreBridge_nativeSetAnalog(JNIEnv*, jobject, jint stick, jint axis, jint value) {
    if (stick < 0 || stick >= 2 || axis < 0 || axis >= 2) return;
    const int clamped = std::max(-32767, std::min(32767, value));
    gAnalog[stick][axis].store(static_cast<int16_t>(clamped), std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL
Java_com_touchstation_app_CoreBridge_nativeSetControllerMode(JNIEnv*, jobject, jint mode) {
    if (!gInitialized || !pSetController) return;
    unsigned device = RETRO_DEVICE_JOYPAD;
    if (mode == 1) {
        device = RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_ANALOG, 0);
    } else if (mode == 2) {
        device = RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_ANALOG, 1);
    }
    pSetController(0, device);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_touchstation_app_CoreBridge_nativeReadAudio(JNIEnv* env, jobject, jshortArray output,
                                                      jint maxFrames) {
    if (!output || maxFrames <= 0) return 0;
    const size_t read = gAudioRead.load(std::memory_order_relaxed);
    const size_t write = gAudioWrite.load(std::memory_order_acquire);
    const size_t frames = std::min(static_cast<size_t>(maxFrames), write - read);
    if (frames == 0) return 0;

    std::vector<jshort> samples(frames * 2);
    for (size_t i = 0; i < frames; ++i) {
        const size_t slot = ((read + i) % kAudioCapacityFrames) * 2;
        samples[i * 2] = gAudio[slot];
        samples[i * 2 + 1] = gAudio[slot + 1];
    }
    env->SetShortArrayRegion(output, 0, static_cast<jsize>(samples.size()), samples.data());
    gAudioRead.store(read + frames, std::memory_order_release);
    return static_cast<jint>(frames);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_touchstation_app_CoreBridge_nativeGetSampleRate(JNIEnv*, jobject) {
    return static_cast<jint>(gSampleRate);
}

extern "C" JNIEXPORT void JNICALL
Java_com_touchstation_app_CoreBridge_nativeSetOption(JNIEnv* env, jobject, jstring key,
                                                      jstring value) {
    if (!key || !value) return;
    const char* keyChars = env->GetStringUTFChars(key, nullptr);
    const char* valueChars = env->GetStringUTFChars(value, nullptr);
    if (keyChars && valueChars) {
        gOptions[keyChars] = valueChars;
        gOptionsChanged = true;
    }
    if (keyChars) env->ReleaseStringUTFChars(key, keyChars);
    if (valueChars) env->ReleaseStringUTFChars(value, valueChars);
}

extern "C" JNIEXPORT void JNICALL
Java_com_touchstation_app_CoreBridge_nativeReset(JNIEnv*, jobject) {
    if (gInitialized && gGameLoaded && pReset) pReset();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_touchstation_app_CoreBridge_nativeSaveState(JNIEnv* env, jobject, jstring statePath) {
    if (!gGameLoaded || !pSerializeSize || !pSerialize || !statePath) return JNI_FALSE;
    const size_t size = pSerializeSize();
    if (size == 0 || size > 256u * 1024u * 1024u) return JNI_FALSE;
    std::vector<uint8_t> data(size);
    if (!pSerialize(data.data(), data.size())) return JNI_FALSE;

    const char* path = env->GetStringUTFChars(statePath, nullptr);
    if (!path) return JNI_FALSE;
    std::ofstream output(path, std::ios::binary);
    env->ReleaseStringUTFChars(statePath, path);
    if (!output) return JNI_FALSE;
    output.write(reinterpret_cast<const char*>(data.data()), static_cast<std::streamsize>(data.size()));
    return output.good() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_touchstation_app_CoreBridge_nativeLoadState(JNIEnv* env, jobject, jstring statePath) {
    if (!gGameLoaded || !pUnserialize || !statePath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(statePath, nullptr);
    if (!path) return JNI_FALSE;
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    env->ReleaseStringUTFChars(statePath, path);
    if (!input) return JNI_FALSE;
    const auto size = input.tellg();
    if (size <= 0 || size > 256 * 1024 * 1024) return JNI_FALSE;
    std::vector<uint8_t> data(static_cast<size_t>(size));
    input.seekg(0);
    input.read(reinterpret_cast<char*>(data.data()), size);
    if (!input) return JNI_FALSE;
    return pUnserialize(data.data(), data.size()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_touchstation_app_CoreBridge_nativeStop(JNIEnv*, jobject) {
    if (gGameLoaded && pUnloadGame) pUnloadGame();
    gGameLoaded = false;
    if (gInitialized && pDeinit) pDeinit();
    gInitialized = false;
    gJoypadMask.store(0, std::memory_order_release);
    resetAudio();
    LOGI("Emulation stopped");
}
