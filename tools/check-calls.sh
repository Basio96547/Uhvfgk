#!/usr/bin/env bash
#
# Checks that every named argument passed to one of *this project's own*
# functions is a parameter that function actually has.
#
# Why this exists: the settings page called SectionCard with expanded=,
# onToggle= and summary= at thirteen sites. SectionCard had none of them. The
# call simply didn't resolve, and because an unresolved call also strips the
# @Composable context from its trailing lambda, one missing parameter produced
# forty compile errors across a file that parsed perfectly. check-syntax.sh saw
# nothing wrong — it only parses — and run-tests.sh can't touch a file that
# imports androidx. Five CI builds failed before anyone looked.
#
# This is deliberately narrow. It does not type-check, it does not resolve
# positional arguments, and it ignores every function it did not find declared
# in this repository. It answers one question: did I pass a parameter name that
# doesn't exist? That is the mistake that cost the five builds.
#
# Usage:  tools/check-calls.sh
# Needs:  python3. No JDK, no network, no Android SDK.

set -uo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$REPO_ROOT" <<'PY'
import re, sys, pathlib

root = pathlib.Path(sys.argv[1])
files = sorted((root / "app/src/main/java").rglob("*.kt"))

def strip_noise(text):
    """Blank out comments and string bodies so they can't look like code."""
    out, i, n = [], 0, len(text)
    while i < n:
        two = text[i:i + 2]
        if two == "//":
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i)); i = j
        elif two == "/*":
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            # Keep newlines so reported line numbers stay true.
            out.append("".join(c if c == "\n" else " " for c in text[i:j])); i = j
        elif text[i:i + 3] == '"""':
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append("".join(c if c == "\n" else " " for c in text[i:j])); i = j
        elif text[i] == '"':
            j, esc = i + 1, False
            while j < n and (esc or text[j] != '"'):
                esc = (text[j] == "\\" and not esc)
                if text[j] == "\n":
                    break
                j += 1
            j = min(j + 1, n)
            out.append("".join(c if c == "\n" else " " for c in text[i:j])); i = j
        else:
            out.append(text[i]); i += 1
    return "".join(out)

def balanced(text, open_at):
    """Index just past the ')' matching the '(' at open_at, or None."""
    depth = 0
    for i in range(open_at, len(text)):
        c = text[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i
    return None

def top_level_split(body):
    """Split an argument list on commas at depth zero, keeping each offset."""
    parts, depth, start = [], 0, 0
    for i, c in enumerate(body):
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == "," and depth == 0:
            parts.append((body[start:i], start)); start = i + 1
    parts.append((body[start:], start))
    return parts

NAME = r"[A-Za-z_][A-Za-z0-9_]*"

# ---- what this project declares ------------------------------------------
# Overloads union their parameters: passing a name that any overload accepts
# is not the mistake this tool is looking for.
declared = {}
sources = {}
for path in files:
    text = strip_noise(path.read_text(encoding="utf-8", errors="replace"))
    sources[path] = text
    for m in re.finditer(rf"\bfun\s+(?:<[^>]*>\s*)?(?:{NAME}\.)?({NAME})\s*\(", text):
        close = balanced(text, m.end() - 1)
        if close is None:
            continue
        params = set()
        for part, _ in top_level_split(text[m.end():close]):
            pm = re.match(rf"\s*(?:(?:va[lr]|vararg|crossinline|noinline)\s+)*({NAME})\s*:", part)
            if pm:
                params.add(pm.group(1))
        declared.setdefault(m.group(1), set()).update(params)

# ---- what this project calls ---------------------------------------------
problems = []
for path, text in sources.items():
    for m in re.finditer(rf"(?<![.\w])({NAME})\s*\(", text):
        fn = m.group(1)
        if fn not in declared:
            continue                      # not ours; we know nothing about it
        # A declaration is not a call.
        if re.search(r"\bfun\s+(?:<[^>]*>\s*)?(?:%s\.)?$" % NAME,
                     text[max(0, m.start() - 60):m.start()]):
            continue
        close = balanced(text, m.end() - 1)
        if close is None:
            continue
        for part, offset in top_level_split(text[m.end():close]):
            am = re.match(rf"\s*({NAME})\s*=(?!=)", part)
            if am and am.group(1) not in declared[fn]:
                line = text.count("\n", 0, m.end() + offset + am.start(1)) + 1
                problems.append(
                    (path.relative_to(root), line, fn, am.group(1),
                     sorted(declared[fn]))
                )

print(f"==> checking named arguments against {len(declared)} declarations "
      f"in {len(files)} files")

if not problems:
    print(">>> EVERY NAMED ARGUMENT EXISTS")
    sys.exit(0)

seen = set()
for rel, line, fn, arg, params in problems:
    key = (str(rel), fn, arg)
    if key in seen:
        continue
    seen.add(key)
    print(f"{rel}:{line}: {fn} has no parameter '{arg}'")
    print(f"    it takes: {', '.join(params) or '(nothing)'}")
print(f">>> {len(seen)} bad argument name(s)")
sys.exit(1)
PY
