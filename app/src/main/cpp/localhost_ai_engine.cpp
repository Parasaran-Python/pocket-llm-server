#include <android/log.h>
#include <jni.h>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <unistd.h>

#include "chat.h"
#include "common.h"
#include "llama.h"
#include "sampling.h"

#define TAG "LocalHostAI-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void custom_llama_log_callback(ggml_log_level level, const char * text, void * /*user_data*/) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: LOGE("%s", text); break;
        case GGML_LOG_LEVEL_WARN:  LOGW("%s", text); break;
        case GGML_LOG_LEVEL_INFO:  LOGI("%s", text); break;
        default:                   LOGD("%s", text); break;
    }
}

// Global engine state
static std::mutex                       g_mutex;
static llama_model                    * g_model = nullptr;
static llama_context                  * g_context = nullptr;
static llama_batch                      g_batch;
static common_chat_templates_ptr        g_chat_templates;
static std::atomic<bool>                g_is_generating(false);
static std::atomic<bool>                g_cancel_requested(false);

static bool is_valid_utf8(const char *string) {
    if (!string) return true;
    const auto *bytes = (const unsigned char *) string;
    int num = 0;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C" {

JNIEXPORT void JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeInit(
        JNIEnv *env,
        jobject /*thiz*/,
        jstring nativeLibDir
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    llama_log_set(custom_llama_log_callback, nullptr);

    if (nativeLibDir != nullptr) {
        const char *lib_dir = env->GetStringUTFChars(nativeLibDir, nullptr);
        LOGI("Loading dynamic backends from: %s", lib_dir);
        ggml_backend_load_all_from_path(lib_dir);
        env->ReleaseStringUTFChars(nativeLibDir, lib_dir);
    } else {
        ggml_backend_load_all();
    }

    llama_backend_init();
    LOGI("LocalHostAI Native Backend Initialized successfully.");
}

JNIEXPORT jstring JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeGetSystemInfo(
        JNIEnv *env,
        jobject /*thiz*/
) {
    return env->NewStringUTF(llama_print_system_info());
}

JNIEXPORT jobjectArray JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeGetAvailableBackends(
        JNIEnv *env,
        jobject /*thiz*/
) {
    std::vector<std::string> backends;
    size_t count = ggml_backend_reg_count();
    for (size_t i = 0; i < count; i++) {
        auto *reg = ggml_backend_reg_get(i);
        if (reg) {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    if (backends.empty()) {
        backends.push_back("CPU (ARM NEON / KleidiAI)");
    }

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(backends.size(), stringClass, nullptr);
    for (size_t i = 0; i < backends.size(); i++) {
        jstring str = env->NewStringUTF(backends[i].c_str());
        env->SetObjectArrayElement(array, i, str);
        env->DeleteLocalRef(str);
    }
    return array;
}

JNIEXPORT jint JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeLoadModel(
        JNIEnv *env,
        jobject /*thiz*/,
        jstring jmodelPath,
        jint nGpuLayers,
        jint nCtx,
        jint nThreads
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model != nullptr) {
        LOGI("Cleaning up existing model before loading new one...");
        if (g_context) {
            llama_free(g_context);
            g_context = nullptr;
        }
        llama_batch_free(g_batch);
        g_chat_templates.reset();
        llama_model_free(g_model);
        g_model = nullptr;
    }

    const char *model_path = env->GetStringUTFChars(jmodelPath, nullptr);
    LOGI("Loading model from: %s | GPU Layers requested: %d | Context: %d | Threads: %d",
         model_path, nGpuLayers, nCtx, nThreads);

    // Model parameters
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;

    auto *model = llama_model_load_from_file(model_path, model_params);

    // If GPU loading failed (or GPU OOM), attempt graceful fallback to CPU
    if (!model && nGpuLayers > 0) {
        LOGW("Failed to load model with %d GPU layers. Attempting CPU fallback...", nGpuLayers);
        model_params.n_gpu_layers = 0;
        model = llama_model_load_from_file(model_path, model_params);
    }

    env->ReleaseStringUTFChars(jmodelPath, model_path);

    if (!model) {
        LOGE("Failed to load model from file.");
        return 1;
    }

    // Hardware thread optimization: if nThreads <= 0, auto-detect
    int actual_threads = nThreads;
    if (actual_threads <= 0) {
        int procs = (int) sysconf(_SC_NPROCESSORS_ONLN);
        actual_threads = std::max(2, std::min(6, procs - 2));
    }

    // Context parameters
    llama_context_params ctx_params = llama_context_default_params();
    int trained_ctx = llama_model_n_ctx_train(model);
    int actual_ctx = (nCtx > 0) ? std::min(nCtx, trained_ctx) : std::min(4096, trained_ctx);

    ctx_params.n_ctx = actual_ctx;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = actual_threads;
    ctx_params.n_threads_batch = actual_threads;

    auto *context = llama_init_from_model(model, ctx_params);
    if (!context) {
        LOGE("Failed to create llama context from model.");
        llama_model_free(model);
        return 2;
    }

    g_model = model;
    g_context = context;
    g_batch = llama_batch_init(512, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");

    LOGI("Model successfully loaded! Context: %d, Threads: %d", actual_ctx, actual_threads);
    return 0;
}

JNIEXPORT void JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeUnloadModel(
        JNIEnv * /*env*/,
        jobject /*thiz*/
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancel_requested = true;

    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    llama_batch_free(g_batch);
    g_chat_templates.reset();
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    LOGI("Model unloaded and resources freed.");
}

JNIEXPORT jboolean JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeIsModelLoaded(
        JNIEnv * /*env*/,
        jobject /*thiz*/
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_context != nullptr);
}

JNIEXPORT jstring JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeGetModelMetadata(
        JNIEnv *env,
        jobject /*thiz*/
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) {
        return env->NewStringUTF("{}");
    }

    char desc[256];
    llama_model_desc(g_model, desc, sizeof(desc));
    uint64_t n_params = llama_model_n_params(g_model);
    uint64_t size_bytes = llama_model_size(g_model);
    int n_ctx_train = llama_model_n_ctx_train(g_model);

    std::ostringstream ss;
    ss << "{"
       << "\"description\":\"" << desc << "\","
       << "\"params_count\":" << n_params << ","
       << "\"size_bytes\":" << size_bytes << ","
       << "\"context_train\":" << n_ctx_train
       << "}";

    return env->NewStringUTF(ss.str().c_str());
}

JNIEXPORT jstring JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeFormatChat(
        JNIEnv *env,
        jobject /*thiz*/,
        jobjectArray roles,
        jobjectArray contents,
        jboolean addGenerationPrompt
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) {
        return env->NewStringUTF("");
    }

    jsize count = env->GetArrayLength(roles);
    std::vector<common_chat_msg> msgs;
    for (jsize i = 0; i < count; i++) {
        auto *jrole = (jstring) env->GetObjectArrayElement(roles, i);
        auto *jcontent = (jstring) env->GetObjectArrayElement(contents, i);

        const char *role_str = env->GetStringUTFChars(jrole, nullptr);
        const char *content_str = env->GetStringUTFChars(jcontent, nullptr);

        common_chat_msg msg;
        msg.role = role_str;
        msg.content = content_str;
        msgs.push_back(msg);

        env->ReleaseStringUTFChars(jrole, role_str);
        env->ReleaseStringUTFChars(jcontent, content_str);
        env->DeleteLocalRef(jrole);
        env->DeleteLocalRef(jcontent);
    }

    common_chat_templates_inputs inputs;
    inputs.messages = msgs;
    inputs.add_generation_prompt = addGenerationPrompt;
    inputs.use_jinja = true;

    auto res = common_chat_templates_apply(g_chat_templates.get(), inputs);
    return env->NewStringUTF(res.prompt.c_str());
}

JNIEXPORT void JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeCancelGeneration(
        JNIEnv * /*env*/,
        jobject /*thiz*/
) {
    LOGI("Generation cancellation requested.");
    g_cancel_requested = true;
}

JNIEXPORT jstring JNICALL
Java_pyhon_pro_localhost_1ai_engine_LocalAiEngine_nativeGenerate(
        JNIEnv *env,
        jobject /*thiz*/,
        jstring jprompt,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jobjectArray stopWords,
        jobject callback
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context) {
        return env->NewStringUTF("{\"error\":\"Model is not loaded\"}");
    }

    g_is_generating = true;
    g_cancel_requested = false;

    // Get Callback Method ID
    jclass callbackClass = nullptr;
    jmethodID onTokenMethod = nullptr;
    if (callback != nullptr) {
        callbackClass = env->GetObjectClass(callback);
        onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;Z)Z");
    }

    // Stop words
    std::vector<std::string> stops;
    if (stopWords != nullptr) {
        jsize stop_count = env->GetArrayLength(stopWords);
        for (jsize i = 0; i < stop_count; i++) {
            auto *jstop = (jstring) env->GetObjectArrayElement(stopWords, i);
            const char *stop_str = env->GetStringUTFChars(jstop, nullptr);
            stops.push_back(stop_str);
            env->ReleaseStringUTFChars(jstop, stop_str);
            env->DeleteLocalRef(jstop);
        }
    }

    // Clear KV cache for clean generation
    llama_memory_clear(llama_get_memory(g_context), false);

    // Tokenize Prompt
    const char *prompt_str = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_str);
    env->ReleaseStringUTFChars(jprompt, prompt_str);

    auto prompt_tokens = common_tokenize(g_context, prompt, true, true);
    int n_prompt = (int) prompt_tokens.size();
    LOGI("Starting generation for prompt of %d tokens. Max tokens: %d, Temp: %.2f",
         n_prompt, maxTokens, temperature);

    int ctx_size = llama_n_ctx(g_context);
    if (n_prompt >= ctx_size - 4) {
        LOGE("Prompt tokens (%d) exceed context size (%d)", n_prompt, ctx_size);
        g_is_generating = false;
        return env->NewStringUTF("{\"error\":\"Prompt exceeds model context size\"}");
    }

    // Setup Sampler
    common_params_sampling sparams;
    sparams.temp = temperature;
    sparams.top_p = topP;
    auto *sampler = common_sampler_init(g_model, sparams);

    // Prompt Processing Benchmark
    const int64_t t_prompt_start = ggml_time_us();

    // Decode Prompt in batches
    llama_pos current_pos = 0;
    for (int i = 0; i < n_prompt; i += 512) {
        if (g_cancel_requested) break;
        int cur_batch_size = std::min(n_prompt - i, 512);
        common_batch_clear(g_batch);
        for (int j = 0; j < cur_batch_size; j++) {
            llama_token token = prompt_tokens[i + j];
            llama_pos pos = current_pos + j;
            bool want_logit = (i + j == n_prompt - 1);
            common_batch_add(g_batch, token, pos, {0}, want_logit);
        }
        if (llama_decode(g_context, g_batch) != 0) {
            LOGE("llama_decode failed during prompt processing at position %d", current_pos);
            common_sampler_free(sampler);
            g_is_generating = false;
            return env->NewStringUTF("{\"error\":\"Failed to evaluate prompt\"}");
        }
        current_pos += cur_batch_size;
    }

    const int64_t t_prompt_end = ggml_time_us();

    // Text Generation Loop
    int n_generated = 0;
    int max_gen = (maxTokens > 0) ? maxTokens : (ctx_size - current_pos - 4);
    std::string accumulated_output;
    std::string cached_token_chars;

    const int64_t t_gen_start = ggml_time_us();

    while (n_generated < max_gen && current_pos < ctx_size - 4) {
        if (g_cancel_requested) {
            LOGI("Generation cancelled by user.");
            break;
        }

        llama_token new_token_id = common_sampler_sample(sampler, g_context, -1);
        common_sampler_accept(sampler, new_token_id, true);

        // Check for End of Generation token
        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
            LOGD("EOS token reached: %d", new_token_id);
            break;
        }

        // Decode new token into KV cache
        common_batch_clear(g_batch);
        common_batch_add(g_batch, new_token_id, current_pos, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) {
            LOGE("llama_decode failed on generated token at pos %d", current_pos);
            break;
        }

        current_pos++;
        n_generated++;

        // Convert token to text piece
        std::string piece = common_token_to_piece(g_context, new_token_id);
        cached_token_chars += piece;

        if (is_valid_utf8(cached_token_chars.c_str())) {
            accumulated_output += cached_token_chars;

            // Check custom stop words
            bool stop_hit = false;
            for (const auto &stop : stops) {
                if (!stop.empty() && accumulated_output.rfind(stop) != std::string::npos) {
                    stop_hit = true;
                    break;
                }
            }
            if (stop_hit) {
                LOGI("Custom stop sequence hit.");
                break;
            }

            // Stream token chunk to callback
            if (callback != nullptr && onTokenMethod != nullptr) {
                jstring jchunk = env->NewStringUTF(cached_token_chars.c_str());
                jboolean cont = env->CallBooleanMethod(callback, onTokenMethod, jchunk, JNI_FALSE);
                env->DeleteLocalRef(jchunk);
                if (!cont) {
                    LOGI("Callback requested stop.");
                    break;
                }
            }
            cached_token_chars.clear();
        }
    }

    const int64_t t_gen_end = ggml_time_us();

    // Final callback invocation with isFinished = true
    if (callback != nullptr && onTokenMethod != nullptr) {
        jstring emptyStr = env->NewStringUTF("");
        env->CallBooleanMethod(callback, onTokenMethod, emptyStr, JNI_TRUE);
        env->DeleteLocalRef(emptyStr);
    }

    common_sampler_free(sampler);
    g_is_generating = false;

    // Calculate speed metrics
    double prompt_time_sec = (double)(t_prompt_end - t_prompt_start) / 1000000.0;
    double gen_time_sec = (double)(t_gen_end - t_gen_start) / 1000000.0;
    double prompt_speed = (prompt_time_sec > 0) ? (n_prompt / prompt_time_sec) : 0.0;
    double gen_speed = (gen_time_sec > 0) ? (n_generated / gen_time_sec) : 0.0;

    LOGI("Generation Finished: Prompt %d tokens in %.2fs (%.1f t/s) | Gen %d tokens in %.2fs (%.1f t/s)",
         n_prompt, prompt_time_sec, prompt_speed, n_generated, gen_time_sec, gen_speed);

    std::ostringstream res_json;
    res_json << std::fixed << std::setprecision(2);
    res_json << "{"
             << "\"prompt_tokens\":" << n_prompt << ","
             << "\"completion_tokens\":" << n_generated << ","
             << "\"total_tokens\":" << (n_prompt + n_generated) << ","
             << "\"prompt_time_sec\":" << prompt_time_sec << ","
             << "\"gen_time_sec\":" << gen_time_sec << ","
             << "\"prompt_speed_tps\":" << prompt_speed << ","
             << "\"gen_speed_tps\":" << gen_speed
             << "}";

    return env->NewStringUTF(res_json.str().c_str());
}

} // extern "C"
