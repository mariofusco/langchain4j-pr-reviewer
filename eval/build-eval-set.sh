#!/usr/bin/env sh
# Builds eval/eval-set.json from closed pull requests of public Java repositories, labelled from their outcomes.
# Reads GitHub through the read-only GitHub MCP server (Docker); calls no model.
set -e
cd "$(dirname "$0")/.."
mvn -q -B package -DskipTests
java -cp target/langchain4j-pr-reviewer-1.0.0-SNAPSHOT-cli.jar dev.langchain4j.prreviewer.eval.EvalSetBuilder "$@"
