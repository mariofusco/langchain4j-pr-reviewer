# The PR reviewer, fully local: all-Java inference on the Mac GPU

Every agent of the reviewer runs **in the reviewer's own JVM, on the GPU**. That includes triage, context gathering
with tools, the five reviewers, scoring, debates and the report writer. The engine is
[jitllm](https://github.com/beehive-lab/jitllm) 3.1.0, whose transformer kernels are written in Java and
JIT-compiled to Metal by [TornadoVM](https://github.com/beehive-lab/TornadoVM). The reviewer router's decision
model runs beside it as a **GraalVM native image** of `jitllm serve`.

There is no API key, no Python, no Ollama and no llama.cpp. Nothing leaves the machine except the read-only
GitHub calls. Every review costs $0.00.

```
 pr-reviewer-local (Quarkus, JVM 25)                      decision server (GraalVM native image)
 ┌──────────────────────────────────────────────┐       ┌──────────────────────────────────────┐
 │ langchain4j-agentic workflow                 │ HTTP  │ jitllm serve  POST /v1/systemone     │
 │   router ───────────────────────────────────────────▶│ Qwen3-1.7B Q8_0: prefill + logits,   │
 │   triage, reviewers, scorer, writer ...      │       │ nothing generated                    │
 │        │ ChatModel (jitllm-economy/-standard/-premium)└──────────────────────────────────────┘
 │        ▼                                     │
 │ LocalChatModel ─▶ JitLLMChatModel ─▶ jitllm 3.1.0 ─▶ TornadoVM 8.2 ─▶ Metal (Apple GPU)
 └──────────────────────────────────────────────┘
```

## Run it

Prerequisites (SDKMAN): `sdk install java 25.0.2-open`, `sdk install tornadovm 8.2.0-jdk22plus-metal`, and
for the native image `sdk install java 25.0.1-graal`. Then:

```sh
export TORNADOVM_HOME=~/.sdkman/candidates/tornadovm/current JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
mvn install -DskipTests

# models (Hugging Face, unsloth): a large one for the escalations, a small one for the routine work, and the decider
curl -L -o ~/models/Qwen3.8-27B-Q4_0.gguf https://huggingface.co/unsloth/Qwen3.8-27B-GGUF/resolve/main/Qwen3.8-27B-Q4_0.gguf
curl -L -o ~/models/Qwen3-4B-Q8_0.gguf    https://huggingface.co/unsloth/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q8_0.gguf
curl -L -o ~/models/Qwen3-1.7B-Q8_0.gguf  https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf
export JITLLM_MODEL=~/models/Qwen3-4B-Q8_0.gguf JITLLM_PREMIUM_MODEL=~/models/Qwen3.8-27B-Q4_0.gguf

# the decision server as a native image (once; about 2.5 minutes)
GRAALVM_HOME=~/.sdkman/candidates/java/25.0.1-graal demo/decision-server.sh build

# everything: decision server, reviewer web interface, browser
demo/run-demo.sh
# or one review from the command line (decision server running); --deep sends correctness and security to the 27B
bin/pr-reviewer-local review langchain4j/langchain4j#7163 --deep
```

`bin/pr-reviewer-local` starts the JVM through TornadoVM (`@$TORNADOVM_HOME/tornado-argfile`) with the `local`
profile. The profile sets `--provider jitllm` and `--decision-model local` against `127.0.0.1:8090`, and lowers
the limits so that a review takes minutes rather than hours.

The models map onto the reviewer's three tiers, the way Haiku, Sonnet and Opus do for Anthropic. `JITLLM_MODEL`
serves every tier that has no model of its own; `JITLLM_ECONOMY_MODEL`, `JITLLM_STANDARD_MODEL` and
`JITLLM_PREMIUM_MODEL` set one. The reviewer escalates to the premium tier only on evidence: high risk,
security-sensitive code, `--deep`, or a critical contested finding. So with Qwen3-4B as the default and
Qwen3.8-27B as premium, the routine calls run at about 20 tok/s and the hard ones get the 27B. Each file loads
once, on its first call. Both models and the decider fit in the 28 GB Metal working set of a 36 GB Mac. Other
knobs: `JITLLM_THINK` (true), `JITLLM_PREMIUM_THINK` (false), `JITLLM_CONTEXT` (32768), `JITLLM_GPU_MEMORY` (26GB),
`JITLLM_HEAP` (8g), `JITLLM_DIRECT_MEMORY` (32g).

Measured end to end on langchain4j/langchain4j#7163 (about 60 changed lines), with the decider native:

| Setup | Review time | Result |
|---|--:|---|
| Qwen3-4B for all tiers, `JITLLM_THINK=false` | about 2 min (117 s) | DONE; one reviewer fell back on malformed JSON |
| Qwen3-4B for all tiers, thinking on | about 6 min | APPROVE 9.8/10, every agent succeeded |
| Qwen3-4B + Qwen3.8-27B premium, `--deep` | about 9 min | APPROVE 9.2/10; the 27B escalation fell back (see Pending) |

Thinking is most of the generated tokens. With `JITLLM_THINK=false` each call takes 10–15 s instead of 30–60 s.

In the web interface a review with the `jitllm` provider gets a **Local GPU** panel. It shows the model, device,
TornadoVM version, prefill kernels and native libraries, as jitllm reports them for the session. Live over SSE it
adds each model call's prompt and generated tokens, its time and tok/s, a sparkline, and the running totals, which
always show $0.00.

## What it shows, and what it doesn't

Measured on an Apple M3 Pro (18-core GPU, 36 GB), macOS 26.6, JDK 25.0.2, TornadoVM 8.1.0, jitllm 3.0.0:

| Model (GGUF) | Role | Prompt tok/s (pp) | Generation tok/s (tg128) | Notes |
|---|---|--:|--:|---|
| Qwen3.8-27B Q4_0, 15 GiB | premium tier | 56 (pp4096) | 7.7 | `qwen35`, Q4_0 kept on the GPU; 24 s to first token from a cold kernel cache |
| Qwen3-8B F16, 15 GiB | — | 247 (pp2048) | 8.8 | same bytes per token as the 27B, so the same decode speed |
| Qwen3-4B Q8_0, 4 GiB | economy and standard tiers | 463 (pp4096) | 29.1 | |
| Qwen3-1.7B Q8_0, 1.7 GiB | decider (native image) | — | — | one routing decision of 5 questions: 0.58 s |
| Qwen3.6-35B-A3B UD-Q4_K_M | — | — | — | does not run on Metal, see below |

`jitllm bench --gpu --metal`, one repetition; FP16 KV cache; jitllm's JIT kernels (no MLX, see below).

- **Decode is bound by memory bandwidth.** On this Mac, tokens per second come out to about 135 GB/s divided by
  the bytes of weights read per token. A 27B model at 4 bits decodes about as fast as an 8B model at 16 bits.
- **No MLX on an M3.** jitllm's MLX prefill needs an M4-class GPU. On M1–M3 jitllm uses its own Metal
  SIMD-group kernels. jitllm 3.0.0 still chose MLX there and failed at the first prefill; since 3.1.0
  ([beehive-lab/jitllm#277](https://github.com/beehive-lab/jitllm/pull/277)) it falls back on its own.
- **Qwen3.6-35B-A3B (MoE) does not run on Metal in jitllm 3.0.0.** Its expert feed-forward is implemented with
  packed integer dot products (`dp4a`), which only CUDA devices have:
  `this feed-forward reads a quantized activation, which needs a device with packed integer dot products`.
  It also needs `--fp32-kv-cache`.
- **The native image pays off in memory, not speed, on Metal.** The decision server's RSS drops from 3.4 GB to
  1.8 GB. Startup is the same with a warm kernel cache, and each decision is slightly slower than on the JVM, as
  jitllm's own measurements report for Metal. That's why only the decider is native. A native image of jitllm
  could only generate until 3.1.0 ([beehive-lab/jitllm#278](https://github.com/beehive-lab/jitllm/pull/278)),
  which dispatches `serve` and `bench` from its single entry point.
- **Structured output relies on the prompt.** jitllm rejects JSON response formats, so the jitllm models do not
  declare JSON-schema support, and LangChain4j writes the format into the prompt. It does so for record answers,
  but not for lists of records. `LocalOutputRepair` undoes the three slips local models make: a Markdown fence, a
  list wrapped as `{"findings": [...]}` where a bare array was expected, and `{}` for an empty list. The reviewer's fallbacks cover whatever
  remains.

## On NVIDIA (CUDA)

The same launcher runs on Linux with an NVIDIA GPU: `sdk install tornadovm 8.2.0-jdk22plus-cuda` instead of the
`-metal` SDK, and `$TORNADOVM_HOME/bin/tornado --devices` must list the CUDA device. On CUDA jitllm runs prefill
projections through cuBLAS (`cublasGemmExFP16FP32`) and decodes with its own kernels.

Measured on an RTX 4090 (24 GB), Ubuntu 22.04, driver 610.57 (CUDA 13.3), i9-13900K, JDK 25.0.2, TornadoVM 8.2.0,
jitllm 3.1.0:

| Model (GGUF) | Prompt tok/s (pp4096) | Generation tok/s (tg128) | M3 Pro, for comparison |
|---|--:|--:|---|
| Qwen3-4B Q8_0 | 9139 (7708 without cuBLAS) | 172 | 463 / 29.1 |
| Qwen3.8-27B Q4_0 | 2147 | 50.7 | 56 / 7.7 |

| Review of langchain4j/langchain4j#7163, JVM decider | Time | Result |
|---|--:|---|
| Qwen3-4B for all tiers, `JITLLM_THINK=false` | 28–32 s | COMMENT 9.1/10, no fallback |
| Qwen3-4B for all tiers, thinking on | 53 s | APPROVE 10.0/10, no fallback |
| Qwen3.8-27B for all tiers (`--model-tier=PREMIUM`) | 110 s | APPROVE 9.5/10, no fallback; 19.9 GB of VRAM |

`JITLLM_NATIVE_LIBRARIES=false` changes a review's time by less than a second: a review is mostly decode.

- **24 GB is not enough for Qwen3-4B and Qwen3.8-27B together.** The 4B takes 9.8 GB of VRAM at the default 32k
  context (7.4 GB at 16k), the 27B 15 GiB of weights alone, and the decider 3.1 GB. With both models set, the first
  escalation to the 27B fails with `TornadoOutOfMemoryException: Unable to allocate ... on the CUDA device` and the
  reviewer falls back to the 4B. On a 24 GB card pick one: the 4B for every tier, or the 27B for every tier.
- **Direct memory.** jitllm stages a model's tensors in direct buffers, which the JVM caps at the heap size. The
  launcher sets `-XX:MaxDirectMemorySize` to 32g (`JITLLM_DIRECT_MEMORY`); under the plain 8g cap the 27B failed to
  load with `Cannot reserve ... bytes of direct buffer memory`.

## How it is wired

| Piece | Where |
|---|---|
| Provider `jitllm`, no key, offered when `reviewer.jitllm.model-path` exists | `model/ModelProvider`, `models/ModelCatalog` |
| One GPU model for the three tiers, loaded on the first call | `models/JitllmModels` |
| Model name, listeners, timing, output repair around jitllm's adapter | `models/LocalChatModel`, `models/LocalOutputRepair` |
| Engine facts and per-call stats, `MODEL_CALL` run events | `observability/LocalInference`, `app/RunEvent` |
| Local GPU panel | `web/Views.GpuView`, `templates/pages/gpu.html`, `GET /reviews/{id}/gpu` |
| Launchers | `bin/pr-reviewer-local`, `demo/decision-server.sh`, `demo/run-demo.sh` |

## Pending before merge


- **The 27B escalation without thinking.** Qwen3.8-27B with thinking off answers in prose, not JSON, so the
  escalated correctness reviewer falls back. With thinking it answers correctly but takes about 20 min at 7.7 tok/s.
  Options: thinking on with a capped output budget, the 27B as the standard tier for unattended runs, or the 27B
  only for the prose agents (writer, critic).
- **One file, two copies.** `JitllmModels` keys a loaded model by file and thinking mode, because the adapter fixes
  thinking when the model is built. A tier that shares a file with another but not its thinking mode (the premium
  tier, `JITLLM_PREMIUM_THINK=false`, without a file of its own) loads the weights onto the GPU a second time.
  It needs a per-request thinking switch in the adapter.
- **Adapter gaps worth fixing upstream in `langchain4j-jitllm`:**
  - It leaves the model name out of responses; `LocalChatModel` stamps it.
  - It does not expose jitllm's `ExecutionInfo`; `LocalChatModel` reads it once by reflection for the panel.
  - It still declares jitllm 1.0.2, so the pom pins jitllm 3.1.0 over it. The Quarkus `jitllm` extension is no
    substitute: 2.0.0.CR1 ships jitllm 3.0.0 (3.1.0 is merged, quarkiverse/quarkus-langchain4j#3296, for the next
    release), and it fixes the model name, quantization and path at build time, where the reviewer picks a GGUF file
    per tier at run time.
- **Qwen3.6-35B-A3B on Metal** needs Metal kernels for jitllm's `qwen35moe` expert feed-forward, which is
  `dp4a`-only today.

