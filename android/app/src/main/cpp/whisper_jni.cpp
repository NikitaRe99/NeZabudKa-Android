#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cctype>
#include <chrono>
#include <climits>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <malloc.h>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include "whisper.h"

namespace {
std::mutex g_mutex;
std::atomic_bool g_abort_requested { false };
whisper_context *g_context = nullptr;
std::string g_model_path;
whisper_vad_context *g_vad_context = nullptr;
std::string g_vad_model_path;
float g_last_confidence = 0.0f;
double g_last_metrics[9] = {};

void throw_runtime(JNIEnv *env, const std::string &message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

std::string to_string(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring new_string_utf8(JNIEnv *env, const std::string &value) {
    std::vector<jchar> utf16;
    utf16.reserve(value.size());
    size_t index = 0;
    while (index < value.size()) {
        const auto first = static_cast<unsigned char>(value[index]);
        uint32_t code_point = 0;
        size_t length = 0;
        if (first < 0x80) {
            code_point = first;
            length = 1;
        } else if ((first & 0xE0) == 0xC0) {
            code_point = first & 0x1F;
            length = 2;
        } else if ((first & 0xF0) == 0xE0) {
            code_point = first & 0x0F;
            length = 3;
        } else if ((first & 0xF8) == 0xF0) {
            code_point = first & 0x07;
            length = 4;
        }

        bool valid = length != 0 && index + length <= value.size();
        for (size_t offset = 1; valid && offset < length; ++offset) {
            const auto continuation = static_cast<unsigned char>(value[index + offset]);
            valid = (continuation & 0xC0) == 0x80;
            if (valid) code_point = (code_point << 6) | (continuation & 0x3F);
        }
        const uint32_t minimum = length == 2 ? 0x80 : length == 3 ? 0x800 : length == 4 ? 0x10000 : 0;
        valid = valid && code_point >= minimum && code_point <= 0x10FFFF &&
                !(code_point >= 0xD800 && code_point <= 0xDFFF);
        if (!valid) {
            code_point = 0xFFFD;
            length = 1;
        }
        index += length;

        if (code_point <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(code_point));
        } else {
            code_point -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (code_point >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (code_point & 0x3FF)));
        }
    }
    const jchar empty = 0;
    return env->NewString(utf16.empty() ? &empty : utf16.data(), static_cast<jsize>(utf16.size()));
}

void free_whisper_context() {
    if (g_context != nullptr) {
        whisper_free(g_context);
        g_context = nullptr;
    }
    g_model_path.clear();
}

void free_vad_context() {
    if (g_vad_context != nullptr) {
        whisper_vad_free(g_vad_context);
        g_vad_context = nullptr;
    }
    g_vad_model_path.clear();
}

bool load_context(const std::string &model_path) {
    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;
    context_params.flash_attn = true;
    const auto started = std::chrono::steady_clock::now();
    // Retain only the model weights. Decoder/encoder state belongs to one call,
    // including retries, so an aborted or failed utterance cannot poison the next.
    g_context = whisper_init_from_file_with_params_no_state(model_path.c_str(), context_params);
    if (g_context == nullptr) return false;
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - started).count();
    __android_log_print(ANDROID_LOG_INFO, "LocalAssistantWhisper",
                        "MODEL_READY %lldms cpu %s",
                        static_cast<long long>(elapsed), model_path.c_str());
    g_model_path = model_path;
    return true;
}

bool ensure_context(const std::string &model_path) {
    if (g_context != nullptr && g_model_path == model_path) return true;
    // The VAD context is independent and cheap to reuse between requests.
    // Reloading the speech model must not evict it.
    free_whisper_context();
    return load_context(model_path);
}

bool ensure_vad_context(const std::string &model_path, int thread_count) {
    if (g_vad_context != nullptr && g_vad_model_path == model_path) return true;
    free_vad_context();
    whisper_vad_context_params params = whisper_vad_default_context_params();
    params.n_threads = std::max(1, thread_count);
    params.use_gpu = false;
    g_vad_context = whisper_vad_init_from_file_with_params(model_path.c_str(), params);
    if (g_vad_context == nullptr) return false;
    g_vad_model_path = model_path;
    return true;
}

struct AbortControl {
    std::chrono::steady_clock::time_point deadline;
    bool enabled;
    std::atomic_bool timed_out { false };
};

bool abort_requested(void *user_data) {
    if (g_abort_requested.load(std::memory_order_relaxed)) return true;
    auto *control = static_cast<AbortControl *>(user_data);
    if (control != nullptr && control->enabled && std::chrono::steady_clock::now() >= control->deadline) {
        control->timed_out.store(true, std::memory_order_relaxed);
        return true;
    }
    return false;
}

uint16_t le16(const unsigned char * value) {
    return static_cast<uint16_t>(value[0]) |
           (static_cast<uint16_t>(value[1]) << 8U);
}

uint32_t le32(const unsigned char * value) {
    return static_cast<uint32_t>(value[0]) |
           (static_cast<uint32_t>(value[1]) << 8U) |
           (static_cast<uint32_t>(value[2]) << 16U) |
           (static_cast<uint32_t>(value[3]) << 24U);
}

bool read_mono_pcm16_wav(const std::string & path, std::vector<float> & samples, std::string & error) {
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    if (!input) {
        error = "Не удалось открыть обработанный WAV: " + path;
        return false;
    }
    const std::streamoff file_size = static_cast<std::streamoff>(input.tellg());
    if (file_size < 44 || (file_size - 44) % 2 != 0) {
        error = "Whisper ожидает полный WAV PCM16";
        return false;
    }
    input.seekg(0, std::ios::beg);
    unsigned char header[44] {};
    if (!input.read(reinterpret_cast<char *>(header), sizeof(header)) ||
        std::memcmp(header, "RIFF", 4) != 0 ||
        std::memcmp(header + 8, "WAVEfmt ", 8) != 0 ||
        std::memcmp(header + 36, "data", 4) != 0 ||
        le16(header + 20) != 1 || le16(header + 22) != 1 ||
        le32(header + 24) != 16000 || le16(header + 34) != 16) {
        error = "Поддерживается только WAV PCM16 mono 16 кГц";
        return false;
    }
    const size_t sample_count = static_cast<size_t>((file_size - 44) / 2);
    if (sample_count == 0 || sample_count > static_cast<size_t>(INT32_MAX)) {
        error = "Обработанная аудиозапись пуста или слишком велика: bytes=" +
                std::to_string(static_cast<long long>(file_size)) +
                ", samples=" + std::to_string(sample_count);
        return false;
    }
    samples.resize(sample_count);
    unsigned char buffer[64 * 1024];
    size_t written = 0;
    while (written < sample_count) {
        const size_t wanted = std::min(sizeof(buffer), (sample_count - written) * 2);
        if (!input.read(reinterpret_cast<char *>(buffer), static_cast<std::streamsize>(wanted))) {
            error = "Не удалось полностью прочитать WAV PCM";
            return false;
        }
        for (size_t offset = 0; offset < wanted; offset += 2) {
            const auto raw = static_cast<int16_t>(le16(buffer + offset));
            samples[written++] = static_cast<float>(raw) / 32768.0f;
        }
    }
    return true;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_ru_nezabudka_app_speech_WhisperNative_transcribeWav(
        JNIEnv *env,
        jobject,
        jstring model_path_value,
        jstring wav_path_value,
        jstring vad_model_path_value,
        jstring language_value,
        jstring prompt_value,
        jint thread_count,
        jint audio_context,
        jint max_tokens,
        jboolean single_segment,
        jint beam_size,
        jint time_limit_ms,
        jfloat temperature_increment) {
    const std::string model_path = to_string(env, model_path_value);
    const std::string wav_path = to_string(env, wav_path_value);
    const std::string vad_model_path = to_string(env, vad_model_path_value);
    const std::string language = to_string(env, language_value);
    const std::string prompt = to_string(env, prompt_value);
    if (model_path.empty()) {
        throw_runtime(env, "Путь к модели whisper.cpp пуст");
        return nullptr;
    }
    std::vector<float> samples;
    std::string wav_error;
    if (!read_mono_pcm16_wav(wav_path, samples, wav_error)) {
        throw_runtime(env, wav_error);
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    g_abort_requested.store(false, std::memory_order_relaxed);
    g_last_confidence = 0.0f;
    std::fill(std::begin(g_last_metrics), std::end(g_last_metrics), 0.0);
    const auto load_started = std::chrono::steady_clock::now();
    if (!ensure_context(model_path)) {
        throw_runtime(env, "whisper.cpp не смог загрузить GGML-модель: " + model_path);
        return nullptr;
    }
    g_last_metrics[7] = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - load_started).count();

    std::unique_ptr<whisper_state, decltype(&whisper_free_state)> state(
            whisper_init_state(g_context), whisper_free_state);
    if (!state) {
        throw_runtime(env, "Не удалось создать чистое состояние распознавания Whisper");
        return nullptr;
    }

    const int sample_count = static_cast<int>(samples.size());

    const bool accurate = beam_size > 1;
    whisper_full_params params = whisper_full_default_params(
            accurate ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    params.beam_search.beam_size = accurate ? std::min(5, static_cast<int>(beam_size)) : 1;
    params.n_threads = std::max(1, static_cast<int>(thread_count));
    params.language = language.empty() ? "ru" : language.c_str();
    params.initial_prompt = prompt.empty() ? nullptr : prompt.c_str();
    params.translate = false;
    params.no_context = true;
    params.carry_initial_prompt = false;
    params.no_timestamps = true;
    // A note can span many sentences. Single-segment mode is intended for short
    // streaming commands and truncates long dictation once the decoder reaches
    // its segment token budget.
    params.single_segment = single_segment == JNI_TRUE;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.abort_callback = abort_requested;
    AbortControl abort_control {
        std::chrono::steady_clock::now() + std::chrono::milliseconds(std::max(0, static_cast<int>(time_limit_ms))),
        time_limit_ms > 0,
    };
    params.abort_callback_user_data = &abort_control;
    params.vad = !vad_model_path.empty();
    params.vad_model_path = params.vad ? vad_model_path.c_str() : nullptr;
    params.vad_params.threshold = 0.5f;
    params.vad_params.min_speech_duration_ms = 250;
    params.vad_params.min_silence_duration_ms = 500;
    params.vad_params.speech_pad_ms = 200;
    params.vad_params.samples_overlap = 0.1f;
    // Keep greedy decoding lightweight on a phone, but do not cap the transcript:
    // whisper.cpp will split long audio into as many segments as necessary.
    params.greedy.best_of = audio_context > 0 && audio_context <= 256 ? 2 : 1;
    // Production uses one deterministic pass and bounded Kotlin refinement.
    // Diagnostics can explicitly enable the original temperature fallback.
    params.temperature_inc = accurate ? 0.0f : std::max(0.0f, static_cast<float>(temperature_increment));
    params.n_max_text_ctx = 256;
    params.max_tokens = std::max(0, static_cast<int>(max_tokens));
    const int requested_audio_context = std::max(0, static_cast<int>(audio_context));
    params.audio_ctx = requested_audio_context == 0
        ? 0
        : std::min(requested_audio_context, whisper_n_audio_ctx(g_context));

    const auto started = std::chrono::steady_clock::now();
    int status = whisper_full_with_state(g_context, state.get(), params, samples.data(), sample_count);
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - started).count();
    __android_log_print(ANDROID_LOG_INFO, "LocalAssistantWhisper",
                        "INFERENCE_DONE %lldms audio=%.2fs threads=%d ctx=%d tokens=%d single=%d vad=%d beam=%d status=%d",
                        static_cast<long long>(elapsed), sample_count / 16000.0,
                        params.n_threads, params.audio_ctx, params.max_tokens,
                        params.single_segment ? 1 : 0, params.vad ? 1 : 0, accurate ? params.beam_search.beam_size : 1, status);
    if (abort_control.timed_out.load(std::memory_order_relaxed) &&
            !g_abort_requested.load(std::memory_order_relaxed)) {
        jclass cls = env->FindClass("java/util/concurrent/TimeoutException");
        if (cls != nullptr) env->ThrowNew(cls, "Точный поиск превысил лимит времени");
        return nullptr;
    }
    if (status != 0) {
        __android_log_print(ANDROID_LOG_WARN, "LocalAssistantWhisper", "INFERENCE_ERROR status=%d cancelled=%d timedOut=%d",
                            status, g_abort_requested.load() ? 1 : 0, abort_control.timed_out.load() ? 1 : 0);
        throw_runtime(env, "whisper.cpp завершил распознавание с ошибкой " + std::to_string(status));
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments_from_state(state.get());
    double probability_sum = 0.0;
    int probability_count = 0;
    double text_sum = 0.0, text_log_sum = 0.0, text_min = 1.0;
    int text_count = 0, weak_count = 0;
    for (int index = 0; index < segments; ++index) {
        g_last_metrics[6] = std::max(g_last_metrics[6], static_cast<double>(whisper_full_get_segment_no_speech_prob_from_state(state.get(), index)));
        const char *segment = whisper_full_get_segment_text_from_state(state.get(), index);
        if (segment != nullptr) text += segment;
        const int tokens = whisper_full_n_tokens_from_state(state.get(), index);
        for (int token = 0; token < tokens; ++token) {
            const float probability = whisper_full_get_token_p_from_state(state.get(), index, token);
            if (std::isfinite(probability) && probability >= 0.0f && probability <= 1.0f) {
                probability_sum += probability;
                probability_count++;
                const auto token_id = whisper_full_get_token_id_from_state(state.get(), index, token);
                const char *piece = whisper_token_to_str(g_context, token_id);
                const bool lexical = piece != nullptr && std::any_of(piece, piece + std::strlen(piece), [](unsigned char ch) {
                    return ch >= 0x80 || std::isalnum(ch);
                });
                if (token_id < whisper_token_eot(g_context) && lexical) {
                    text_sum += probability;
                    text_log_sum += std::log(std::max(1e-9f, probability));
                    text_min = std::min(text_min, static_cast<double>(probability));
                    weak_count += probability < 0.35f ? 1 : 0;
                    text_count++;
                }
            }
        }
    }
    if (probability_count > 0) {
        g_last_confidence = static_cast<float>(probability_sum / probability_count);
    }
    g_last_metrics[0] = g_last_confidence;
    if (text_count > 0) {
        g_last_metrics[1] = text_sum / text_count;
        g_last_metrics[2] = text_log_sum / text_count;
        g_last_metrics[3] = text_min;
        g_last_metrics[4] = static_cast<double>(weak_count) / text_count;
        g_last_metrics[5] = text_count;
    }
    g_last_metrics[8] = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started).count();
    text.erase(text.begin(), std::find_if(text.begin(), text.end(), [](unsigned char ch) { return !std::isspace(ch); }));
    text.erase(std::find_if(text.rbegin(), text.rend(), [](unsigned char ch) { return !std::isspace(ch); }).base(), text.end());
    return new_string_utf8(env, text);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_ru_nezabudka_app_speech_WhisperNative_detectSpeechSegments(
        JNIEnv *env,
        jobject,
        jstring vad_model_path_value,
        jstring wav_path_value,
        jint thread_count,
        jfloat max_speech_seconds) {
    const std::string vad_model_path = to_string(env, vad_model_path_value);
    const std::string wav_path = to_string(env, wav_path_value);
    std::vector<float> samples;
    std::string wav_error;
    if (vad_model_path.empty() || !read_mono_pcm16_wav(wav_path, samples, wav_error)) {
        throw_runtime(env, vad_model_path.empty() ? "Путь к модели Silero VAD пуст" : wav_error);
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    g_abort_requested.store(false, std::memory_order_relaxed);
    if (!ensure_vad_context(vad_model_path, static_cast<int>(thread_count))) {
        throw_runtime(env, "Не удалось загрузить модель Silero VAD");
        return nullptr;
    }
    whisper_vad_params params = whisper_vad_default_params();
    params.threshold = 0.5f;
    params.min_speech_duration_ms = 250;
    params.min_silence_duration_ms = 500;
    params.max_speech_duration_s = std::max(1.0f, static_cast<float>(max_speech_seconds));
    params.speech_pad_ms = 200;
    params.samples_overlap = 0.1f;
    whisper_vad_segments *segments = whisper_vad_segments_from_samples(
            g_vad_context, params, samples.data(), static_cast<int>(samples.size()));
    if (segments == nullptr) {
        throw_runtime(env, "Silero VAD не смог обработать аудиозапись");
        return nullptr;
    }

    const int count = whisper_vad_segments_n_segments(segments);
    std::vector<jlong> boundaries;
    boundaries.reserve(static_cast<size_t>(count) * 2U + 32U);
    for (int index = 0; index < count; ++index) {
        const double start = whisper_vad_segments_get_segment_t0(segments, index);
        const double end = whisper_vad_segments_get_segment_t1(segments, index);
        // VAD segment timestamps are centiseconds, while Kotlin groups PCM samples.
        boundaries.push_back(static_cast<jlong>(std::llround(start * 160.0)));
        boundaries.push_back(static_cast<jlong>(std::llround(end * 160.0)));
    }

    // Short, confident silence is a preferred split point, not discarded
    // audio. Kotlin receives it as a zero-length pair and can cut a long
    // fragment there while preserving every PCM sample around the pause.
    const int probability_count = whisper_vad_n_probs(g_vad_context);
    const float *probabilities = whisper_vad_probs(g_vad_context);
    if (probability_count > 0 && probabilities != nullptr) {
        const int frame_samples = std::max(1, static_cast<int>(
                (samples.size() + static_cast<size_t>(probability_count) - 1U) /
                static_cast<size_t>(probability_count)));
        const int minimum_pause_samples = 256 * 16;
        int pause_start = -1;
        for (int index = 0; index <= probability_count; ++index) {
            const bool quiet = index < probability_count && probabilities[index] < 0.20f;
            if (quiet && pause_start < 0) {
                pause_start = index;
            } else if (!quiet && pause_start >= 0) {
                const int pause_samples = (index - pause_start) * frame_samples;
                if (pause_samples >= minimum_pause_samples) {
                    const jlong midpoint = static_cast<jlong>(
                            (static_cast<int64_t>(pause_start) + index) * frame_samples / 2);
                    boundaries.push_back(midpoint);
                    boundaries.push_back(midpoint);
                }
                pause_start = -1;
            }
        }
    }
    whisper_vad_free_segments(segments);
    jlongArray result = env->NewLongArray(static_cast<jsize>(boundaries.size()));
    if (result != nullptr && !boundaries.empty()) {
        env->SetLongArrayRegion(result, 0, static_cast<jsize>(boundaries.size()), boundaries.data());
    }
    return result;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_ru_nezabudka_app_speech_WhisperNative_lastConfidence(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_last_confidence;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_ru_nezabudka_app_speech_WhisperNative_lastMetrics(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto result = env->NewDoubleArray(9);
    if (result != nullptr) env->SetDoubleArrayRegion(result, 0, 9, g_last_metrics);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_ru_nezabudka_app_speech_WhisperNative_cancel(JNIEnv *, jobject) {
    // Cancellation must remain lock-free while whisper_full() owns g_mutex.
    g_abort_requested.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_ru_nezabudka_app_speech_WhisperNative_release(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    free_whisper_context();
    free_vad_context();
    mallopt(M_PURGE, 0);
}

extern "C" JNIEXPORT jstring JNICALL
Java_ru_nezabudka_app_speech_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}
