# Plan: make every part of this real

A running list of what genuinely works, what is a façade, and what it would
take to close each gap. Ordered by **value ÷ effort**, not by what is pleasant
to say.

The rule for this document: nothing is called "done" because it compiles.
Something is done when it has been *run* — on the device, against a real model,
producing a real result. Everything else says what is still unproven.

---

## Where things actually stand

| Part | Truth |
|---|---|
| Chat, routing, streaming | Real. Fixed against a real device report. |
| GGUF via llama.cpp | Real code, built and shipped. **Never run against a model by me.** |
| MediaPipe `.task` | Real code. Never run. |
| Arabic UI + RTL | Real, and the completeness is machine-checked. |
| Web search | Real scraper, tested parser. **Never hit a live endpoint** — the sandbox blocks DuckDuckGo and Wikipedia. |
| Studio | Real. Never generated a page. |
| Thermal management | Real API calls. Never observed throttling. |
| System speech | Engine and voice are chosen deliberately now. **Never heard by me.** |
| **GPU execution** | **Backend compiled in.** OpenCL builds; offload happens when the user picks GPU and a driver answers. **Never run on a device.** |
| **NPU execution** | **Façade, and honest about it.** Detected, reported, never used. |
| Custom TTS models | Runtime is packaged now, so the path is reachable. **Never run against a real voice model** — and good Arabic voices are ONNX, not `.tflite` (see 2c). |
| Cloud voice | Real code, real endpoints, real request format. **Never sent a request** — no key in the sandbox. |
| Terminal | Real `ProcessBuilder` against `/system/bin/sh`. **Never run on a device** — no Android here to run it on. |
| Tools / skills | Real registry, real parser, real execution path. **No model has ever emitted a call into it.** |
| PDF text | Real PDFBox extraction, real chunking and selection. **No PDF has been opened by me** — there is no Android here. |
| PDF images | Real: embedded objects pulled out, pages rendered by the platform renderer. Never run. |
| **Arabic OCR offline** | **Not available.** Cloud only, and the reason is below. |

---

## 1. GPU acceleration for GGUF — built, unproven

**Done at the build level.** `GGML_OPENCL=ON` with the Adreno kernels embedded,
and OpenCL-Headers plus the Khronos ICD loader fetched and built from source
because the NDK ships neither a `libOpenCL.so` to link against nor a `CL/cl.h`
to include. The loader is static, so there is nothing extra to package.

**What actually changes at runtime:** the app reads back the devices ggml
registered rather than guessing, offloads only when the user picks GPU *and* a
device answered, and a GPU load that fails retries on the CPU instead of
refusing the model. AUTO stays on the CPU until this has been proven on real
hardware.

**Still unproven, and this is the important part.** Nothing here has run on a
phone. Adreno OpenCL drivers vary by vendor build; some refuse quantisations
the CPU path handles. The three outcomes the UI can now tell apart — offloaded,
no device answered, the GPU refused this model — exist precisely because I
cannot tell which one a given phone will give.

**Why it matters more than anything else here:** llama.cpp has an OpenCL
backend written specifically for Adreno, and the S25 Ultra's Adreno 830 is
idle during every decode. This is the difference between a phone answering at
reading speed and answering slowly, and it is the one lever that does not
require the user to download anything.

**What it takes:**
- `GGML_OPENCL=ON`, plus OpenCL-Headers and OpenCL-ICD-Loader via FetchContent
  — the NDK ships no `libOpenCL.so`, so the loader has to be built and the
  vendor driver resolved at runtime.
- `n_gpu_layers` becomes meaningful: offload as many layers as VRAM allows.
- Fall back to CPU cleanly when no driver answers, and *say which one ran*.

**Risk:** real. Adreno OpenCL drivers vary, and a bad build fails at load
rather than at compile. Mitigation: the CPU path stays the default until the
GPU path has run on the device once.

**Verification:** `tools/verify-native.sh` extended to build the OpenCL
backend; CI proves it compiles; only the device proves it runs.

---

## 2. Arabic speech that sounds human — two separate problems

The request was a clearer, more human Arabic voice. There are two things in
the way, and only one of them is about models.

### 2a. The voice already on the phone is probably not the one being used

Android exposes many voices per language, at different quality tiers, some
network-only. The app calls `setLanguage(ar-SA)` and takes whatever default
falls out — which on most phones is the oldest, most robotic Arabic voice
installed.

**Done.** `VoicePicker` ranks installed voices by quality with a deliberate
penalty for network-only ones, and Settings exposes both the **engine** and the
**voice**. The engine matters more: phones ship a vendor engine as the default
while a better one sits installed beside it, and the app was never choosing.

### 2b. The custom-model path cannot run at all

`ModelTtsSynthesizer` is complete and unreachable: `tensorflow-lite` is
`compileOnly`, so nothing is packaged and `isRuntimeAvailable()` returns false
forever. The app tells the user to edit `build.gradle.kts` — which is not a
feature, it is a note to a developer left in a shipped product.

**Done.** Flipped to `implementation`. The caution had cost the whole feature:
a precaution that silently removes something the user asked for on day one is
worse than the conflict it guards against. The packaging rule handles the
duplicate `.so`; if duplicate *classes* appear, exclude them rather than going
back. CI is the referee.

### 2c. The honest problem with Arabic TTS models

Even with the runtime enabled, good Arabic voices are not `.tflite`:

- **Piper** has genuinely good Arabic (`ar_JO-kareem-medium`) — but it is
  **ONNX**, and it needs espeak-ng to turn Arabic text into phonemes. That is
  a second native dependency, and it is the real reason this is not a
  one-afternoon job.
- **MMS-TTS Arabic** (Meta) is VITS and covers Arabic, but wants romanised
  input for Arabic script, which is another preprocessing stage.

### 2d. The hosted voices, which are the actual answer today

Asked to give up the offline promise for a voice that sounds human, the honest
answer is that Azure Neural and ElevenLabs are markedly better at Arabic than
anything that runs on the phone, and no amount of work on 2a–2c closes that gap
this year.

**Done, with the cost stated.** `CloudTts` builds the request, `CloudTtsSynthesizer`
sends it and plays raw PCM back — no audio decoder, because both services will
return PCM directly. Six documented Azure Arabic voices (Saudi, Egyptian,
Emirati) plus any ElevenLabs voice id.

What it costs, said in Settings rather than buried here: **the text of every
spoken reply leaves the device.** So it is off by default, needs the user's own
key, and the privacy line sits above the fields rather than under them.

**Still unproven:** no request has ever been sent. The escaping, the URLs, the
SSML shape and the rate conversion are unit-tested; the account, the key and
the network are not.

**So the plan is ordered:** 2a done, 2b done, 2d done and honest about its
price, 2c as a deliberate project — ONNX Runtime plus a phonemiser — not a
checkbox.

---

## 2e. Tools, and the one thing that will decide whether they work

The terminal and the skills around it are built: a shell, files, search, a
clock, arithmetic, device facts, all described to the model from the live
registry so the prompt cannot drift from the code.

**The risk is not the tools. It is whether a 4B model calls them at all.**
Tool use is the capability that degrades fastest as models get smaller: a 4B
model will call a tool when the example is right in front of it and then, three
turns later, answer from memory instead. The mitigations that are in:

- Every call shape a model actually emits is parsed, not just the documented
  one — `<tool_call>` tags, fenced JSON, a bare object, four vocabularies for
  "name" and "arguments", trailing commas, single quotes.
- The house rules name the specific failure: never guess a value a tool can
  give you.
- A failed call comes back as a labelled failure, not silence, so the model
  can try something else instead of inventing a result.

**What is not measured:** how often it actually calls one, on a real model. A
device session should count tool calls attempted, parsed and succeeded. Until
that number exists, "the model can use tools" means the app can, not that it
does.

---

## 2f. Reading PDFs, and the one honest gap in it

The pipeline is ordered by what is cheap and exact before what is slow and
approximate:

1. **The text layer**, via PDFBox. Almost every PDF that was produced rather
   than photographed has one; it is exact, free, and works in Arabic with none
   of OCR's caveats. This covers most real documents.
2. **A quality check per page.** Not only "is it empty" — a page whose fonts
   are subsetted with no `ToUnicode` map extracts as control codes or unrelated
   glyphs. Extraction "succeeds" and the model is handed gibberish it answers
   from confidently. That page is treated as an image.
3. **Render and read only those pages.** Rasterising all forty to OCR the two
   that needed it turns a two-second import into a two-minute one.
4. **Embedded images are pulled out regardless**, because a diagram on a page
   with perfectly good text is still something that was asked for.

Then `DocumentIndex` decides what actually reaches the model: a forty-page
report is two hundred thousand characters and a 4B model has room for a few
thousand, so the document is chunked page-aligned and the chunks relevant to
the question are selected. A question with no content words — "summarise this"
— falls back to the opening pages, because that is the right answer for
exactly those questions.

### The gap: there is no offline Arabic OCR here

Stated plainly because it is the one part that is not what this app wants to
be:

- **ML Kit** ships Latin, Chinese, Japanese, Korean and Devanagari. **Not
  Arabic.** Shipping it would mean an offline setting that works for everyone
  except the people this app is for.
- **Tesseract** does read Arabic well, via `tesseract4android`. It is
  **JitPack-only** — not on Maven Central — so adding it means adding JitPack
  to the repository list, and it could not be verified from here at all. It
  also needs `ara.traineddata` fetched at runtime.
- **Cloud Vision** reads Arabic properly, is one synchronous POST with an API
  key, and needs no SDK. That is what is built.

So OCR costs a key and a network today. The offline path is a real project —
JitPack, a native AAR, and a language file to manage — not a checkbox, and it
is on the list below rather than pretended at in the UI.

---

## 3. Prove the pieces that are written but unproven

Nothing new to build; these need a device and a model.

- **Web search** against a live endpoint. The parser is tested against saved
  HTML, which proves the parsing and nothing about DuckDuckGo's current markup.
- **Real inference** on a GGUF model: tokens per second, memory, whether the
  KV-cache fixes hold across a long conversation.
- **Thermal** behaviour under a sustained decode — does the thread budget
  actually keep it off the throttle?
- **Studio**: whether a 4B model produces a page that runs.
- **Tools**: whether a 4B model emits a parseable call, and how often — see 2e.

**How:** a diagnostics screen already exists. It should record a decode's
tokens/second, peak memory and thermal level per turn, so a device session
produces evidence instead of impressions.

---

## 4. NPU — this changed, and the old entry here was wrong

**What this section used to say:** that running an LLM on the Hexagon NPU needs
Qualcomm's QNN/Genie runtime and a model recompiled into a QNN context binary
for that exact Hexagon version, that GGUF is not interchangeable with that
artifact, and that it was therefore not on the near list.

**That is now out of date.** llama.cpp has a `GGML_HEXAGON` backend that runs
**GGUF directly** on the Hexagon NPU over FastRPC — no conversion, no QNN
context binary, no separate model file. Verified by reading
`ggml/src/ggml-hexagon/` on master rather than from memory.

### What it actually requires

1. **The Hexagon SDK.** `ggml/src/ggml-hexagon/CMakeLists.txt` opens with a
   `FATAL_ERROR` if `HEXAGON_SDK_ROOT` is not a directory. The SDK is a
   registration-walled Qualcomm download of several gigabytes whose licence
   does not allow redistribution — so it cannot be fetched in CI, and CI is the
   only place this project has ever produced a working build.
2. **A second toolchain.** The backend cross-compiles a DSP "skel"
   (`libggml-htp`) with the Hexagon compiler, which is not the Android NDK.
   Two cross-compiles in one build, targeting two different processors.
3. **Signing.** There is a `HEXAGON_HTP_CERT` option for signing the HTP
   library. A retail, non-rooted phone will not load an unsigned library onto
   the DSP. This is the part that could make the whole thing moot on a shop
   handset even after the first two are solved.

### What it would buy, and for which models

Supported weight types are narrow — `Q4_0`, `Q4_1`, `Q8_0`, `IQ4_NL`, `MXFP4`.
**Not `Q4_K_M` or `Q6_K`**, which is what most GGUF downloads are. Worth noting
for this project specifically: the model this app has actually been run with is
`Qwen3-4B-Q8_0`, which *is* on that list.

### Where this leaves it

Blocked on item 1, and that is a licence and distribution problem rather than
an engineering one. The honest position: it is possible, it is no longer
"needs a different model format", and it cannot be built from here. `NpuRuntime`
stays as the integration point and the UI keeps refusing to pretend.

---

## Order of work

1. ~~Arabic voice and engine picker~~ — done.
2. ~~Enable the TTS runtime~~ — done; CI is the referee on the conflict.
3. ~~Hosted Arabic voice~~ — done; the best voice available today, and the
   privacy cost is stated where the decision is made.
4. **OpenCL for GGUF** — the largest real gain; needs care.
5. **Instrument the diagnostics** — turn a device session into evidence.
6. **ONNX + Piper for Arabic speech** — a fully on-device answer to "more
   human", so the cloud stops being the only good option.
7. **Offline Arabic OCR** — Tesseract via JitPack plus `ara.traineddata`, so
   reading a scanned Arabic page stops costing a key and a connection.
8. **Hexagon NPU via `GGML_HEXAGON`** — only with the Hexagon SDK in hand, and
   only if a retail device will load the signed skel. See 4.

## What will not be claimed

- That anything is fast, until a token rate has been measured on the device.
- That the GPU path works, until a phone has reported layers on it.
- That search works, until it has returned a live result.
- That a voice is better, until it has been heard.
- That the cloud voice works, until a real key has returned real audio.
- That the model can use tools, until one has been seen to call one.
- That a PDF can be read, until one has been opened on a device.
