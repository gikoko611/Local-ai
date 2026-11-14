#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <cstdint>
#include <algorithm>
#include <chrono>
#include <string>
#include <vector>
#include <initializer_list>
#include "llama.h"

#define TAG "GILocalAI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct Engine {
    llama_model * model = nullptr;
    int n_ctx = 2048;
    int n_threads = 4;
};

static std::atomic<bool> g_stop{false};
static bool g_backend_ready = false;

// Moves complete UTF-8 sequences out of `pending` and returns them.
// Incomplete trailing bytes stay in `pending` (important for Burmese: one glyph can span several tokens).
static std::string drain_utf8(std::string & pending) {
    std::string out;
    size_t i = 0;
    const size_t n = pending.size();
    while (i < n) {
        unsigned char c = (unsigned char) pending[i];
        size_t len = 0;
        if (c < 0x80) len = 1;
        else if (c >= 0xC2 && c <= 0xDF) len = 2;
        else if (c >= 0xE0 && c <= 0xEF) len = 3;
        else if (c >= 0xF0 && c <= 0xF4) len = 4;
        else { i++; continue; } // invalid lead byte, drop it
        if (i + len > n) break;  // incomplete tail, wait for more bytes
        bool ok = true;
        for (size_t k = 1; k < len; k++) {
            if (((unsigned char) pending[i + k] & 0xC0) != 0x80) { ok = false; break; }
        }
        if (ok) out.append(pending, i, len);
        i += ok ? len : 1;
    }
    pending.erase(0, i);
    return out;
}

static std::string bytes_to_string(JNIEnv * env, jbyteArray arr) {
    jsize len = env->GetArrayLength(arr);
    std::string s((size_t) len, '\0');
    if (len > 0) env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte *>(&s[0]));
    return s;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_gikoko_gilocalai_LlamaBridge_nativeLoad(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    if (!g_backend_ready) {
        llama_backend_init();
        g_backend_ready = true;
    }
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.use_mmap = true;
    LOGI("Loading model: %s", path);
    llama_model * model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) {
        LOGE("Model load failed");
        return 0;
    }
    Engine * e = new Engine();
    e->model = model;
    e->n_ctx = n_ctx;
    e->n_threads = n_threads;
    return reinterpret_cast<jlong>(e);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gikoko_gilocalai_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    Engine * e = reinterpret_cast<Engine *>(handle);
    if (!e) return;
    if (e->model) llama_model_free(e->model);
    delete e;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gikoko_gilocalai_LlamaBridge_nativeStop(JNIEnv *, jobject) {
    g_stop = true;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_gikoko_gilocalai_LlamaBridge_nativeCount(JNIEnv * env, jobject, jlong handle, jbyteArray jtext) {
    Engine * e = reinterpret_cast<Engine *>(handle);
    if (!e) return 0;
    std::string text = bytes_to_string(env, jtext);
    if (text.empty()) return 0;
    const llama_vocab * vocab = llama_model_get_vocab(e->model);
    int n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, false, true);
    return n < 0 ? -n : n;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gikoko_gilocalai_LlamaBridge_nativeInfo(JNIEnv * env, jobject, jlong handle) {
    Engine * e = reinterpret_cast<Engine *>(handle);
    if (!e) return env->NewStringUTF("");
    char desc[256] = {0};
    llama_model_desc(e->model, desc, sizeof(desc));
    char buf[768];
    snprintf(buf, sizeof(buf), "%s | %.2fB params | ctx %s%d | threads %d | %s",
             desc, llama_model_n_params(e->model) / 1e9, e->n_ctx <= 0 ? "AUTO (model/RAM) " : "", e->n_ctx, e->n_threads, llama_print_system_info());
    return env->NewStringUTF(buf);
}

// Returns number of generated tokens, or a negative error code:
// -1 tokenize failed, -2 tokenize failed, -3 prompt too long, -4 context creation failed (RAM), -5 decode failed
extern "C" JNIEXPORT jint JNICALL
Java_com_gikoko_gilocalai_LlamaBridge_nativeGenerate(JNIEnv * env, jobject, jlong handle, jbyteArray jprompt,
                                                      jint max_tokens, jfloat temp, jfloat top_p, jint top_k,
                                                      jfloat repeat_penalty, jobject callback) {
    Engine * e = reinterpret_cast<Engine *>(handle);
    if (!e || !e->model) return -1;
    g_stop = false;

    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_cls, "onToken", "([B)Z");
    if (!on_token) return -1;

    const llama_vocab * vocab = llama_model_get_vocab(e->model);
    std::string prompt = bytes_to_string(env, jprompt);

    int n_prompt = -llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    if (n_prompt <= 0) return -1;
    std::vector<llama_token> toks((size_t) n_prompt);
    if (llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), toks.data(), n_prompt, true, true) < 0) return -2;

    // Auto mode (n_ctx <= 0) starts from the model's trained limit, capped at 2048
    // for low-memory phones. If allocation fails, progressively try smaller KV caches.
    const int trained_ctx = (int) llama_model_n_ctx_train(e->model);
    int requested = e->n_ctx > 0 ? e->n_ctx : std::min(trained_ctx > 0 ? trained_ctx : 2048, 2048);
    if (trained_ctx > 0) requested = std::min(requested, trained_ctx);
    const int candidates[] = { requested, 1024, 768, 512, 384, 256 };
    llama_context * ctx = nullptr;
    llama_context_params cp = llama_context_default_params();
    int n_ctx = 0;
    int batch_sz = 64;
    for (int candidate : candidates) {
        if (candidate <= 0 || candidate > requested || candidate < n_prompt + 8) continue;
        if (n_ctx == candidate) continue;
        for (int batch : {64, 32, 16}) {
            cp = llama_context_default_params();
            cp.n_ctx = (uint32_t) candidate;
            cp.n_batch = (uint32_t) std::min(candidate, batch);
            cp.n_ubatch = (uint32_t) std::min((int) cp.n_batch, 32);
            cp.n_threads = e->n_threads;
            cp.n_threads_batch = e->n_threads;
            ctx = llama_init_from_model(e->model, cp);
            if (ctx) {
                n_ctx = candidate;
                batch_sz = (int) cp.n_batch;
                LOGI("Context selected automatically: ctx=%d batch=%d trained=%d", n_ctx, batch_sz, trained_ctx);
                break;
            }
        }
        if (ctx) break;
    }
    if (!ctx) return -4;
    const int max_new = std::min((int) max_tokens, n_ctx - n_prompt - 1);

    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (repeat_penalty > 1.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, repeat_penalty, 0.0f, 0.0f));
    }
    if (temp <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        if (top_k > 0) llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
        if (top_p > 0.0f && top_p < 1.0f) llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
        uint32_t seed = (uint32_t) std::chrono::steady_clock::now().time_since_epoch().count();
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(seed));
    }

    int result = 0;
    for (int i = 0; i < n_prompt; i += batch_sz) {
        int cnt = std::min(batch_sz, n_prompt - i);
        llama_batch b = llama_batch_get_one(toks.data() + i, cnt);
        if (llama_decode(ctx, b) != 0) { result = -5; break; }
        if (g_stop) break;
    }

    if (result == 0 && !g_stop) {
        std::string pending;
        int generated = 0;
        for (int i = 0; i < max_new && !g_stop; i++) {
            llama_token id = llama_sampler_sample(smpl, ctx, -1);
            if (llama_vocab_is_eog(vocab, id)) break;

            char piece[256];
            int len = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, false);
            if (len > 0) pending.append(piece, (size_t) len);

            std::string out = drain_utf8(pending);
            if (!out.empty()) {
                jbyteArray arr = env->NewByteArray((jsize) out.size());
                env->SetByteArrayRegion(arr, 0, (jsize) out.size(), reinterpret_cast<const jbyte *>(out.data()));
                jboolean cont = env->CallBooleanMethod(callback, on_token, arr);
                env->DeleteLocalRef(arr);
                if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
                if (!cont) break;
            }

            llama_batch nb = llama_batch_get_one(&id, 1);
            if (llama_decode(ctx, nb) != 0) break;
            generated++;
        }
        result = generated;
    }

    llama_sampler_free(smpl);
    llama_free(ctx);
    return result;
}
