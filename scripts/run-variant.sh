#!/usr/bin/env bash
# Runs an exercise's test suite against one variant, the way Artemis CI does:
# copy tests/ to a temp folder, put the variant's src/ into assignment/src, run ./gradlew clean test.
# Temporary helper for build step 1; the validator does this itself from step 2 on.
#
# Usage: scripts/run-variant.sh <exercise-folder> <variant>   e.g. exercises/array-max solution
set -euo pipefail

exercise="$(cd "$1" && pwd)"
variant="$2"
src="$exercise/$variant/src"
[[ -d "$src" ]] || { echo "No src/ folder at $src" >&2; exit 2; }

work="$(mktemp -d)"
[[ -n "${KEEP_WORK:-}" ]] && echo "work dir: $work" >&2 || trap 'rm -rf "$work"' EXIT
cp -R "$exercise/tests/." "$work/"
mkdir -p "$work/assignment"
cp -R "$src" "$work/assignment/src"

cd "$work"
./gradlew clean test --console=plain -q > gradle.log 2>&1 && status=0 || status=$?

results=(build/test-results/test/*.xml)
if [[ ! -e "${results[0]}" ]]; then
  echo "$variant: no test results (compile or build failure, exit $status)"
  tail -30 gradle.log
  exit 1
fi

echo "$variant:"
python3 - "${results[@]}" <<'PY'
import sys, xml.etree.ElementTree as ET
for path in sys.argv[1:]:
    for case in ET.parse(path).getroot().iter("testcase"):
        name = case.get("name")
        bad = case.find("failure")
        if bad is None:
            bad = case.find("error")
        if bad is not None:
            msg = (bad.get("message") or bad.get("type") or "").splitlines()[0][:110]
            print(f"  FAIL  {name}: {msg}")
        elif case.find("skipped") is not None:
            print(f"  SKIP  {name}")
        else:
            print(f"  pass  {name}")
PY
