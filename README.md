# On-Device LLM Chat (Android)

A ready-to-run Android app that runs a Large Language Model **fully on-device**
— no internet, no API keys, no data leaving the phone. It uses Google's
[MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
to run models such as **Gemma** locally, with a simple streaming chat UI built
in Jetpack Compose.

## Features

- 💬 Streaming chat interface (tokens appear as they are generated)
- 📴 100% offline inference on the device's CPU/GPU
- 🧩 Model-agnostic: any MediaPipe-compatible `.task` LLM bundle works
- 🎨 Material 3 UI with dynamic color (Android 12+)
- 🏗️ Modern stack: Kotlin, Jetpack Compose, MVVM, Coroutines

## Tech stack

| Layer      | Choice                                   |
|------------|------------------------------------------|
| Language   | Kotlin 2.0                               |
| UI         | Jetpack Compose + Material 3             |
| Inference  | `com.google.mediapipe:tasks-genai`       |
| Build      | Gradle 8.14 / AGP 8.7                     |
| Min / Target SDK | 24 / 35                            |

## Requirements

- Android Studio (Ladybug or newer recommended)
- A physical Android device is strongly recommended (on-device LLMs are slow or
  unsupported on many emulators). A device with **≥ 4 GB RAM** is a good target.
- A MediaPipe-compatible model file (see below).

## 1. Get a model

The app does **not** ship a model (they are hundreds of MB to several GB). Grab
a `.task` bundle, for example **Gemma** from the Google / LiteRT community:

- Kaggle: <https://www.kaggle.com/models/google/gemma-3> (look for the
  *LiteRT* / *MediaPipe* `.task` variants, e.g. `gemma3-1b-it-int4`)
- Hugging Face: <https://huggingface.co/litert-community>

Smaller, quantized models (e.g. a 1B int4/int8 build) run best on phones.

## 2. Push the model to the device

The app loads the model from `/data/local/tmp/llm/model.task` (the path used by
Google's official samples — writable via `adb` without root):

```bash
adb shell mkdir -p /data/local/tmp/llm
adb push your-model.task /data/local/tmp/llm/model.task
```

> Want a different path/name? Edit `MODEL_PATH` in
> [`InferenceModel.kt`](app/src/main/java/com/example/ondevicellm/InferenceModel.kt).

## 3. Build and run

Open the project in Android Studio and press **Run**, or from the command line:

```bash
./gradlew installDebug   # build + install on a connected device
# or
./gradlew assembleDebug  # just build the APK -> app/build/outputs/apk/debug/
```

On first launch the app loads the model (this can take a few seconds), then
you can start chatting.

## Project structure

```
app/src/main/java/com/example/ondevicellm/
├── MainActivity.kt        # Compose entry point
├── InferenceModel.kt      # MediaPipe LLM Inference wrapper (loads model, streams tokens)
├── ChatViewModel.kt       # MVVM state: messages, model status, send/reset
└── ui/
    ├── ChatScreen.kt      # Chat list + input + loading/error states
    └── theme/             # Material 3 theme
```

## How inference works

`InferenceModel` creates an `LlmInference` engine from the model file, then opens
an `LlmInferenceSession` configured with `topK` / `temperature`. Each user
message is added with `addQueryChunk(...)` and answered via
`generateResponseAsync { partial, done -> ... }`, which streams partial tokens
back to the UI. "New chat" (the refresh icon) resets the session to clear
conversation context. Tune `MAX_TOKENS`, `TOP_K`, and `TEMPERATURE` in
`InferenceModel.kt`.

## Troubleshooting

- **"No model found at …"** — the model was not pushed, or the path/name does
  not match `MODEL_PATH`. Re-run the `adb push` step.
- **App crashes / OOM on load** — use a smaller / more heavily quantized model,
  or a device with more RAM. `largeHeap` is already enabled.
- **Very slow generation** — expected for larger models on CPU; try a 1B int4
  build.

## License

Sample/starter code — use it however you like. Model files are subject to their
own licenses (e.g. the Gemma Terms of Use).
