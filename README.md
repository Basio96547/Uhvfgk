# On-Device LLM Chat (Android)

An Android app that runs language models **fully on-device** — no internet, no
API keys, no data leaving the phone. Built around Google's
[MediaPipe LLM Inference API](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android),
with a model manager, hardware-aware backend selection, reasoning ("thinking")
support, and both voice input and voice output.

Tuned for high-end Qualcomm devices such as the **Galaxy S25 Ultra
(Snapdragon 8 Elite)**, but runs on any arm64 Android 7.0+ device.

## Features

| | |
|---|---|
| 💬 **Chat** | Streaming responses, fully offline |
| 🧠 **Thinking** | Detects `<think>…</think>` reasoning and shows it in a collapsible block |
| 🎙️ **Voice input** | On-device speech-to-text, editable before sending |
| 🔊 **Voice output** | Speak replies with the system engine **or your own TTS model**; save as WAV |
| 📦 **Model manager** | Add, configure, switch and delete models at runtime |
| ⚙️ **Backend control** | Per-model CPU / GPU / NPU / Auto selection |
| 📊 **Device screen** | SoC, NPU runtime detection, RAM **and RAM Plus** reporting |

## Screens

- **Chat** — conversation, reasoning traces, mic input, per-reply speak/save
  buttons, active-backend banner.
- **Models** — add models (file picker, path, or folder scan), edit per-model
  settings, load/delete. Chat, ASR and TTS models each get their own slot.
- **Device** — hardware, accelerators, and live memory including extended memory.
- **Settings** — reasoning, speech output engine/speed/pitch, voice language,
  system prompt.

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

Per model you can set its type (Text / Speech → Text / Text → Speech /
Multimodal). Chat models add backend, max tokens, temperature, top-K and a
reasoning flag; TTS models add sample rate and speaker id.

The type is guessed from the filename when a model is added (`whisper-*` → ASR,
`kokoro-*`/`piper-*`/`vits-*` → TTS, `*-3n`/`*-vl` → multimodal) and is always
editable.

---

## Voice input (speech → text)

Uses Android's speech recognizer with `EXTRA_PREFER_OFFLINE`, so with an offline
language pack installed transcription stays on-device. Results land in the text
field so you can edit before sending. Set the language tag (`ar-SA`, `en-US`, …)
in Settings.

For a fully self-contained ASR model instead, register a **Speech → Text** model
on the Models screen and implement the `AudioTranscriber` interface in
`audio/SpeechInput.kt`.

---

## Voice output (text → speech)

Every reply gets a 🔊 button; there is also **Speak replies automatically** in
Settings. Two engines:

**System engine (default).** Android's built-in TTS. Works with no extra setup
and stays offline once a voice pack is installed. Honours speed and pitch.

**Your own TTS model.** Add a model on the Models screen, set its type to
**Text → Speech**, then pick *My TTS model* in Settings. Audio is generated with
LiteRT, normalized, and played through `AudioTrack`.

> **This needs one word changed first.** The LiteRT dependency ships as
> `compileOnly` so that nothing from it is packaged and it can never clash with
> the TFLite runtime MediaPipe links internally — the app builds and runs with
> zero conflicts out of the box. To run your own TTS model files, open
> `app/build.gradle.kts` and change
> `compileOnly("org.tensorflow:tensorflow-lite:2.16.1")` to
> `implementation(...)`, then rebuild. The app detects the runtime and tells you
> exactly this if it is missing, instead of crashing.

### What a TTS model must look like

`ModelTtsSynthesizer` expects the common VITS/Piper-style export:

| Tensor | Type | Shape | Meaning |
|---|---|---|---|
| input 0 | `int32` | `[1, T]` | token ids |
| input 1..n *(optional)* | `int32` / `float32` | 1 element | speaker id / speaking rate |
| output 0 | `float32` | `[1, N]` or `[N]` | mono waveform in ~[-1, 1] |

Two per-model settings matter:

- **Sample rate** — must match what the model was trained to output, or speech
  plays too fast or too slow. Guessed from the filename for known families
  (Kokoro 24 kHz, Piper/VITS 22.05 kHz, SpeechT5 16 kHz); editable under *Tune*.
- **Vocabulary** — put a sidecar JSON next to the model
  (`voice.tflite` → `voice.tokens.json`):
  ```json
  { "pad": 0, "bos": 1, "eos": 2, "vocab": { "a": 3, "b": 4, " ": 5 } }
  ```
  Without it the app falls back to a generated Latin+Arabic vocabulary, which
  only sounds right if it happens to match the model.

If a model's signature doesn't match, the app says so and dumps the actual
tensor layout instead of playing noise.

### Saving audio

The ⬇ button next to a reply renders it to a WAV file in the app's external
files directory and shows the path.

---

## Build and run

Requirements: Android Studio (Ladybug+), a **physical arm64 device**
(x86_64 emulators are unsupported — `tasks-genai` ships arm64 only).

```bash
./gradlew installDebug    # build + install
./gradlew assembleDebug   # APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest
```

### Why this build has no dependency conflicts

Running two on-device inference runtimes in one app is the usual source of
duplicate-native-library and duplicate-class failures. This project avoids that
by construction:

| Choice | Reason |
|---|---|
| Only **one** runtime is packaged — MediaPipe `tasks-genai` | Nothing else can clash with it |
| LiteRT is `compileOnly` | Its classes and `.so` files are never packaged |
| `pickFirsts` on the TFLite `.so` names | A no-op today; pre-empts a duplicate if you later switch LiteRT to `implementation` |
| `abiFilters = ["arm64-v8a"]` | The only ABI `tasks-genai` ships; avoids "missing library" failures on other ABIs |
| All dependency versions pinned explicitly | No surprise transitive upgrades between builds |

Speech output works out of the box through the system TTS engine, which is a
platform API with no dependency at all.

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
├── audio/
│   ├── SpeechInput.kt        Speech-to-text + AudioTranscriber hook
│   ├── SpeechSynthesizer.kt  Text-to-speech contract (options, results)
│   ├── SystemTtsSynthesizer.kt  Android TTS engine
│   ├── ModelTtsSynthesizer.kt   LiteRT-backed TTS model runner
│   ├── TtsTokenizer.kt       Vocabulary handling + sidecar loading
│   ├── AudioPlayer.kt        AudioTrack PCM playback
│   └── PcmAudio.kt           PCM conversion, normalize, resample, WAV writer
└── ui/                       Compose screens + shared components
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
| Speech plays too fast/slow | The model's **sample rate** is wrong — fix it under *Tune* |
| TTS output is gibberish | Supply a `<name>.tokens.json` vocabulary next to the model |
| "signature doesn't match" | The model isn't a VITS/Piper-style export; adapt `ModelTtsSynthesizer` |
| No sound at all | Check the engine in Settings; *My TTS model* needs a TTS model selected |
| "LiteRT runtime isn't bundled" | Expected by default — use the system engine, or switch `compileOnly` to `implementation` (see above) |

## Notes on verification

The logic that is easiest to get subtly wrong is covered by unit tests:

- `ThinkingStreamParserTest` — `<think>` tags split across streaming chunks.
- `PcmAudioTest` / `WavWriterTest` — PCM clipping and a byte-exact RIFF header.
- `CharacterTokenizerTest` — vocabulary mapping and sidecar path derivation.
- `ModelHeuristicsTest` — model-type guessing, including names that match both
  the ASR and TTS families.

```bash
./gradlew testDebugUnitTest
```

## License

Sample/starter code — use it however you like. Model files are subject to their
own licenses (e.g. the Gemma Terms of Use).
