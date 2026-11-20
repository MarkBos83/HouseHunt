#!/usr/bin/env bash
# Used by the Claude scheduled task: merges its Funda/Pararius results into homes.json,
# then commits and pushes to main. Pushing homes.json makes GitHub rebuild the website.
# Usage: bash scripts/update-claude-homes.sh /path/to/results.json
set -euo pipefail

results="$(realpath "$1")"
today="$(TZ=Europe/Amsterdam date +%F)"
cd "$(git rev-parse --show-toplevel)"

fail() { echo "FAILED: $1" >&2; exit 1; }

validate_results() {
  jq -e 'type == "object" and has("funda") and has("pararius")' "$results" > /dev/null \
    || fail "results must be an object with the keys funda and pararius"
  jq -e '[.funda, .pararius] | all(. == null or type == "array")' "$results" > /dev/null \
    || fail "funda and pararius must each be a list of homes or null"
  if jq -e '.funda == null and .pararius == null' "$results" > /dev/null; then
    echo "Both sources failed; homes.json is left unchanged."
    exit 0
  fi
}

merge_results() {
  jq --slurpfile results "$results" --arg today "$today" -f scripts/merge-claude-homes.jq homes.json > homes.json.tmp
  jq -e 'type == "array"' homes.json.tmp > /dev/null || fail "merge did not produce a list"
  mv homes.json.tmp homes.json
}

summarize() {
  jq -r --arg today "$today" '
    ["funda", "pararius"] | map(. as $name | [$name,
      ($all | map(select(.source == $name and .no_longer_listed == false)) | length),
      ($all | map(select(.source == $name and .first_seen == $today and .no_longer_listed == false)) | length)])
    | map("\(.[0]): \(.[1]) listed, \(.[2]) first seen today") | join("; ")' \
    --argjson all "$(cat homes.json)" -n
}

commit_and_push() {
  git config user.name > /dev/null || git config user.name "Claude scheduled task"
  git config user.email > /dev/null || git config user.email "noreply@anthropic.com"
  for attempt in 1 2 3; do
    git fetch -q --depth 1 origin main
    git reset -q --hard FETCH_HEAD
    merge_results
    git add homes.json
    if git diff --cached --quiet; then echo "No changes. $(summarize)"; return 0; fi
    git commit -q -m "Update Funda and Pararius homes"
    if git push -q origin HEAD:main; then echo "Pushed. $(summarize)"; return 0; fi
    sleep 5
  done
  fail "could not push after 3 attempts"
}

validate_results
commit_and_push
