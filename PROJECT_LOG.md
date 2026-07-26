# Project log

Working reference for this project: what exists, why it is the way it is, what
is verified, and what still isn't. Updated as work happens — read this first
before changing anything.

---

## 1. What this is

An Android app that runs LLMs **on the device**. Chat, reasoning traces, voice
in and out, a model manager, hardware-aware execution, and optional web
grounding. Targeted at high-end Qualcomm hardware (Galaxy S25 Ultra /
Snapdragon 8 Elite) but runs on any arm64 Android 7.0+ device.

- **Repo:** `Basio96547/Uhvfgk`
- **Branch:** `claude/delete-everything-k31c2n` (all work lives here; `main`
  still holds two unrelated legacy HTML files)
- **APK:** built by CI, published to the rolling `latest-debug` release

---

## 2. Current status

| Area | State |
|---|---|
| Kotlin/Compose app | ✅ Compiles, APK built and published |
| Unit tests | ✅ Passing in CI |
| MediaPipe `.task` path | ✅ Builds — **never run against a real model** |
| llama.cpp GGUF path | ✅ Bridge compiled, **linked and run against real llama.cpp** (`tools/verify-native.sh`); Android rebuild pending |
| Web search | ⚠️ Real organic results + page reading; parser tested — **never hit a live endpoint** |
| Thermal management | ⚠️ Logic tested — **never observed on real hardware** |
| Diagnostics/crash log | ⚠️ Compiles — **never triggered in anger** |

**Nothing has been run on a physical device yet.** Everything below marked ⚠️
is "correct by construction and unit tests" but unproven in practice.

### Last known CI result
Run 3 (`857ee6f`) failed: `llama_model_params` no longer has `use_mmap` /
`use_mlock` — replaced upstream by `load_mode`. Fixed by setting
`LLAMA_LOAD_MODE_MMAP`. llama.cpp itself compiled fine; only the bridge failed.

### Native bridge — actually verified, not assumed
`tools/verify-native.sh` clones the pinned llama.cpp, builds it, then:
1. compiles `llama_bridge.cpp` against the real headers (`-Wall`, clean);
2. **links** it — so a renamed or removed `llama_*` symbol fails here, which is
   exactly the class of break that cost two CI rounds;
3. **runs** the JNI entry points inside a real JVM: `nativeInit`, the
   null-handle guards, a missing model, and an empty path.

All pass at `7cdd557f76800b5a84ddee2bff6f20178a3e31fe`, which is now the pinned
commit. Tracking `master` is what broke run 3.

**Still not proven:** real inference. That needs a GGUF file, and
huggingface.co is unreachable from this sandbox.

---

## 3. Architecture

```
app/src/main/
├── cpp/                        Native: llama.cpp + JNI bridge (CMake FetchContent)
│   ├── CMakeLists.txt          Builds llama.cpp from source, CPU only
│   └── llama_bridge.cpp        Load / generate / stop / reset / set-threads
├── java/com/example/ondevicellm/
│   ├── OnDeviceLlmApp.kt       Application; arms ErrorLog + crash handler
│   ├── MainActivity.kt         Shell: header, crossfaded screens, bottom bar
│   ├── ChatViewModel.kt        All app state
│   ├── core/
│   │   ├── DeviceCapabilities  SoC/NPU probing, /proc/meminfo, RAM Plus
│   │   ├── ThermalGuard        Thermal status → thread budget
│   │   ├── SettingsStore       Persisted preferences
│   │   └── ErrorLog            Diagnostics + crash persistence
│   ├── model/
│   │   ├── ModelSpec           Metadata + JSON
│   │   ├── ModelFormat         Magic-byte detection — **routes the engine**
│   │   ├── ModelRegistry       Model library, selection, folder scan
│   │   ├── ModelImporter       SAF import, in-place registration
│   │   └── ModelHeuristics     Filename → type/rate guesses
│   ├── llm/
│   │   ├── TextEngine          Interface both runtimes implement
│   │   ├── EngineFactory       Picks runtime from magic bytes
│   │   ├── InferenceEngine     MediaPipe (.task)
│   │   ├── LlamaCppEngine      llama.cpp (.gguf) + JNI wrapper
│   │   ├── BackendResolver     CPU/GPU/NPU resolution + NpuRuntime stub
│   │   └── ThinkingStreamParser  Splits <think> from the answer
│   ├── audio/                  ASR in, TTS out, PCM/WAV, tokenizer
│   ├── web/                    DuckDuckGo + Wikipedia grounding
│   └── ui/                     Compose screens + shared components + theme
└── test/                       Unit tests for the pure logic
```

---

## 4. Decisions and why

Do not undo these without re-reading the reason.

**Two inference runtimes, chosen by magic bytes.** MediaPipe reads `.task`,
llama.cpp reads GGUF; the formats are mutually unreadable. Routing on the
filename would send a mislabelled file to the wrong engine, so `ModelFormat`
reads the first bytes. This is why `ModelFormat` is load-bearing and must not
be deleted as "just an error message".

**LiteRT is `compileOnly`.** Packaging a second TFLite runtime alongside the one
MediaPipe links internally risks duplicate-class and duplicate-`.so` failures.
Nothing from the artifact ships, so a conflict is impossible; custom TTS models
need one word changed to `implementation`. The app detects the missing runtime
and says so rather than crashing.

**No NPU execution, and the app says so.** MediaPipe exposes CPU and GPU only.
Real Hexagon execution needs Qualcomm's QNN/Genie SDK plus a model recompiled
to a QNN context binary. Selecting "NPU" runs on GPU and states the substitution
in the UI. `NpuRuntime` in `BackendResolver.kt` is the wiring point.

**Thread budget deliberately below core count.** Snapdragon 8 Elite has no
efficiency cores — all eight are big. Decode is memory-bandwidth bound, so past
~4–6 threads extra cores add heat, not speed, and the resulting throttle is
slower than never boosting. Hence 6/4/2 by thermal level, always leaving two
cores for UI and audio.

**RAM Plus counts toward the load budget.** Android reports Samsung's extended
memory as *swap*, not RAM. Weights are mmap'd, so pages genuinely can live
there. `effectiveAvailableBytes = availableRam + freeSwap`.

**Web search off by default.** It is the only feature that leaves the device.
Settings states plainly what is sent and where.

**Errors are reported, never swallowed.** 46 sites used to discard exceptions.
The crash handler records and then *delegates to the platform handler* — it
must not prevent the crash, only make it visible.

**No new dependencies for web/JSON.** `HttpURLConnection` + `org.json` are
platform APIs, so grounding can't conflict with the inference runtimes.

---

## 5. Change history

### Session 1 — 2026-07-26

1. **Wiped the repo** on request (two legacy HTML files removed).
2. **Base app**: MediaPipe LLM chat, Compose UI, Gradle wrapper generated
   locally (Gradle 8.14.3 was available in the sandbox).
3. **Hardware + models**: SoC/NPU probing, RAM Plus accounting, per-model
   backend, `<think>` parsing, voice input, model manager (import / adb path /
   folder scan).
4. **Text-to-speech**: `ModelKind.TTS` alongside `ASR` (legacy `AUDIO`
   migrates), system engine + LiteRT model engine, PCM/WAV pipeline, per-reply
   speak and save.
5. **Design system**: `Design.kt` (spacing, gradients, `Modifier.panel`), new
   palette, shared `Components.kt`, redesigned all four screens.
6. **Conflict-proofing**: LiteRT → `compileOnly`; explicit `getApplication<>()`;
   `Interpreter.Options().setNumThreads()`.
7. **CI**: `build-apk.yml` — tests, APK, rolling `latest-debug` release.
   - Run 1 ❌ missing Compose imports in `Components.kt` (`animateFloat`,
     `runtime.getValue`) → fixed
   - Run 2 ✅ APK published (33.3 MB)
8. **GGUF support**: llama.cpp built from source via CMake FetchContent, JNI
   bridge written against the *current* `llama.h` (fetched from GitHub, not
   guessed), `TextEngine` abstraction, `EngineFactory` routing.
   - Run 3 ❌ `use_mmap`/`use_mlock` removed upstream → fixed to `load_mode`
9. **Dead code removal**: audited every declaration; deleted 20 unused ones.
   Regex deletion silently ate `object PcmAudio`'s closing brace — caught by
   compiling, not by reading. Added a brace-balance check.
10. **Thermal + web search**: `ThermalGuard`, live `llama_set_n_threads`,
    sustained performance mode; DuckDuckGo + Wikipedia grounding with citations.
11. **Diagnostics**: `ErrorLog`, crash handler, `OnDeviceLlmApp`, diagnostics
    dialog, header badge; replaced silent catches. Added a **stop-generation**
    control that was missing entirely.
12. **This log.**
13. **Host-side native verification**: cloned and built real llama.cpp in the
    sandbox, compiled + linked + ran the bridge through a JVM, then pinned
    `LLAMA_CPP_TAG` to the verified commit and switched `GIT_SHALLOW` off
    (shallow fetch of a bare commit is unreliable). Saved as
    `tools/verify-native.sh`.
14. **Real web search**: the Instant Answer API returns definitions only, so
    most questions got nothing. Added `HtmlExtract` — a tested parser for
    DuckDuckGo's HTML endpoint (organic results, click-redirect unwrapping, ad
    filtering, entity decoding) plus readable-text extraction so `SearchDepth.DEEP`
    can open the top three pages and ground answers in real content. Globe
    toggle moved into the input bar; depth chips in Settings.

---

## 6. Known gaps

**Blocking**
- CI rebuild after the `load_mode` fix is unconfirmed.
- Nothing has run on a real device.

**Cannot be verified from this sandbox** (network policy blocks them; not
design choices):
- Live search — `html.duckduckgo.com`, `api.duckduckgo.com` and
  `*.wikipedia.org` all fail to connect. The parser is tested against captured
  markup shapes, not a live response.
- Real GGUF inference — `huggingface.co` is unreachable, so no model to load.
- Thermal behaviour and MediaPipe — device-only by nature.

**Fragile**
- MediaPipe API surface (`setPreferredBackend`, `setTopP`) is unexercised —
  compiles, but no model has been loaded through it. There is no host-side
  equivalent of `verify-native.sh` for it: MediaPipe ships as an Android AAR.
- `ModelTtsSynthesizer` assumes a VITS/Piper tensor layout; other exports are
  reported, not adapted.

**Fragile (continued)**
- Search scrapes HTML. DuckDuckGo can change its markup at any time; the parser
  accepts two class-name shapes and fails to zero results rather than crashing,
  and `HtmlExtractTest` will catch a regression once markup samples are updated.

**Not done**
- GGUF runs CPU-only; no GPU backend compiled into llama.cpp.
- No conversation persistence — history dies with the process.
- No multimodal input despite `ModelKind.MULTIMODAL` existing.
- `main` is still the old HTML; nothing merged.
- Repo/branch names (`Uhvfgk`, `delete-everything-…`) don't describe the project.

---

## 7. How to verify

**Native bridge** — `tools/verify-native.sh`. Compiles, links and runs the JNI
layer against real llama.cpp on the host. Run this before bumping
`LLAMA_CPP_TAG`; it catches API drift in one step instead of a CI round trip.

**Full build** — push; CI runs tests, builds the APK, publishes the release.

**Pure logic without a build** — the sandbox has no Android SDK, but Gradle
ships a Kotlin compiler that can check Android-free files:

```
/opt/gradle-8.14.3/lib/kotlin-compiler-embeddable-2.0.21.jar
```

Compile the pure sources against `kotlin-stdlib` with a stub `org.json` and run
a harness. This caught the `PcmAudio` brace bug that review missed.

**Brace balance across all files** — cheap guard after any scripted edit.

**Current unit test coverage**: thinking-tag streaming, PCM conversion, WAV
header (byte-exact vs RIFF), tokenizer, model-kind guessing, format detection
(including a file that only *claims* to be GGUF), thermal thread budgets,
search URL/context building, and DuckDuckGo HTML parsing.
