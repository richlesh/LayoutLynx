#!/usr/bin/env bash
#
# Refreshes the bundled emoji datasets used by MarkdownExtras / EmojiShortcodes.
#
# Deliberately opt-in (not part of the normal build) so builds stay offline-capable and
# reproducible. Run from the repository root:
#
#     ./scripts/update-emoji.sh
#   or
#     mvn -Pupdate-emoji generate-resources
#
# Produces:
#   app/src/main/resources/emoji.json.gz    — full gemoji Unicode dataset (gzipped)
#   app/src/main/resources/github-emoji.json — image-only GitHub-custom shortcodes -> PNG URL
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RES="$ROOT/app/src/main/resources"
GEMOJI_URL="https://raw.githubusercontent.com/github/gemoji/master/db/emoji.json"
GH_API_URL="https://api.github.com/emojis"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Fetching gemoji dataset..."
curl -fsSL "$GEMOJI_URL" -o "$tmp/emoji.json"

echo "Fetching GitHub emoji API..."
curl -fsSL -H "Accept: application/vnd.github+json" "$GH_API_URL" -o "$tmp/gh_emojis.json"

echo "Writing gzipped Unicode dataset..."
gzip -9 -c "$tmp/emoji.json" > "$RES/emoji.json.gz"

echo "Deriving image-only custom shortcodes..."
python3 - "$tmp/emoji.json" "$tmp/gh_emojis.json" "$RES/github-emoji.json" <<'PY'
import json, sys
gemoji_path, gh_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
gem = json.load(open(gemoji_path, encoding="utf-8"))
gh = json.load(open(gh_path, encoding="utf-8"))
unicode_aliases = {a for e in gem for a in e.get("aliases", [])}
# Custom = in the API, not in the Unicode alias set, and not a /unicode/ image URL.
custom = {k: v for k, v in gh.items()
          if k not in unicode_aliases and "/unicode/" not in v}
out = json.dumps(dict(sorted(custom.items())), ensure_ascii=False, indent=2)
open(out_path, "w", encoding="utf-8").write(out + "\n")
print(f"  wrote {len(custom)} custom shortcodes")
PY

echo "Done."
echo "  $RES/emoji.json.gz        ($(wc -c < "$RES/emoji.json.gz") bytes)"
echo "  $RES/github-emoji.json    ($(wc -c < "$RES/github-emoji.json") bytes)"
