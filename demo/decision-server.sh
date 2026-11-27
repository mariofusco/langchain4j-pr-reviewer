#!/usr/bin/env bash
# The reviewer router's decision model, on this machine: jitllm's System One endpoint (POST /v1/systemone) over a small
# Qwen3 model, served by a GraalVM native image of jitllm on the GPU. A decision is one prefill and a read of the
# option labels' logits: nothing is generated.
#
#   demo/decision-server.sh build     # once: clone jitllm, build the jar and the native image (GraalVM for JDK 25)
#   demo/decision-server.sh           # serve on 127.0.0.1:8090 (SYSTEM_ONE_BASE_URL of the local profile)
#   demo/decision-server.sh --jvm     # the same server on the JVM, for comparison
#
# Environment: TORNADOVM_HOME (jdk22plus SDK), JAVA_HOME (JDK 25), GRAALVM_HOME (GraalVM for JDK 25, build only),
# DECISION_MODEL (default ~/models/Qwen3-1.7B-Q8_0.gguf), JITLLM_SRC (default ~/repos/jitllm-demo/jitllm),
# JITLLM_REF (default jitllm-v3.1.0), JITLLM_PRS (default none: pull requests to merge on top of the ref),
# DECISION_PORT (default 8090).
set -euo pipefail

: "${TORNADOVM_HOME:?set TORNADOVM_HOME to a TornadoVM SDK of the jdk22plus line}"
src="${JITLLM_SRC:-$HOME/repos/jitllm-demo/jitllm}"
model="${DECISION_MODEL:-$HOME/models/Qwen3-1.7B-Q8_0.gguf}"
port="${DECISION_PORT:-8090}"

if [[ "${1:-}" == build ]]; then
    : "${GRAALVM_HOME:?set GRAALVM_HOME to a GraalVM for JDK 25 with native-image}"
    if [[ ! -d "$src" ]]; then
        git clone https://github.com/beehive-lab/jitllm.git "$src"
        git -C "$src" checkout -q -b decision-server "${JITLLM_REF:-jitllm-v3.1.0}"
        for pr in ${JITLLM_PRS:-}; do
            git -C "$src" fetch -q origin "pull/$pr/head:pr-$pr"
            git -C "$src" merge -q --no-edit "pr-$pr"
        done
    fi
    (cd "$src" && ./mvnw -q clean package -DskipTests && scripts/native-image.sh build)
    echo "built $src/target/jitllm"
    exit 0
fi

[[ -f "$model" ]] || { echo "no decision model $model (DECISION_MODEL)" >&2; exit 1; }
common=(-Duse.tornadovm=true -Dtornado.tvm.maxbytecodesize=65536 -Dtornado.device.memory=4GB)

if [[ "${1:-}" == --jvm ]]; then
    exec "$src/jitllm" serve --gpu --heap-min 1g --heap-max 4g --gpu-memory 4GB -m "$model" -c 4096 \
        --host 127.0.0.1 --port "$port"
fi

binary="$src/target/jitllm"
[[ -x "$binary" ]] || { echo "no native image at $binary: run $0 build" >&2; exit 1; }
exec "$binary" "${common[@]}" -Djava.library.path="$TORNADOVM_HOME/lib" -Xmx4g \
    serve --gpu -m "$model" --ctx 4096 --host 127.0.0.1 --port "$port"
