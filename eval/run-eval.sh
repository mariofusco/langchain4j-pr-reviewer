#!/usr/bin/env sh
# Scores the reviewer against eval/eval-set.json. Every run calls the Anthropic models and costs money:
# try one case first (--limit 1 --runs 1) and check the cost it prints.
set -e
cd "$(dirname "$0")/.."
mvn -q -B package -DskipTests
java -cp target/langchain4j-pr-reviewer-1.0.0-SNAPSHOT-cli.jar dev.langchain4j.prreviewer.eval.EvalRunner "$@"
