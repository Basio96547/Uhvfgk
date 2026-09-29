#!/usr/bin/env bash
#
# Two cheap checks against this project's own code, neither of which needs a
# JDK, the Android SDK, or the network:
#
#   1. every named argument passed to a function declared here is a parameter
#      that function actually has;
#   2. every `import com.basel.ai....` names something that exists;
#   3. no `@Composable` sits on a declaration that cannot be composable.
#
# Why this exists: the settings page called SectionCard with expanded=,
# onToggle= and summary= at thirteen sites. SectionCard had none of them. The
# call simply didn't resolve, and because an unresolved call also strips the
# @Composable context from its trailing lambda, one missing parameter produced
# forty compile errors across a file that parsed perfectly. check-syntax.sh saw
# nothing wrong — it only parses — and run-tests.sh can't touch a file that
# imports androidx. Five CI builds failed before anyone looked.
#
# The second check earns its place the same way. ChatMessage moved to the
# com.basel.ai.chat package so the pure test harness could reach it, and
# ChatScreen kept importing com.basel.ai.ChatMessage. A stale import parses
# perfectly and the harness never compiles that file, so it too reached CI.
#
# The third is narrower still, and it is here because it happened: a stray
# `@Composable` ended up above `sealed interface Shared`, which the parser is
# perfectly happy with and which dies as "This annotation is not applicable to
# target 'interface'".
#
# All three are deliberately narrow. None type-checks, and anything not
# declared in this repository is ignored — guessing about Compose's overloads
# or androidx's package layout would produce noise rather than findings.
#
# Usage:  tools/check-refs.sh
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

# ---- @Composable on something that cannot be composable ------------------
# Only annotations alone on their own line: in a type position — `content:
# @Composable () -> Unit` — the same annotation is correct and common.
NOT_COMPOSABLE = ("class", "interface", "object", "enum", "typealias", "annotation")
bad_annotations = []
for path, text in sources.items():
    lines = text.split("\n")
    for i, line in enumerate(lines):
        if line.strip() != "@Composable":
            continue
        for follower in lines[i + 1:]:
            token = follower.strip()
            if not token or token.startswith("@") or token.startswith("*"):
                continue          # blank, another annotation, or a blanked comment
            words = [w for w in re.split(r"[\s(]+", token) if w]
            # Skip modifiers to reach the declaration keyword itself.
            keyword = next(
                (w for w in words if w not in (
                    "public", "private", "internal", "protected", "sealed", "abstract",
                    "open", "final", "data", "value", "inline", "expect", "actual",
                )),
                "",
            )
            if keyword in NOT_COMPOSABLE:
                bad_annotations.append(
                    (path.relative_to(root), i + 1, keyword, token[:60])
                )
            break

# ---- what this project imports -------------------------------------------
# Every name declared in each package, nested ones included, so that an import
# of an enum entry or a nested interface resolves like any other.
in_package = {}
for path, text in sources.items():
    pm = re.search(rf"^package\s+([\w.]+)", text, re.MULTILINE)
    if not pm:
        continue
    pkg = pm.group(1)
    names = in_package.setdefault(pkg, set())
    for dm in re.finditer(
        rf"\b(?:class|interface|object|enum\s+class|annotation\s+class|typealias|fun|val|var)\s+"
        # An optional generic parameter list, then an optional receiver type:
        # `fun Long.formatBytes()` declares formatBytes, not Long.
        rf"(?:<[^>]*>\s*)?(?:[\w.<>?,\s]+\.)?({NAME})", text
    ):
        names.add(dm.group(1))
    # Enum entries: the first line of an `enum class X { A, B }` body.
    for em in re.finditer(rf"\benum\s+class\s+{NAME}[^{{]*\{{([^}}]*)\}}", text):
        for entry in em.group(1).split(","):
            entry = entry.strip().split("(")[0].strip()
            if re.fullmatch(NAME, entry):
                names.add(entry)

OWN = "com.basel.ai"
bad_imports = []
for path, text in sources.items():
    for im in re.finditer(r"^import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$", text, re.MULTILINE):
        fqn = im.group(1)
        if not fqn.startswith(OWN + "."):
            continue
        # R and BuildConfig are generated at build time; there is no source
        # file here to find them in.
        if fqn.split(".")[-1] in ("R", "BuildConfig") or ".R." in fqn:
            continue
        parts = fqn.split(".")
        # Longest prefix that is a package we actually have; the next segment
        # then has to be a name declared in it.
        pkg_len = max(
            (i for i in range(1, len(parts)) if ".".join(parts[:i]) in in_package),
            default=0,
        )
        if pkg_len == 0:
            reason = "no such package"
        elif parts[pkg_len] in in_package[".".join(parts[:pkg_len])]:
            continue
        else:
            pkg = ".".join(parts[:pkg_len])
            where = sorted(p for p, n in in_package.items() if parts[pkg_len] in n)
            reason = f"not in {pkg}"
            if where:
                reason += f" — it is in {where[0]}"
        line = text.count("\n", 0, im.start()) + 1
        bad_imports.append((path.relative_to(root), line, fqn, reason))

print(f"==> checking named arguments against {len(declared)} declarations "
      f"in {len(files)} files, every {OWN} import, and every @Composable")

for rel, line, fqn, reason in bad_imports:
    print(f"{rel}:{line}: import {fqn} — {reason}")

for rel, line, keyword, snippet in bad_annotations:
    print(f"{rel}:{line}: @Composable is not applicable to a {keyword}")
    print(f"    it sits above: {snippet}")

if not problems and not bad_imports and not bad_annotations:
    print(">>> ARGUMENTS, IMPORTS AND ANNOTATIONS ALL RESOLVE")
    sys.exit(0)

seen = set()
for rel, line, fn, arg, params in problems:
    key = (str(rel), fn, arg)
    if key in seen:
        continue
    seen.add(key)
    print(f"{rel}:{line}: {fn} has no parameter '{arg}'")
    print(f"    it takes: {', '.join(params) or '(nothing)'}")
print(
    f">>> {len(seen)} bad argument name(s), {len(bad_imports)} bad import(s), "
    f"{len(bad_annotations)} misplaced annotation(s)"
)
sys.exit(1)
PY
