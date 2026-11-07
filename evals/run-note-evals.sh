#!/bin/bash
# Scores note reading against evals/note-evals.jsonl and writes a report to evals/results.
# Usage: evals/run-note-evals.sh [model[,model...]]
#   e.g. evals/run-note-evals.sh gemini-flash-lite-latest,deepseek:deepseek-chat
# Gemini models by name; DeepSeek models as deepseek:<model>.
# Keys (GEMINI_API_KEY, DEEPSEEK_API_KEY) come from local.properties, read by Gradle and never printed.
set -e
cd "$(dirname "$0")/.."
./gradlew :shared:testAndroidHostTest -Peval -PevalModels="${1:-gemini-flash-lite-latest}" --rerun -q
echo "Report: $(ls -t evals/results/*.md | head -1)"
