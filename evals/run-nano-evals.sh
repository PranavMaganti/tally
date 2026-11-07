#!/bin/bash
# Scores Gemini Nano (on the phone) against evals/note-evals.jsonl, alongside the cloud models.
# The phone must be unlocked with Tally able to come to the front: Nano only runs for the app on screen.
# Usage: evals/run-nano-evals.sh [adb-serial] [cloud models, or "none"]
set -e
cd "$(dirname "$0")/.."
ADB=(adb); [ -n "$1" ] && ADB=(adb -s "$1")
DIR=/sdcard/Android/data/me.vanpra.tally/files/evals
"${ADB[@]}" shell mkdir -p "$DIR"
"${ADB[@]}" push evals/note-evals.jsonl "$DIR/note-evals.jsonl" >/dev/null
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am start -S -n me.vanpra.tally/.MainActivity --ez evals true >/dev/null
for _ in $(seq 1 90); do sleep 2; "${ADB[@]}" logcat -d -s TallyProbe:I | grep -q "evals done" && break; done
mkdir -p evals/results
"${ADB[@]}" pull "$DIR/nano-results.jsonl" evals/results/nano-latest.jsonl >/dev/null
MODELS="${2:-none}"; [ "$MODELS" = none ] && MODELS=""
./gradlew :shared:testAndroidHostTest -Peval -PevalModels="$MODELS" -PevalNano="$PWD/evals/results/nano-latest.jsonl" --rerun -q
echo "Report: $(ls -t evals/results/*.md | head -1)"
