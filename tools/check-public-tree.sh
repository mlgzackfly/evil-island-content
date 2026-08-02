#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"

failed=0
mode="${1:---staged}"

list_paths() {
    case "$mode" in
        --staged) git diff --cached --name-only --diff-filter=ACMR ;;
        --tracked) git ls-files ;;
        *)
            printf 'Usage: %s [--staged|--tracked]\n' "$0" >&2
            exit 2
            ;;
    esac
}

while IFS= read -r path; do
    case "$path" in
        research/*|scripts/*|server/*|DESIGN_NOTES.md|src/main/resources/novel-index.yml|*.txt|*.sqlite3|*source-index*.json|*.jar)
            printf 'Blocked private or generated path: %s\n' "$path" >&2
            failed=1
            ;;
    esac
done < <(list_paths)

patterns='\[(I|II)-[0-9]{2}[[:space:]]+L[0-9]+|噩盡島[^[:space:]]*[^/]*\.txt|SETTING_BIBLE|TIMELINE_AND_VOLUMES|PLUGIN_ADAPTATION_RULES|SOURCE_MANIFEST|corpus\.sqlite3|source-index\.json|novel-index\.yml'

if git grep --cached -I -n -E "$patterns" -- . \
    ':(exclude).gitignore' \
    ':(exclude)tools/check-public-tree.sh' >/tmp/evil-island-private-scan.$$ 2>/dev/null; then
    printf 'Blocked sensitive source reference in staged content:\n' >&2
    cat /tmp/evil-island-private-scan.$$ >&2
    failed=1
fi
rm -f /tmp/evil-island-private-scan.$$

if (( failed != 0 )); then
    printf '\nCommit rejected. Remove the private material from the index and try again.\n' >&2
    exit 1
fi

printf 'Privacy scan passed.\n'
