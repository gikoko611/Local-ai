# GI Local AI

Offline GGUF chat app for Android (Kotlin + Jetpack Compose + llama.cpp), built for Redmi 9A (armeabi-v7a, 4 GB RAM).

## Features
- Load any GGUF model (file picker, or scan the phone), streamed chat, stop button
- Dark-green cyber UI, animated 3D "GI" splash, 3D GI launcher icon
- **Auto-learn** (the model weights are not retrained; knowledge is stored locally and given to the model as notes):
  - Learn from books/files: TXT, MD, CSV, JSON, HTML, PDF, EPUB, DOCX
  - Learn from web pages and Wikipedia (Burmese + English), automatically when it does not know the answer
  - Learn from chat: "remember ...", "my name is ...", corrections; save good answers with the 💾 button
  - Burmese <-> English glossary (seeded, grows from Wikipedia language links, editable)
  - Search works for Burmese (character bigram BM25), no extra model needed
- Settings: temperature, top-p/k, repeat penalty, context size, threads, chat template (ChatML/Llama 3/Gemma/Plain), system prompt

## Build with GitHub Actions
```
cd GILocalAI
git init
git add .
git commit -m "GI Local AI"
git branch -M main
git remote add origin https://github.com/gikoko611/GILocalAI.git
git push -u origin main
```
The workflow (.github/workflows/build.yml) clones llama.cpp (tag b5200), builds, and uploads
`GILocalAI.apk` under Actions -> latest run -> Artifacts. No local Android Studio needed.

## Using it
1. Settings -> "Pick .gguf file" (or "Scan phone"). Qwen models use the ChatML template.
2. Chat. Learn tab to feed books / web / notes.
3. If the app is slow: context 2048, 4 threads, a Q4 model (Q8 of a 0.5B model works too).

## Notes
- Android 10 phones read /storage/emulated/0/... directly (legacy storage). On other devices the picker is used and
  the model may be copied once into app storage.
- Only armeabi-v7a is built. For a 64-bit phone add "arm64-v8a" to abiFilters in app/build.gradle.kts.
- If you change llama.cpp version, the JNI file app/src/main/cpp/gi_jni.cpp may need small API updates.
