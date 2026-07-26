#!/usr/bin/env bash
#
# Parses every Kotlin source with the real Kotlin compiler and reports only
# *syntax* errors — unresolved references are expected here, because the
# Android and Compose jars aren't on the classpath.
#
# Why this exists: a scripted edit across the UI once ate half a string literal
# ("Put a \"<name>.tokens.json\"…" — the regex stopped at the escaped quote),
# leaving a file that balanced its braces, read fine at a glance, and failed CI
# five minutes later. A brace count can't see that. A parser can.
#
# Usage:  tools/check-syntax.sh
# Needs:  a JDK, and Gradle's bundled kotlin-compiler-embeddable.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

GRADLE_LIB="$(dirname "$(readlink -f "$(command -v gradle 2>/dev/null)" 2>/dev/null)" 2>/dev/null)/../lib"
[ -d "$GRADLE_LIB" ] || GRADLE_LIB=/opt/gradle-8.14.3/lib
if ! ls "$GRADLE_LIB"/kotlin-compiler-embeddable-*.jar >/dev/null 2>&1; then
    echo "No kotlin-compiler-embeddable jar under $GRADLE_LIB — skipping." >&2
    exit 0
fi

CP="$(ls "$GRADLE_LIB"/kotlin-*.jar "$GRADLE_LIB"/kotlinx-*.jar \
        "$GRADLE_LIB"/annotations-*.jar "$GRADLE_LIB"/trove4j*.jar 2>/dev/null | tr '\n' ':')"

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

mapfile -t SOURCES < <(find "$REPO_ROOT/app/src" -name '*.kt' | sort)
echo "==> parsing ${#SOURCES[@]} Kotlin files"

# The compiler will report a flood of unresolved references; only "Syntax error"
# and the parser's own complaints mean the file is actually malformed.
java -Dfile.encoding=UTF-8 -cp "$CP" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    "${SOURCES[@]}" -d "$OUT" -nowarn 2>&1 \
    | grep -E "Syntax error|Expecting |Unexpected tokens|Expecting an element" \
    > "$OUT/syntax.txt"

if [ -s "$OUT/syntax.txt" ]; then
    echo
    cat "$OUT/syntax.txt"
    echo
    echo ">>> SYNTAX ERRORS"
    exit 1
fi

echo ">>> ALL FILES PARSE"
