#!/usr/bin/env bash
set -euo pipefail

pdf_directory=${1:-build/use-case-pdfs}
validator_url=${ARLINGTON_URL:-https://arlington.verapdf.org/api/validate/arlington2.0}

command -v qpdf >/dev/null || {
  echo "qpdf is required" >&2
  exit 2
}

find "$pdf_directory" -maxdepth 1 -type f -name '*.pdf' -print0 | while IFS= read -r -d '' pdf; do
  qpdf --check "$pdf" >/dev/null
  result=$(curl --fail --silent --show-error -F "file=@$pdf" "$validator_url")
  RESULT="$result" python3 - "$pdf" <<'PY'
import json
import os
import sys

job = json.loads(os.environ["RESULT"])["report"]["jobs"][0]
result = job["arlingtonResult"][0]
if not result["compliant"]:
    print(f"{sys.argv[1]}: Arlington PDF 2.0 validation failed", file=sys.stderr)
    print(json.dumps(result["details"]["ruleSummaries"], indent=2), file=sys.stderr)
    raise SystemExit(1)
print(f"{sys.argv[1]}: PDF 2.0 compliant")
PY
done
