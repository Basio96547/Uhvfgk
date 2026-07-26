# On-Device LLM Chat (Android)

An Android app that runs language models **fully on-device** — no internet, no
API keys, no data leaving the phone. Built around Google's
[MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android),
with a model manager, hardware-aware backend selection, reasoning ("thinking")
support, and voice input.

Tuned for high-end Qualcomm devices such as the **Galaxy S25 Ultra
(Snapdragon 8 Elite)**, but runs on any arm64 Android 7.0+ device.

## Features

| | |
|---|---|
| 💬 **Chat** | Streaming responses, fully offline |
| 🧠 **Thinking** | Detects `<think>…</think>` reasoning and shows it in a collapsible block |
| 🎙️ **Voice input** | On-device speech-to-text, editable before sending |
| 📦 **Model manager** | Add, configure, switch and delete models at runtime |
| ⚙️ **Backend control** | Per-model CPU / GPU / NPU / Auto selection |
| 📊 **Device screen** | SoC, NPU runtime detection, RAM **and RAM Plus** reporting |

## Screens

- **Chat** — conversation, reasoning traces, mic input, active-backend banner.
- **Models** — add models (file picker, path, or folder scan), edit per-model
  settings, load/delete.
- **Device** — hardware, accelerators, and live memory including extended memory.
- **Settings** — system prompt, thinking toggles, voice language.

---

## Hardware support

### Snapdragon 8 Elite / Galaxy S25 Ultra

The Device screen identifies the SoC (`SM8750`) and the S25 Ultra model
(`SM-S938x`), and probes the device for vendor accelerator runtimes: Vulkan,
OpenCL, NNAPI, and Qualcomm QNN / Hexagon libraries (including the Hexagon
architecture tag, e.g. `V79`).

### About the NPU — please read

**The bundled MediaPipe LLM runtime exposes two backends: CPU and GPU. There is
no public NPU backend in `tasks-genai`.**

Selecting **NPU** for a model therefore does this:

1. Detects whether a Qualcomm NPU runtime is actually present on the device.
2. Runs the model on the **GPU** (the fastest generally-available backend).
3. **Tells you plainly** in the chat banner that a substitution happened and why.

It never silently pretends to use the NPU.

Driving the Hexagon NPU for LLM inference requires Qualcomm's **AI Engine
Direct (QNN) / Genie** runtime from the Qualcomm AI Hub, plus a model compiled
into a QNN context binary for that specific Hexagon architecture — a different
SDK and a different model artifact, neither publicly available on Maven. The
integration point is already stubbed out for you:

```
app/src/main/java/com/example/ondevicellm/llm/BackendResolver.kt  →  object NpuRuntime
```

Wiring steps are documented in that file's KDoc.

### RAM Plus (extended memory)

Android reports Samsung's **RAM Plus** as *swap*, not physical RAM: `MemTotal`
stays at the physical size and the extra capacity appears under `SwapTotal`. The
app reads `/proc/meminfo` directly and:

- shows physical RAM, extended memory, and free extended memory on the Device
  screen;
- computes an **effective budget** = available RAM + free extended memory;
- uses that budget (not physical RAM alone) to decide whether a model can load,
  and explains the shortfall when it can't.

Model weights are memory-mapped, so pages genuinely can be backed by extended
memory instead of failing to allocate. Enable it in
**Settings › Device care › Memory › RAM Plus**.

---

## Getting a model

The app ships **no** model (they are hundreds of MB to several GB). Grab a
MediaPipe-compatible `.task` bundle:

- Hugging Face: <https://huggingface.co/litert-community>
- Kaggle: <https://www.kaggle.com/models/google/gemma-3> (LiteRT / MediaPipe variants)

Good starting points on a phone: a **1B–4B int4** build (e.g. `gemma3-1b-it-int4`).
For **reasoning**, look for Qwen3 or DeepSeek-R1 distill builds — the app
auto-flags models whose filename suggests reasoning, and you can toggle it
manually per model.

### Adding a model

Three ways, all on the **Models** screen:

1. **Add model** — file picker; copies into app-private storage (progress shown,
   cancellable).
2. **Path…** — register a file already on disk *in place*, no copy. Use with adb:
   ```bash
   adb shell mkdir -p /data/local/tmp/llm
   adb push your-model.task /data/local/tmp/llm/model.task
   ```
3. **Scan** — auto-discovers bundles in `/data/local/tmp/llm` and `Downloads`.

Per model you can set: type (Text / Audio / Multimodal), backend, max tokens,
temperature, top-K, and whether it emits reasoning.

---

## Voice input

Uses Android's speech recognizer with `EXTRA_PREFER_OFFLINE`, so with an offline
language pack installed transcription stays on-device. Results land in the text
field so you can edit before sending. Set the language tag (`ar-SA`, `en-US`, …)
in Settings.

For a fully self-contained ASR model instead, register an **Audio** model on the
Models screen and implement the `AudioTranscriber` interface in
`audio/SpeechInput.kt`.

---

## Build and run

Requirements: Android Studio (Ladybug+), a **physical arm64 device**
(x86_64 emulators are unsupported — `tasks-genai` ships arm64 only).

```bash
./gradlew installDebug    # build + install
./gradlew assembleDebug   # APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest
```

## Project structure

```
app/src/main/java/com/example/ondevicellm/
├── MainActivity.kt           Bottom-nav shell (Chat / Models / Device / Settings)
├── ChatViewModel.kt          App state: models, chat, voice, memory
├── core/
│   ├── DeviceCapabilities.kt SoC + accelerator probing, /proc/meminfo & RAM Plus
│   └── SettingsStore.kt      Persisted preferences
├── model/
│   ├── ModelSpec.kt          Model metadata + JSON serialization
│   ├── ModelRegistry.kt      Persistent model list, selection, folder scan
│   └── ModelImporter.kt      SAF import with progress; in-place registration
├── llm/
│   ├── InferenceEngine.kt    MediaPipe wrapper, memory guard, streaming
│   ├── BackendResolver.kt    CPU/GPU/NPU resolution + NpuRuntime hook
│   └── ThinkingStreamParser.kt  Splits <think> reasoning from the answer
├── audio/SpeechInput.kt      On-device speech-to-text + AudioTranscriber hook
└── ui/                       Compose screens
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Model file not found" | Re-push the model, or re-add it on the Models screen |
| "not readable by this app" | Re-import through **Add model** so it lives in app storage |
| "Not enough memory" | Enable RAM Plus, close background apps, or use a smaller quantized model |
| Runtime rejects the model | Ensure it's a MediaPipe `.task` bundle, and try the CPU backend |
| Very slow generation | Expected for larger models; try a 1B int4 build on GPU |
| No reasoning shown | Enable *Emits reasoning* for that model, and *Show reasoning* in Settings |

## Notes on verification

The `ThinkingStreamParser` (the trickiest logic — tags split across streaming
chunks) is covered by unit tests in
`app/src/test/java/com/example/ondevicellm/llm/ThinkingStreamParserTest.kt`.

## License

Sample/starter code — use it however you like. Model files are subject to their
own licenses (e.g. the Gemma Terms of Use).
