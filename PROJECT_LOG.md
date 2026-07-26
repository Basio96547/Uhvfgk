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
| 21 | `1fb9bcc` | ✅ current — Studio; APK **38.9 MB** |

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

23. **Chat bubbles use `TextDirection.Content`.** Direction comes from the text
    itself, so an Arabic reply reads right-to-left even with the interface in
    English, and a code block inside an Arabic conversation still reads
    left-to-right.

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
