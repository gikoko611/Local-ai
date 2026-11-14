package com.gikoko.gilocalai

/** Called for every chunk of generated text (UTF-8 bytes). Return false to stop generation. */
fun interface TokenCallback {
    fun onToken(bytes: ByteArray): Boolean
}

/** Thin JNI wrapper around llama.cpp (see cpp/gi_jni.cpp). */
class LlamaBridge {
    companion object {
        init {
            System.loadLibrary("gilocalai")
        }
    }

    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
    external fun nativeFree(handle: Long)
    external fun nativeCount(handle: Long, text: ByteArray): Int
    external fun nativeInfo(handle: Long): String
    external fun nativeStop()
    external fun nativeGenerate(
        handle: Long,
        prompt: ByteArray,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: TokenCallback
    ): Int
}
