#!/usr/bin/env bash
# Everything on this machine: starts the native decision server, then the reviewer's web interface on the local GPU,
# and opens the browser. Ctrl-C stops both.
#
#   JITLLM_MODEL=~/models/Qwen3.8-27B-Q4_0.gguf demo/run-demo.sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
port="${PORT:-8080}"
decision_port="${DECISION_PORT:-8090}"
export SYSTEM_ONE_BASE_URL="http://127.0.0.1:$decision_port"

if curl -sf "$SYSTEM_ONE_BASE_URL/health" >/dev/null; then
    echo "decision server already up on $SYSTEM_ONE_BASE_URL"
else
    "$here/demo/decision-server.sh" > "${TMPDIR:-/tmp}/pr-reviewer-decision.log" 2>&1 &
    decider=$!
    trap 'kill $decider 2>/dev/null || true' EXIT
    printf 'starting the decision server (native image)'
    until curl -sf "$SYSTEM_ONE_BASE_URL/health" >/dev/null; do
        kill -0 $decider 2>/dev/null || { echo; cat "${TMPDIR:-/tmp}/pr-reviewer-decision.log" >&2; exit 1; }
        printf '.'; sleep 0.2
    done
    echo " up"
fi

# open on macOS, xdg-open on Linux (where `open` may be openvt); without either, print the address
browse() {
    if [[ "$(uname)" == Darwin ]]; then open "$1"
    elif command -v xdg-open >/dev/null; then xdg-open "$1" >/dev/null 2>&1
    else echo "open $1"; fi
}
( until curl -sf "http://127.0.0.1:$port/" >/dev/null; do sleep 0.5; done; browse "http://127.0.0.1:$port/" ) &
"$here/bin/pr-reviewer-local" serve --port "$port"
