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
| **GPU execution** | **Façade.** The Backend picker offers GPU; GGUF ignores it entirely and runs on CPU. |
| **NPU execution** | **Façade, and honest about it.** Detected, reported, never used. |
| Custom TTS models | Runtime is packaged now, so the path is reachable. **Never run against a real voice model** — and good Arabic voices are ONNX, not `.tflite` (see 2c). |
| Cloud voice | Real code, real endpoints, real request format. **Never sent a request** — no key in the sandbox. |

---

## 1. GPU acceleration for GGUF — the biggest real win available

**Now:** `GGML_OPENCL=OFF`. Every GGUF token is decoded on the CPU. The
"GPU" backend option does nothing for the format the user actually runs, and
the app says so, but saying so is not the same as doing it.

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

## 3. Prove the pieces that are written but unproven

Nothing new to build; these need a device and a model.

- **Web search** against a live endpoint. The parser is tested against saved
  HTML, which proves the parsing and nothing about DuckDuckGo's current markup.
- **Real inference** on a GGUF model: tokens per second, memory, whether the
  KV-cache fixes hold across a long conversation.
- **Thermal** behaviour under a sustained decode — does the thread budget
  actually keep it off the throttle?
- **Studio**: whether a 4B model produces a page that runs.

**How:** a diagnostics screen already exists. It should record a decode's
tokens/second, peak memory and thermal level per turn, so a device session
produces evidence instead of impressions.

---

## 4. NPU — say what is true, and leave the door open

Running an LLM on the Hexagon NPU needs Qualcomm's QNN/Genie runtime and a
model recompiled into a QNN context binary for that exact Hexagon version.
Neither is a Maven dependency, and the model artifact is not interchangeable
with GGUF.

This is not on the near list. `NpuRuntime` is the integration point and the UI
already refuses to pretend. That is the correct state until the SDK is
actually in hand.

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
7. **QNN/NPU** — only with the SDK in hand.

## What will not be claimed

- That anything is fast, until a token rate has been measured on the device.
- That search works, until it has returned a live result.
- That a voice is better, until it has been heard.
- That the cloud voice works, until a real key has returned real audio.
