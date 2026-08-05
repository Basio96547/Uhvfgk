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
| MediaPipe `.task` path | ✅ Compiles against the real AAR in CI — **never run against a real model** |
| TTS model path | ⚠️ Tensor handling rewritten for real VITS/Piper layouts — **untested against an actual voice model** |
| llama.cpp GGUF path | ✅ Bridge compiled, linked and run against real llama.cpp (`tools/verify-native.sh`); **native lib builds and ships in the APK** — never run against a real model |
| Web search | ⚠️ Real organic results + page reading; parser tested — **never hit a live endpoint** |
| Thermal management | ⚠️ Logic tested — **never observed on real hardware** |
| Diagnostics/crash log | ⚠️ Compiles — **never triggered in anger** |
| Query routing (search/think) | ✅ 30 tests, incl. the reported "مرحبا" case |
| Token streaming (UTF-8) | ✅ Crash fixed and covered by 9 native assertions |
| Arabic localization | ✅ Whole UI + RTL; completeness enforced by the compiler and by `StringsTest` |
| Answer quality | ✅ Transcript, sampling and budget defects fixed; house rules shipped — **judged only by reading the code, never by reading a reply** |
| Studio | ✅ Builds and ships; extractor covered by 13 tests — **no page has ever been generated or rendered** |
| Cloud voice (Azure / ElevenLabs) | ⚠️ Request building covered by 17 tests — **no request has ever been sent; no key exists here** |
| Terminal | ⚠️ Real process execution; policy and paths covered by 30 tests — **never run on a device** |
| Tools / skills | ⚠️ Parser covered by 19 tests against real model output shapes — **no model has ever called one** |
| PDF reading | ⚠️ Chunking, selection, quality checks and page ranges covered by 52 tests — **no PDF has been opened here** |
| Arabic OCR | ⚠️ Cloud only, and the UI says so. **No offline option exists** — see PLAN 2f |

**Device status:** run once on a real Galaxy S25 Ultra with `Qwen3-4B-Q8_0`.
That run produced four bug reports — routing, a hard crash on Arabic output,
useless backend selection, and reasoning leaking into replies. All four are
fixed in session 2 below; **the fixes themselves have not been back on the
device yet.** Everything still marked ⚠️ is "correct by construction and unit
tests" but unproven in practice.

### CI history
| Run | Commit | Result |
|---|---|---|
| 1 | `32d0afd` | ❌ missing Compose imports |
| 2 | `aaa7d72` | ✅ APK 33.3 MB |
| 3 | `857ee6f` | ❌ `use_mmap`/`use_mlock` removed upstream |
| 4–5 | | ❌ |
| 6–7 | | ⏹ cancelled by newer pushes |
| 8 | `25e046a` | ✅ |
| 9 | `de0879c` | ✅ APK **38.7 MB** |
| 10 | `3220d23` | ✅ |
| 11 | `e3347f9` | ✅ |
| 12 | `91a0e1e` | ✅ the four device fixes |
| 13 | `2277bb5` | ❌ scripted edit ate half a string literal |
| 14 | `f2cccb3` | ❌ StringsTest needs kotlin-reflect, not a declared dep |
| 15 | `6adce14` | ✅ APK **38.8 MB** |
| 16 | `cd1d6cf` | ✅ |
| 17 | `4d68799` | ✅ the five answer-quality fixes |
| 18 | `8b9d9b1` | ⏹ cancelled by a newer push |
| 19 | `881ff16` | ✅ house rules + model advisor |
| 20 | `e7b1dda` | ✅ |
| 21 | `1fb9bcc` | ✅ Studio |
| 22–25 | | ✅, one ⏹ cancelled by a newer push |
| 26 | `5412a85` | ✅ proved the tensorflow-lite `implementation` flip causes no clash |
| 27 | `16fc614` | ✅ dead-code sweep |
| 28 | `063f1b5` | ✅ cloud voice + engine-picker fixes |
| 29 | `3c85d15` | ✅ terminal, tools, four bug fixes |
| 30 | `1197a58` | ✅ |
| 31 | `2c35971` | ✅ PDF reading; PDFBox resolves and links |
| 32 | `5d74ea9` | ✅ |
| 33 | `8626c45` | ✅ device fixes: composer, spoken text, search |
| 34 | `36822ad` | ✅ current — **OpenCL cross-compiles**; APK 52.4 → 54.9 MB |

Runs 13 and 14 are worth keeping in view: both were caused by the local checks
being *weaker* than CI, not by the code being wrong in some subtle way. A brace
count can't see a half-eaten string literal, and a harness with a jar on its
classpath that the build doesn't declare will pass anything. `tools/check-syntax.sh`
and `tools/run-tests.sh` close both gaps, and the second one was verified by
deleting the dependency and watching it fail the same way CI did.

The APK grew 33.3 → 38.7 MB when GGUF landed. That ~5.4 MB is
`libllamabridge.so` with llama.cpp statically linked — concrete evidence the
native build not only compiled but is packaged and shipping.

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
├── java/com/basel/ai/
│   ├── BaselAiApp.kt       Application; arms ErrorLog + crash handler
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
│   ├── studio/                 Prompt-to-page: extractor, prompt, session, store
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
11. **Diagnostics**: `ErrorLog`, crash handler, `BaselAiApp`, diagnostics
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

### Session 2 — 2026-07-26 · first real-device feedback

The app ran on an actual Galaxy S25 Ultra with `Qwen3-4B-Q8_0`. Four bugs came
back from that run. All four were real, and none of them were visible from the
sandbox.

15. **"مرحبا took half an hour and searched the web."** Every message got a web
    search *and* a full chain of thought, unconditionally. Added
    `llm/QueryRouter.kt`: a rule set that classifies a message as SOCIAL /
    SIMPLE / LOOKUP / REASONING and decides search, thinking and a token cap
    per turn. Deliberately rules, not a model call — routing must be instant
    and work offline, and a second inference pass to decide whether to run the
    first would cost more than it saves.
    - Handles Arabic properly: diacritics stripped, `أإآ→ا`, `ى→ي`, `ة→ه`.
    - `hasPhrase()` matches on word boundaries. Plain `contains` had put
      "قارن بين الاندرويد والايفون" into a web search, because "الان" (now) is
      a substring of "الاندرويد" (Android).
    - A greeting attached to a real question is still the question:
      "مرحبا، لماذا السماء زرقاء؟" reasons.
    - A greeting is capped at 200 tokens. `ALWAYS` overrides are ignored for
      greetings — forcing a chain of thought on "hello" is the bug itself.
    - `thinkingEnabled: Boolean` in settings became
      `thinkingMode: RoutingMode` (Auto/Always/Never), and `searchMode` joined
      it. The globe stays the master switch; the mode says *when*.
    - The decision's reason is printed above each reply, so routing is never a
      black box.
    - 21 tests in `QueryRouterTest`.

16. **The app crashed and closed after sending a message.** Root cause found in
    `llama_bridge.cpp`: `env->NewStringUTF(piece.c_str())` on each token. A BPE
    token is a run of *bytes*, not characters — Arabic letters are two bytes and
    the tokenizer splits them across pieces constantly. ART aborts the process
    on malformed Modified UTF-8, so the first split Arabic letter killed the
    app. Which is why it crashed on his messages and never in any test here.
    - Added `utf8_complete_prefix()`: only complete UTF-8 is emitted, an
      incomplete tail is held until the next token completes it.
    - The callback now takes `ByteArray`, decoded JVM-side as real UTF-8.
      `NewStringUTF` cannot represent 4-byte sequences at all, so emoji were a
      second crash waiting to happen.
    - Nine assertions in `tools/verify-native.sh` cover it, including the exact
      "مرحبا" split-byte case.
    - Fixed alongside: prompt ingestion decoded the whole prompt in one batch.
      Past `n_batch` (512) llama.cpp rejects it, and search grounding routinely
      pushes prompts well past that. Now chunked.

17. **"The processor selection is dumb — no priority for an S25 Ultra."**
    Correct on both counts.
    - **The real cause was in CMake, not in the picker.** Cross-compiling for
      Android with neither `GGML_CPU_ARM_ARCH` nor `GGML_CPU_ALL_VARIANTS` set,
      ggml adds *no* `-march` flags, so the NDK default applies: plain
      `armv8-a`. Every GGUF model was running the scalar fallback with SDOT
      unused — several times slower than what the chip can do. Now pinned to
      `armv8.2-a+dotprod+fp16`, the highest baseline safe for every device this
      app installs on (minSdk 24; dotprod is universal since 2018).
      `GGML_CPU_ALL_VARIANTS`, which would dispatch per-device, is unusable
      here: it emits versioned MODULE libraries (`libggml-cpu-*.so.N`) and an
      APK only packages plain `.so`.
    - `nativeCpuFeatures()` reports which SIMD kernels are actually live —
      compile-time *and* HWCAP — and it is shown on the model chip.
    - `BackendPlanner` replaces the old "is there a GPU driver? → GPU" answer.
      It weighs model size and free memory: a 4 GB bundle does not go on a
      phone GPU, it gets memory-mapped on the CPU. Pure Kotlin, 13 tests.

18. **Reasoning showed up inside the reply with thinking switched off.** The
    `<think>` parser was only constructed when thinking was enabled — but a
    model that ignores `/no_think` still emits the block, and with no parser it
    landed verbatim in the bubble. Both engines now parse unconditionally and
    discard the trace when reasoning wasn't asked for.

### Session 3 — 2026-07-26 · Arabic as a first-class language

19. **`core/Strings.kt`: every user-facing string, in both languages.** An
    interface with two implementing objects rather than `strings.xml` — a
    missing Arabic string is then a *compile error*, where a missing XML key is
    a crash on the device in the one language the author can't read. It is
    plain Kotlin with no Android imports, so `StringsTest` walks every property
    by reflection and fails on anything copied across untranslated or left in
    Latin script. That check is what makes "fully translated" a fact rather
    than a claim.
    - `AppLanguage` (Auto / العربية / English) in settings; each option written
      in its own language, because a picker that says "Arabic" in English is no
      use to the person who needs it.
    - `ProvideLocalization` overrides `LocalLayoutDirection` as well as the
      words, so the whole app mirrors — padding, rows, nav bar, bubbles.
    - `Localization.strings` is a global mirror for code below the UI. Engine
      load failures and search errors were exactly the messages appearing in
      English on the device, and they are raised far from any composable.
    - Enum labels (`ModelKind`, `BackendPref`, `TtsEngine`, `ThermalLevel`,
      `RoutingMode`, `SearchDepth`) took an `AppStrings` parameter. `QueryRouter`
      stopped returning an English sentence and returns the decision; the UI
      words it. `BackendPlanner` takes `AppStrings` and stays unit-testable —
      two tests assert an Arabic plan reaches the same decision and leaks no
      English prose.

20. **The router learned how people actually write Arabic.**
    - Arabic-Indic digits (٠-٩) and Persian (۰-۹) folded to ASCII. Samsung's
      Arabic keyboard produces them, so "احسب ٢٥ × ١٧" contained no ASCII digit
      and was routed as a plain question with no working shown.
    - Arithmetic is matched on digit-folded but *unstripped* text: `normalize()`
      removes `+` along with the rest of `\p{Punct}`, which hid "12 + 8".
    - Normalisation extended: ٱ, ئ, ؤ, گ/ک, ی, and «» quotation marks.
    - Phrase lists rewritten across dialects — Gulf (شلونك, شخبارك), Egyptian
      (ازيك, دلوقتي, ايه الاخبار), Levantine (شو الاخبار, هسه), Maghrebi
      (كي داير) — plus prayers and pleasantries (يعطيك العافية, ما قصرت,
      جزاك الله خير) that are greetings, not questions.

21. **Search follows the question's script, not the app's language.**
    `SearchQuery.isArabic` counts Arabic letters; a third of the letters is
    enough, because real Arabic questions carry Latin product names
    ("متى صدر Android 16") and "Android" alone outweighs the words around it.
    A single Arabic word inside an English sentence stays below the line. An
    Arabic question then gets ar.wikipedia.org, DuckDuckGo's `kl=xa-ar` region,
    **and an Arabic context block** — an English instruction block in front of
    an Arabic question pulls a small model into answering in English, so
    grounding was costing the user their language.

22. **The model is told to answer in Arabic.** `replyLanguageInstruction` leads
    the system prompt when Arabic is selected. Empty for English: telling a
    model to use its default wastes context.

### Session 4 — 2026-07-26 · answer quality

Device report: "it improved, but there's abnormal stupidity" — and, when
pressed, "it feels like it doesn't understand me". Not a crash, not a
truncation: comprehension. Five real defects were found, all in the same place
— what actually reaches the model.

24. **The assistant's turn was never closed.** Generation breaks on the
    end-of-turn token *without decoding it*, so the reply ran straight into the
    next user turn with no `<|im_end|>` between them. From turn two onward the
    model was reading a malformed transcript.
25. **The system block was re-emitted every turn.** The KV cache already held
    the conversation, so turn three read as a transcript that restarted twice
    in the middle. It goes in on the first turn only now — and again after a
    context-full restart, the only other time the cache is empty.
26. **Search results lived in the system prompt**, so they persisted for the
    rest of the conversation: turn three was still answering with turn one's
    pages. They ride with the user turn they belong to.
27. **No repetition penalty in the sampler chain at all** — llama.cpp's most
    common cause of a model looping or padding an answer with restatements.
28. **Thinking turns were capped at a plain turn's budget**, so the model could
    deliberate carefully and be cut off before saying anything. Thinking gets
    its own budget, the GGUF context floor went to 6144, and a reply truncated
    by the cap now says so rather than just ending — a silent truncation is
    indistinguishable from a stupid answer.

29. **`ModelAdvisor` — the honest answer to "it doesn't understand me".** Four
    billion parameters at Q8_0 is the ceiling, and no prompt work moves it. The
    one lever the user actually has is which weights they load, and the app
    never said so. The Device screen now computes, from the memory really free
    on that phone, which weight classes fit — and states the thing that matters
    most: **a bigger model at Q4_K_M understands more than a smaller one at
    Q8_0 for the same memory.** Going 8-bit → 4-bit costs a few percent;
    doubling the parameters is worth far more. It also notes that for Arabic
    the family matters as much as the size. Nine tests.

30. **House rules for the model (`assistantGuidance`).** The remaining lever,
    and the cheapest one. A frontier model works out how to conduct itself; a
    4B one does not, and most of the gap between an assistant that pads,
    confabulates and drifts and one that answers is a short set of imperative
    rules: answer what was asked, say when you don't know, never invent a
    fact or a number, keep it as short as the question allows, ask instead of
    guessing when the request is ambiguous, use what was said earlier, no
    opening pleasantries. Deliberately short — a long system prompt confuses a
    small model and eats the context the conversation needs. The Arabic version
    adds the rule that matters here: read every dialect, answer in simple MSA.
    On by default, shown in full in Settings rather than described, and four
    tests hold it to being a short rule list rather than prose.

### Session 5 — 2026-07-26 · Studio

31. **A coding studio, on the model of bolt.diy, as a fifth tab.** Describe a
    page, watch the model write it, see it run. Scoped to **one self-contained
    HTML file** — not a limitation dressed up as a feature, but the only kind
    of program a phone can build *and execute* with nothing installed: no
    toolchain, no package manager, no server. A WebView is a complete runtime
    that is already on the device.
    - `CodeExtractor` handles every shape a small model actually replies in:
      labelled fence, unlabelled fence, four backticks, prose around the block,
      raw HTML with no fence, and a fence left unterminated because the reply
      was truncated (the partial page is kept — it shows how far it got, which
      beats a blank screen). A bare `<div>` is wrapped into a real document,
      because a WebView renders a loose fragment as unstyled text in the corner
      and that reads as the model failing when it didn't. 13 tests.
    - **Studio turns are stateless.** The file *is* the conversation, so the
      session is reset and each request carries the current code plus the
      change. Threading it as a chat would put three copies of the page in the
      context after two edits, and the model would be editing a version whose
      top it could no longer see.
    - **The preview is sandboxed.** `loadDataWithBaseURL(null, …)` gives an
      opaque origin; network loads blocked; file and content access off. A page
      the model wrote is untrusted input. Blocking the network also makes the
      constraint honest rather than intermittent, and the system prompt tells
      the model there is no network so it stops reaching for a CDN.
    - Thinking is forced off for builds: a chain of thought here spends the
      whole budget before a single tag is written.
    - The preview and the code editor are pinned LTR, so an Arabic interface
      doesn't mirror a layout the model wrote for LTR.

### Session 6 — 2026-07-26 · sizing for the reference device

Report: the Studio project view is out of proportion on an S25 Ultra, and the
layouts should be tuned for that screen.

32. **What that screen actually is.** 411 × 891 dp. At 1440×3120 physical
    pixels it sounds enormous, but Samsung ships density 3.5, so in layout units
    it is exactly as *wide* as an ordinary phone and unusually *tall*. Designing
    for "a big screen" by widening things is the wrong instinct — the room is
    vertical. `ui/theme/Layout.kt` holds the arithmetic as plain functions, all
    derived from the real window size, with 14 tests.

33. **The navigation bar was overflowing, and I caused it.** Adding the Studio
    tab took the row from four items to five, at padding sized for four: 425 dp
    of a 395 dp row, so the last tab was pushed off the edge. Items now take
    equal weighted shares and the pill padding is computed from the width and
    the tab count. The test asserts the row fits *and* that each item still
    meets the 48 dp touch target — squeezed items fit and then get mistapped.

34. **Touch targets.** Header buttons were 40 dp, Studio toolbar buttons 38 dp,
    and the send/mic/search controls 44 dp — all under the platform minimum, and
    all reading small on the largest screen in the range. All 48 dp now.

35. **The preview was laying out at 980 px.** The real answer to "out of
    proportion": a WebView with no viewport meta tag uses a 980 px viewport and
    scales the result down, so a page written for a phone arrives looking like a
    shrunken desktop site. `ensureViewport` injects the tag when the model
    leaves it out — which it does often enough that relying on it is not a plan
    — and the WebView is pinned to device width with the system font scale
    ignored, since that belongs to the app's text, not to a layout the model
    sized itself.

36. **Bubble width is a proportion now.** 340 dp was 83% of this screen and 96%
    of a small one: the same constant read as roomy on one phone and
    edge-to-edge on another.

37. **The keyboard covered the input.** `enableEdgeToEdge()` draws behind the
    system bars, and nothing accounted for the IME. One `imePadding()` at the
    root lifts content and navigation together on every screen.

### Session 7 — 2026-07-26 · logo, coding rules, Arabic voice, and a real plan

38. **App icon.** The supplied «ذكاء الأمويين» medallion, cropped to a circle.
    The disc was found by measurement, not by eye: the source bounding box is
    670 × 687, and the extra 17 px is a drop shadow, so the crop anchors on the
    top edge and the measured diameter rather than the box. Supersampled 4× for
    a clean edge, inset 0.6% to shed the white fringe. Legacy PNGs at five
    densities plus an adaptive icon whose foreground sits at 70/108 — the
    masked viewport is 72 dp, and a full 72 puts the gold rim exactly on the
    boundary. Background is the lapis sampled from the logo's own ring.

39. **The Studio prompt now encodes how to build, not just what to output.**
    The rule that matters most: *every control must do what it looks like it
    does*. A model asked for a calculator will cheerfully emit buttons wired to
    nothing, and that is the single most common way a generated page looks
    finished and does nothing. Also: no placeholders or dummy data, handle the
    empty and wrong-input cases, build for a phone (44 px targets, flexbox, no
    fixed page width), and restraint — exactly what was asked, named for what
    it is, commented only where the reason isn't obvious. Six tests hold the
    prompt to it.

40. **Arabic voice.** The app called `setLanguage("ar-SA")` and took whatever
    fell out — routinely the oldest, most robotic voice installed, while a
    better one sat on the same phone unused. `VoicePicker` ranks installed
    voices by quality with a deliberate penalty for network-only ones, and
    Settings lets the user choose. 14 tests. **This is the immediate half of
    the answer; the model half is in `PLAN.md`, honestly — good Arabic TTS is
    Piper, which is ONNX and needs a phonemiser, not a `.tflite` drop-in.**

41. **`PLAN.md`.** An honest inventory of what is real, what is a façade, and
    what each gap costs. Three things are named as façades: GPU execution
    (offered, never used — llama.cpp's OpenCL backend is the largest real gain
    available), NPU (detected, never used, and the UI already says so), and
    custom TTS models (dead code — `compileOnly` means the path is unreachable
    in any shipped build).

### Session 8 — 2026-07-26 · dead-code sweep

42. **Swept the whole tree by analysis, not by memory.** 1426 declarations
    checked for references; the two that came back unreferenced (`MainActivity`,
    `BaselAiApp`) are named in the manifest, so nothing top-level was dead.
    The rot was in the categories a name-scan misses — fields, config, and one
    C++ struct member. **Every candidate was verified before deletion, and three
    were false positives:** `hexagonStubs` is read by `hexagonVersion`,
    `timeMillis` by `timestamp`, and `sidecarFor` by its own file. Deleting on
    the strength of the scan alone would have broken all three.

    Actually removed:
    - `Session::last_error` (C++) — shadowed by the `g_last_error` the code uses.
    - `ResolvedBackend.requested` — assigned twice, read nowhere.
    - `MemorySnapshot.isLowMemory` — never read; the budget is what decides.
    - `AppStrings.studioShare` — a string with no screen behind it.
    - `Layout.contentMaxWidth` — written for tablets, applied nowhere. Kept
      `navFitsComfortably` beside it, which is also test-only, because it names
      the invariant that actually regressed; `contentMaxWidth` solved a problem
      this app does not have.
    - `testInstrumentationRunner` and both `androidTestImplementation`
      dependencies — there is no `androidTest` source set.
    - Two unused imports; `GPU_MODEL_LIMIT_BYTES` made private.

23. **Chat bubbles use `TextDirection.Content`.** Direction comes from the text
    itself, so an Arabic reply reads right-to-left even with the interface in
    English, and a code block inside an Arabic conversation still reads
    left-to-right.

### Session 15 — 2026-07-27 · the app decides for itself

82. **`AutoPolicy`: eight fixed numbers replaced by decisions.** Every one of
    them had been chosen once, on no evidence, for a phone in no particular
    state — a 6144-token context whether there is 1 GB free or 8, deep search
    reading three full pages on a mobile plan, three tool steps (three whole
    generations) at 8% battery. None were wrong *settings*; they were the wrong
    kind of thing to settle in advance.

83. **The app was blind to two things it should never have been.** Thermal
    awareness has been there since early on; **battery and network had no
    representation at all.** So a thinking turn burned four cores for two
    minutes at 8%, and a scanned PDF uploaded forty page images over mobile
    data without asking. `PowerState` and `NetworkState` read them; metered
    versus unmetered is the distinction that matters, not Wi-Fi versus mobile,
    because a metered hotspot is a phone sharing its own plan.

84. **Every decision carries its reason, and Settings shows them.** An
    automatic system that cannot be asked "why did you do that" is just an
    opaque one, and everything else here is built on being able to find out.
    The last turn's decisions appear as sentences: "Two of eight cores —
    saving battery", "On mobile data — snippets only, to spare your allowance".

85. **An explicit choice always wins.** The policy is consulted for AUTO and
    nothing else. Someone who picks DEEP gets DEEP on mobile data — they said
    so, and they can see their own signal bar.

86. **AUTO on the backend finally means something.** It sat on the CPU by
    default; it now weighs whether a GPU device answered, how hot the phone is,
    and whether there is 20% memory headroom rather than a bare fit — landing
    exactly on the limit means the *next* allocation fails instead of this one.

87. **State was separated from its readers, and that was the real design fix.**
    `ThermalLevel`, `PowerSnapshot` and `Connection` lived beside the Android
    code that reads them, so the policy built on them could not be compiled
    without an Android runtime, let alone tested. A reading needs a `Context`;
    the *meaning* of a reading does not — and the meaning is what decides
    behaviour. `SearchDepth` moved out of the networking file for the same
    reason. 28 new tests, every threshold pinned rather than believed.

---

### Session 14 — 2026-07-27 · the app has a name

79. **Renamed to باسل Ai, all the way down.** Not only the label under the
    icon: the package, the `applicationId`, the directory tree, the Compose and
    XML themes, the Application class, the Gradle project, the CI artifact and
    the APK file name. 110 files rewritten, longest identifier first so a short
    form could never eat part of a long one.

    The part that would have failed silently: **JNI symbol names are derived
    from the package.** `Java_com_example_ondevicellm_llm_LlamaBridge_*` had to
    become `Java_com_basel_ai_llm_LlamaBridge_*`, in the C++ and in the
    verifier's own declarations, or every native call would resolve to nothing
    at runtime with a build that compiled perfectly. `tools/verify-native.sh`
    links and calls them, so it catches exactly this — and it passed.

    Moved with `git mv` rather than copy-and-delete, so the history of every
    file survives the rename.

80. **The APK file name changed with it**, to `BaselAI-latest.apk`. That is the
    one and only time it changes: the name is stable by design now, and the
    reason it is stable is that it stopped carrying anything that varies.

81. **The README's first paragraph was no longer true.** It said "no internet,
    no API keys, no data leaving the phone", and since then web search, the
    hosted voice and cloud OCR have all arrived. They are opt-in and off by
    default, which is a fair thing to say — but "no data leaving the phone" as
    an unqualified claim on the front page is not, so it now names the three
    exceptions instead.

---

### Session 13 — 2026-07-27 · the GPU, and six bugs found by review

71. **The OpenCL backend is compiled in.** `GGML_OPENCL=ON` with Adreno kernels
    embedded — embedded rather than loose `.cl` files, because loose kernels
    are looked up by path at runtime and an APK has no paths. OpenCL-Headers
    and the Khronos ICD loader are fetched and built from source: the NDK ships
    neither a `libOpenCL.so` to link against nor a `CL/cl.h` to include. The
    loader links statically, so nothing extra is packaged.

    Designed so the worst case is no change at all: `n_gpu_layers` stays 0
    unless the user picks GPU *and* a device answered, and a failed GPU load
    retries on the CPU rather than refusing the model. The UI now reads back
    what ggml registered and what the loader actually did, so "offloaded", "no
    device answered" and "the GPU refused this model" are three different
    messages instead of one guess. **Never run on a phone.**

72. **A redirect was hiding dangerous commands.** `CommandPolicy.classify`
    returned WRITES the moment it saw `>`, before the loop that marks `pm`,
    `settings` and `rm -rf` as DANGEROUS. So `rm -rf work > /dev/null` was
    classified as an ordinary write and ran with only the writes switch on —
    walking straight past the switch that exists to stop it. The redirect is a
    floor now, not an answer.

73. **Page numbers in Arabic did not parse.** `PageRange` called `toIntOrNull`
    on text that had not been digit-folded, and Kotlin's parser takes ASCII
    only. The model answers in the user's language, so it asks for `صفحة ٥` —
    both bounds came back null, the page was dropped, and the user was told "no
    such document" about a document and a page that both exist.

74. **A form feed in extracted text could shuffle a whole document.** Page text
    is stored separated by U+000C, and `tidy` never stripped control
    characters — which the quality check tolerates up to a third of. One stray
    form feed from a broken font map splits into an extra segment on load and
    misaligns every later page's text against its number, permanently. Control
    characters are now stripped first.

75. **"Attached" was reported when whole pages had gone in blank.** The notice
    checked whether the OCR *switch* was on, not whether OCR could run — and
    with the switch on and no key entered yet it cannot. Now it reports on the
    recognizer, which is the thing that either read the page or did not.

76. **The shell had a race between the terminal page and the model.** Both
    share one `Shell` and both dispatch on IO, so a `cd` from one could land
    between the other's `directory(workingDirectory)` and its `start()`,
    launching a process somewhere nobody asked for. Serialised with a mutex.

77. **CI run 34 proves the OpenCL build.** Not by its green tick alone — the
    APK went from 52.4 MB to 54.9 MB, and 2.5 MB is the size of the backend
    plus its embedded Adreno kernels. A silently skipped `if` would have
    changed nothing.

78. **A failed import left an empty directory behind for ever.** Asking for the
    target path creates the directory as a side effect, and the `null` stream
    path returned before the cleanup that only ran on a thrown exception.

---

### Session 12 — 2026-07-27 · the first real device report since session 2

65. **The composer had 171dp to type in, and I did that.** Adding the attach
    button made four 48dp targets share one row with the field: on a 411dp
    screen that leaves 171dp, about twenty Arabic characters before the text
    scrolls out from under you while you write. Field on its own row, actions
    beneath — 363dp. `Layout.composerFieldWidth` and its test pin the number so
    the next button cannot quietly take it back.

66. **The navigation bar sat between the composer and the keyboard.** The root
    already applies `imePadding`, so the bar was squeezed into the little
    vertical room left on the one screen where every line counts. Hidden while
    the keyboard is open.

67. **The speech engine was reading Markdown out loud.** This is most of what
    "the Arabic voice is annoying and inaccurate" actually is — not the voice
    mispronouncing Arabic, but the voice pronouncing `**` as "نجمة نجمة",
    spelling out every character of a URL, reading `[صفحة 3]` as brackets, and
    narrating a whole code block. `SpeechText` strips all of it, keeps Arabic
    punctuation because it is heard as pauses, and says "مقطع برمجي" in place
    of code rather than silence. 15 tests, each one something that was being
    said aloud.

68. **The result parser could not read DuckDuckGo's lite frontend at all.** The
    pattern required `class` before `href` and both double-quoted; lite writes
    `<a rel="nofollow" href="…" class='result-link'>` — neither. It matched
    nothing on a page full of results. Attributes are now parsed properly,
    order and quote style irrelevant.

69. **Search asks three ways and says why when all three fail.** POST to the
    HTML endpoint first, because a form post is what a browser sends and a bare
    GET is what a scraper sends; then GET; then lite. A page that fetches fine
    and parses to zero results now records its size and its opening in the
    diagnostics log — a challenge page and moved markup look identical from
    here otherwise.

70. **A turn can no longer leave the UI stuck busy.** Every path out of
    `sendMessage` now clears the flag in a `finally`. "It stopped suddenly" is
    exactly what a stuck `isBusy` looks like, and it needed the app killed to
    recover.

---

### Session 11 — 2026-07-27 · reading PDFs

57. **The text layer first, and only then the pixels.** PDFBox pulls the text
    layer page by page — one stripper pass per page rather than one for the
    document, because page boundaries are the citations a user checks and a
    single call returns one string with nothing to align them to. Sorting by
    position is on: without it a two-column paper comes out interleaved line by
    line and is worthless to a model and to a person alike.

58. **The check that decides everything: is this text, or does it merely look
    like text?** An empty page is the easy case. The one that matters is a PDF
    with subsetted fonts and no `ToUnicode` map: it yields *characters*, they
    are the wrong ones, extraction "succeeds", and the model answers from
    gibberish. `TextLayerQuality` measures the share of characters that carry
    meaning and the number of letter runs, so control codes, replacement
    characters and private-use glyphs fail the check and the page goes to OCR.

59. **Only the pages that need it get rendered.** Rasterising forty pages to
    OCR the two without a text layer turns a two-second import into a
    two-minute one. Rendering is the platform's `PdfRenderer` rather than
    PDFBox's software renderer — hardware-accelerated, more faithful, and
    already what every PDF viewer on the phone uses. The bitmap is filled white
    first: `PdfRenderer` paints only what the page paints, and an unpainted
    background stays transparent, which encodes to black in a JPEG and hands
    OCR a blank sheet.

60. **`DocumentIndex` is what makes this work at all.** A forty-page report is
    two hundred thousand characters; a 4B model has room for a few thousand.
    Chunks are page-aligned so a citation is checkable, selection is by
    distinct query-token coverage rather than raw hit count (a chunk repeating
    one word forty times is a table of contents), and the result is re-sorted
    into reading order — a model handed page 9 before page 2 narrates them in
    that order. A question with no content words, which is every "summarise
    this", falls back to the opening pages rather than returning nothing.

    Arabic goes through the same normaliser as the router: أ/إ/آ, ة/ه, ى/ي fold
    and diacritics go. Without it a word written two ordinary ways does not
    match itself and Arabic document search barely functions.

61. **Images are their own deliverable.** Embedded image objects are extracted
    whether or not OCR ran, because a diagram on a page with perfectly good
    text is still something that was asked for. Objects under 64 px a side are
    skipped: rules, icons and spacers are most of the image objects in a
    typical document and are never what anyone meant.

62. **Three document skills, plus the attachment path.** Attaching answers
    "what does this say about X" in one turn, which is what people do. The
    tools let the model *work*: `list_documents`, `read_pdf` with a page range,
    `search_pdf` to find which pages mention something. A model that can only
    be handed a document cannot go and look something up in it. `PageRange`
    parses what models actually emit — en dashes, `p. 5`, `صفحة 5`, `1, 4-6`,
    backwards ranges — and caps a request for the whole book, which is
    otherwise how one call blows the context window.

63. **Arabic OCR is cloud-only, and the switch says so.** ML Kit does not read
    Arabic; Tesseract does but is JitPack-only and could not be verified from
    here. Cloud Vision is one POST with a key and reads Arabic properly, so
    that is what is built — with the privacy cost stated above the key field
    and the missing offline option named on the switch itself rather than
    buried in a document nobody opens.

64. **A bug in my own page-range parser, caught by its test.** Prefixes were
    stripped shortest-first, so `page 5` became `age 5` and parsed as nothing.
    Longest first, and only one.

---

### Session 10 — 2026-07-27 · a terminal, and skills to go with it

48. **A real terminal.** `ProcessBuilder` against `/system/bin/sh`, its own page
    in the app, and the limits stated on the page rather than discovered: no
    root, Android's toybox rather than GNU coreutils, and each command its own
    process. `cd` is therefore interpreted by `Shell` instead of passed
    through, because a `cd` inside a process that is about to exit does
    nothing.

    Two details that are not decoration: stdout and stderr are drained on
    separate threads — reading one to the end first deadlocks the moment a
    command writes more to the other than the pipe buffer holds, which `find /`
    does immediately — and output is capped at 24 k characters, which is about
    the context window, not about memory.

49. **Skills, chosen from what a small model is bad at.** Not from what sounds
    impressive: it cannot multiply (`calc`), does not know the date and will
    invent one (`now`), knows nothing after its cut-off (`web_search`), cannot
    see the device it runs on (`device_info`), cannot remember between turns
    (`read_file` / `write_file` / `list_files`), and cannot do anything outside
    the conversation at all (`shell`).

50. **The tools section of the prompt is generated from the live registry.** A
    hand-written list goes stale the first time a tool is renamed, and the
    failure is silent — the model calls something that no longer exists and the
    turn dies unexplained. The registry is also rebuilt per turn from settings,
    so the model is never told about a tool the user has switched off.

51. **The parser accepts every shape a model actually emits.** There is no
    single format: Qwen writes `<tool_call>` tags, others fence it as ```json
    or emit a bare object, and one 4B model does all three across a
    conversation. Four vocabularies for "which tool", four for "with what",
    plus trailing commas, unquoted keys and single quotes — because a parser
    that accepts only the documented shape rejects most real calls, and a
    rejected call is not an error anyone sees, it is a turn that silently went
    back to guessing. 19 tests, including the ones that must **not** parse: a
    CSS block, an object with no tool name, ordinary prose.

52. **`MiniJson`, because `org.json` cannot be tested here.** The build asks
    for default return values rather than Robolectric, so every `org.json` call
    in a unit test silently answers null. Tool-call parsing is the single point
    of failure for the whole feature; it could not be the one part with no
    tests.

53. **Two shells would have made the page a demo.** The terminal page and the
    model's `shell` tool share one `TerminalSession`, so `cd logs` from the
    model leaves the user standing in that directory looking at the same
    output. The model's commands are tinted differently — on a page that mixes
    both, "who ran this" is the first thing you need.

54. **`CommandPolicy` refuses a short list and classifies the rest.** The
    request was that the model might need anything, and a terminal that runs
    nine approved commands is a menu. So there is no allowlist. There is a
    refusal list of things that hang the phone or destroy with no undo — a fork
    bomb needs no privileges — and everything else is classified READ_ONLY /
    WRITES / DANGEROUS so writes and system commands have their own switches.
    Worth saying plainly: the real protection is the sandbox. This runs as the
    app's uid, so `rm -rf /` is already permission-denied on nearly everything.

55. **Four bugs found by reading, and fixed.**
    - `WebSearchService.fetch` read the response with a single `read()`. A
      `BufferedReader` stops as soon as the socket has nothing already
      buffered, so a 50–150 KB results page arrived cut off mid-tag, the parser
      found nothing, and search failed as "no results" at random depending on
      timing. Now it loops to the cap.
    - `HtmlExtract` paired snippets with titles by list index. Anchors are
      dropped — ads, tracking links — and the snippet list does not lose the
      matching entry, so one sponsored link at the top shifted every snippet
      onto the wrong result. Now paired by document position, which cannot
      drift.
    - Pressing Stop did not stop MediaPipe: it has no cancel API, so the next
      token flipped the spinner back on and carried on appending text the user
      had stopped — and with `isBusy` already false they could send again,
      running two generations over one non-thread-safe session. A reply epoch
      now drops tokens that belong to nobody.
    - Loading model B while A was still loading reverted the UI to A and leaked
      B's native memory. `cancel()` was never going to help: `EngineFactory.load`
      blocks with no suspension point, so a cancelled load runs to completion
      and then assigns itself. A load generation now discards and closes the
      stale one.

56. **Six tabs, asserted rather than eyeballed.** Adding a tab is exactly the
    change that broke the navigation row before, so `LayoutTest` checks the
    sixth clears the 48 dp touch target on both reference sizes.

---

### Session 9 — 2026-07-26 · the hosted voice, and two bugs it uncovered

43. **A cloud voice, because it is the honest answer.** Asked for the most
    natural Arabic voice available, and told the offline promise could go, the
    answer is Azure Neural or ElevenLabs — nothing that runs on the phone is
    close. `CloudTts` builds the request (pure, unit-tested: SSML escaping, the
    signed-percentage rate, URL/headers, per-provider pre-flight validation),
    `CloudTtsSynthesizer` sends it. Both providers are asked for **raw 24 kHz
    PCM**, so no audio decoder was added to the app for this.

    Six documented Azure Arabic voices — Saudi, Egyptian, Emirati — because
    "Arabic" as one voice serves nobody. ElevenLabs takes a voice id from the
    user's own account.

    The cost is stated where the decision is made: **the text of every spoken
    reply leaves the device.** Off by default, the user's own key, and the
    privacy line sits above the fields rather than under them. The key field is
    masked with a reveal, because a key you cannot see is a key you cannot check
    for a bad paste. Setup mistakes are named — "enter the region", not a 401 —
    and an HTTP failure carries the provider's own error body, so a wrong key, a
    wrong region and a voice the account cannot use look different.

    **Never sent a request.** There is no key in the sandbox. The escaping, URL
    and body shape are tested; the account and the network are not.

44. **The chosen speech engine was never the one that spoke.** `prepare()` with
    no argument meant "the system default", so every internal call — speaking,
    saving audio — tore down the engine the user picked in Settings and rebuilt
    it as the default. The engine picker added last session therefore changed
    the setting and nothing else. It now remembers what was asked for
    explicitly, so the no-argument call reuses it.

45. **Settings listed no voices until something had spoken.** Enumerating voices
    reads a live `TextToSpeech`, and nothing had necessarily started one — so on
    a fresh launch the picker said "no voices installed" on a phone full of
    them, and after switching engines it showed the *old* engine's voices. The
    screen now starts the engine and re-reads when it reports in.

46. **PCM decoding covered.** `pcm16ToFloat` is new and load-bearing for the
    cloud path: a truncated response drops its odd trailing byte instead of
    reading past the end, and the round-trip through `floatToPcm16` is tested.

47. **One exemption list, not two.** `StringsTest` kept its "same word in both
    languages" set copied into two tests; the brand names Azure and ElevenLabs
    made that drift visible. Hoisted to one property.

---

## 6. Known gaps

**Blocking**
- Nothing has run on a real device. The app builds, installs and is packaged
  correctly; whether a model loads and generates is unknown.

**Cannot be verified from this sandbox** (network policy blocks them; not
design choices):
- Live search — `html.duckduckgo.com`, `api.duckduckgo.com` and
  `*.wikipedia.org` all fail to connect. The parser is tested against captured
  markup shapes, not a live response.
- Real GGUF inference — `huggingface.co` is unreachable, so no model to load.
- Thermal behaviour and MediaPipe — device-only by nature.

**Fixed after an audit of "does this actually work?"**
- `SCAN_DIRS` listed `/sdcard/Download`, which scoped storage forbids listing
  without `MANAGE_EXTERNAL_STORAGE` (and model files aren't media, so
  `READ_MEDIA_*` wouldn't help). It silently found nothing and read as "no
  models". Removed, and the message now says where files must come from.
- `ModelTtsSynthesizer` filled only single-element auxiliary inputs and left
  the rest null — but VITS/Piper declare `input_lengths` and a three-element
  `scales` vector, so it would have thrown on exactly the models it targets.
  Inputs are now matched by tensor **name** first, int64 token ids are
  supported, and no slot is ever left null.
- `ErrorLog` rewrote the whole file on every entry; one search emits a dozen
  INFO notes. Only ERROR/CRASH write immediately now, with a flush when the
  diagnostics view opens.

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
- The GPU path has never run on a phone. It compiles; that is all that is known.
- No PDF has been opened on a device; there is no Android here to open one.
- Arabic OCR needs a Cloud Vision key and a connection. There is no offline
  path, and PLAN 2f says exactly why and what would change it.
- No model has ever emitted a tool call into the parser. The shapes are tested
  against saved output; how *often* a 4B model calls a tool is unmeasured.
- The terminal has never run a command on a device — there is no Android here.
- The cloud voice has never sent a request — no key exists in the sandbox.
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
