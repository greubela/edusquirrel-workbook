#!/usr/bin/env python3
"""
exercise_codegen.py
===================
Read a JSON exercise definition and patch BlockFeedbackExerciseRegistry.scala.

Usage:
    python tools/dev/exercise_codegen.py <json-file> [--dry-run]

JSON schema (see tools/dev/exercises/example.json):
    scalaName       – Scala val name, e.g. "addTwoNumbers"
    exerciseId      – stable registry key, e.g. "block:add-two-numbers"
    title.en / .de  – display titles
    statement.en/de – exercise statement text (backticks allowed)
    config
      enableVmStaticChecks   (bool, default true)
      enablePythonStaticChecks (bool, default true)
      enableUnitTests        (bool, default true)
      enableAiSummary        (bool, default true)
      timeoutMs              (int, default 4000)
      visibleTests: [{name, code, hint?, hintDE?, weight?}]
      hiddenTests:  [{name, code, hint?, hintDE?, weight?}]
      fixtures:     []   (usually empty)
      packages:     []   (usually empty)

What the script does:
    1. Generates the Scala code block for the exercise (with @exercise/@end markers).
    2. Patches BlockFeedbackExerciseRegistry.scala:
         a. Inserts/updates the exercise ID constant in the @ids block.
         b. Inserts or replaces the exercise val definition.
         c. Inserts/updates the byExerciseId map entry.

Python escape sequences in test code:
    Store them as JSON escape sequences, e.g. \\t for a literal backslash-t.
    The codegen correctly re-escapes them for Scala string literals.
    Example: "assert f('a\\\\t b') == 'a b'" in JSON
           → "assert f('a\\t b') == 'a b'"  in Python
           → "assert f('a\\t b') == 'a b'" in Scala (runtime: \\t = backslash+t → Python sees \\t)
"""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT   = SCRIPT_DIR.parent.parent

REGISTRY_PATH = REPO_ROOT / (
    "src/main/scala/interactionPlugins/blockEnvironment"
    "/feedback/config/BlockFeedbackExerciseRegistry.scala"
)
EXERCISES_DIR = SCRIPT_DIR / "exercises"

RULER = "─" * 70  # matches the width used in the registry source
INDENT = "  "     # object-member indentation (2 spaces)


def scala_string(s: str) -> str:
    """Wrap `s` in a Scala double-quoted string literal with proper escapes.

    JSON stores Python escape sequences as their character-pair form:
        JSON "\\t" → Python two-char string r'\t'
    We need the Scala source to read:
        "\\t"   which Scala parses as runtime string r'\t'
    so the generated Scala code passes the right thing to the Python executor.
    """
    s = s.replace("\\", "\\\\")   # backslash becomes \\\\
    s = s.replace('"',  '\\"')    # double-quote becomes \\"
    s = s.replace("\n", "\\n")    # actual newline becomes \\n literal
    s = s.replace("\t", "\\t")    # actual tab becomes \\t literal
    s = s.replace("\r", "\\r")    # actual CR becomes \\r literal
    return f'"{s}"'


def bool_lit(v: Any) -> str:
    """Convert a JSON bool-ish value to Scala bool literal."""
    if isinstance(v, bool):
        return "true" if v else "false"
    return "true" if str(v).lower() in ("true", "1", "yes") else "false"


def render_test(t: dict, base_indent: str) -> str:
    """Render a single BlockFeedbackPythonTest(…) constructor call."""
    i = base_indent
    lines = [f"{i}BlockFeedbackPythonTest("]

    # Required fields
    lines.append(f"{i}  name = {scala_string(t['name'])},")

    # code gets a trailing comma only if more fields follow
    optional = {}
    if "weight" in t:
        optional["weight"] = t["weight"]
    if t.get("hint"):
        optional["hint"] = t["hint"]
    if t.get("hintDE"):
        optional["hintDE"] = t["hintDE"]

    if optional:
        lines.append(f"{i}  code = {scala_string(t['code'])},")
        keys = list(optional.keys())
        for idx, k in enumerate(keys):
            is_last = (idx == len(keys) - 1)
            if k == "weight":
                val_str = f"{float(optional[k])}"
                # format as integer if it is .0
                if val_str.endswith(".0"):
                    val_str = val_str[:-2] + ".0"  # keep .0 for Scala Double
            else:  # hint, hintDE
                val_str = f"Some({scala_string(optional[k])})"
            comma = "" if is_last else ","
            lines.append(f"{i}  {k} = {val_str}{comma}")
    else:
        lines.append(f"{i}  code = {scala_string(t['code'])}")

    lines.append(f"{i})")
    return "\n".join(lines)


def render_test_seq(tests: list, field_name: str, base_indent: str) -> str:
    """Render a visibleTests/hiddenTests Seq(…) or Nil."""
    if not tests:
        return f"{base_indent}{field_name} = Nil,"
    inner_indent = base_indent + "  "
    items = [render_test(t, inner_indent) for t in tests]
    joined = (",\n").join(items)
    return f"{base_indent}{field_name} = Seq(\n{joined}\n{base_indent}),"


def generate_scala_block(ex: dict) -> str:
    """Generate the complete Scala registry block for an exercise, including
    @exercise and @end marker comments so the codegen can patch it later."""

    scala_name  = ex["scalaName"]
    exercise_id = ex["exerciseId"]
    title_en    = ex["title"]["en"]
    title_de    = ex["title"]["de"]
    stmt_en     = ex["statement"]["en"]
    stmt_de     = ex["statement"]["de"]
    cfg         = ex["config"]

    timeout  = int(cfg.get("timeoutMs", 4000))
    visible  = cfg.get("visibleTests", [])
    hidden   = cfg.get("hiddenTests",  [])
    packages = cfg.get("packages", [])

    # Config booleans
    def b(k: str, default: bool = True) -> str:
        return bool_lit(cfg.get(k, default))

    # Test sequences (indented to BlockFeedbackConfig level = 10 spaces)
    test_indent = INDENT * 5
    visible_scala  = render_test_seq(visible,  "visibleTests",  test_indent)
    hidden_scala   = render_test_seq(hidden,   "hiddenTests",   test_indent)
    fixtures_scala = f"{test_indent}fixtures = Nil,"

    if packages:
        pkg_list = ", ".join(scala_string(p) for p in packages)
        packages_scala = f"{test_indent}packages = Seq({pkg_list}),"
    else:
        packages_scala = f"{test_indent}packages = Nil,"

    lines = [
        f"{INDENT}// @exercise val={scala_name} id={exercise_id}",
        f"{INDENT}// {RULER}",
        f"{INDENT}// {title_en}  ·  {exercise_id}",
        f"{INDENT}// {RULER}",
        f"{INDENT}val {scala_name}: FeedbackExerciseDefinition =",
        f"{INDENT}  FeedbackExerciseDefinition(",
        f"{INDENT}    id = {scala_name}ExerciseId,",
        f"{INDENT}    titleTranslations = Map(",
        f"{INDENT}      english -> {scala_string(title_en)},",
        f"{INDENT}      german -> {scala_string(title_de)}",
        f"{INDENT}    ),",
        f"{INDENT}    statementTranslations = Map(",
        f"{INDENT}      english -> {scala_string(stmt_en)},",
        f"{INDENT}      german -> {scala_string(stmt_de)}",
        f"{INDENT}    ),",
        f"{INDENT}    config =",
        f"{INDENT}      BlockFeedbackConfig(",
        f"{INDENT}        enableVmStaticChecks = {b('enableVmStaticChecks')},",
        f"{INDENT}        enablePythonStaticChecks = {b('enablePythonStaticChecks')},",
        f"{INDENT}        enableUnitTests = {b('enableUnitTests')},",
        f"{INDENT}        enableAiSummary = {b('enableAiSummary')},",
        visible_scala,
        hidden_scala,
        fixtures_scala,
        packages_scala,
        f"{INDENT}        timeoutMs = {timeout}",
        f"{INDENT}      )",
        f"{INDENT}  )",
        f"{INDENT}// @end {scala_name}",
    ]
    return "\n".join(lines)


def patch_ids_block(content: str, scala_name: str, exercise_id: str) -> tuple[str, str]:
    """Insert or update the exerciseId constant inside the @ids block."""
    id_const_name = f"{scala_name}ExerciseId"
    id_const_line  = f'  val {id_const_name}: String = "{exercise_id}"'

    existing = re.compile(rf'  val {re.escape(id_const_name)}: String = "[^"]*"')
    if existing.search(content):
        content = existing.sub(id_const_line, content)
        return content, f"✓ Updated ID const: {id_const_name}"

    # Insert after the last existing ID with the same prefix (block: or script:)
    # so block IDs stay grouped together and script IDs stay grouped together.
    prefix = exercise_id.split(":")[0]
    prefix_ids = list(re.finditer(
        rf'  val \w+ExerciseId: String = "{re.escape(prefix)}:[^"]*"\n', content
    ))
    if prefix_ids:
        insert_at = prefix_ids[-1].end()
        content   = content[:insert_at] + id_const_line + "\n" + content[insert_at:]
        return content, f"✓ Inserted ID const: {id_const_name}"

    # Fallback: insert before private val (handles first-ever entry)
    m = re.search(r'(  val \w+ExerciseId: String = "[^"]*"\n)(\n  private val)', content)
    if m:
        insert_at = m.end(1)
        content   = content[:insert_at] + id_const_line + "\n" + content[insert_at:]
        return content, f"✓ Inserted ID const: {id_const_name}"

    return content, f"⚠  Could not find @ids block; add manually:\n     {id_const_line}"


def patch_exercise_block(content: str, scala_name: str, scala_block: str) -> tuple[str, str]:
    """Replace an existing @exercise…@end block, or insert before @byExerciseId."""
    ex_pattern = re.compile(
        rf'  // @exercise val={re.escape(scala_name)} id=[^\n]*\n'
        rf'.*?'
        rf'  // @end {re.escape(scala_name)}',
        re.DOTALL,
    )
    if ex_pattern.search(content):
        content = ex_pattern.sub(scala_block, content)
        return content, f"✓ Replaced exercise block: {scala_name}"

    # Insert before the @byExerciseId banner (or val byExerciseId as fallback)
    marker = re.search(r'(  // @byExerciseId|  val byExerciseId\b)', content)
    if marker:
        content = content[: marker.start()] + scala_block + "\n\n" + content[marker.start():]
        return content, f"✓ Inserted new exercise block: {scala_name}"

    return content, f"⚠  Could not find insertion point for {scala_name}; append manually."


def patch_by_exercise_id(content: str, scala_name: str) -> tuple[str, str]:
    """Add the exerciseId → val mapping to byExerciseId if not already present."""
    entry_pattern = re.compile(
        rf'\s+{re.escape(scala_name)}ExerciseId\s*->\s*{re.escape(scala_name)}\b'
    )
    if entry_pattern.search(content):
        return content, f"✓ byExerciseId entry already present for {scala_name}"

    # Find the last entry in the Map and insert after it (adding a trailing comma)
    last_entry = re.search(
        r'(      \w+ExerciseId -> \w+)(,?\n    \))',
        content
    )
    if not last_entry:
        return content, f"⚠  Could not patch byExerciseId; add manually: {scala_name}ExerciseId -> {scala_name}"

    before_close = last_entry.start(2)
    # Ensure existing last entry has a comma
    if not last_entry.group(1).rstrip().endswith(","):
        content = (
            content[: last_entry.start(2)]
            + ",\n"
            + f"      {scala_name}ExerciseId -> {scala_name}"
            + content[last_entry.start(2):]
        )
    else:
        content = (
            content[: before_close]
            + f",\n      {scala_name}ExerciseId -> {scala_name}"
            + content[before_close:]
        )
    return content, f"✓ Added {scala_name} to byExerciseId"


def patch_registry(ex: dict, dry_run: bool = False) -> None:
    scala_name  = ex["scalaName"]
    exercise_id = ex["exerciseId"]

    if not REGISTRY_PATH.exists():
        print(f"ERROR: Registry not found at {REGISTRY_PATH}", file=sys.stderr)
        sys.exit(1)

    content = REGISTRY_PATH.read_text(encoding="utf-8")
    scala_block = generate_scala_block(ex)

    messages: list[str] = []

    content, msg = patch_ids_block(content, scala_name, exercise_id)
    messages.append(msg)

    content, msg = patch_exercise_block(content, scala_name, scala_block)
    messages.append(msg)

    content, msg = patch_by_exercise_id(content, scala_name)
    messages.append(msg)

    for m in messages:
        print(f"  {m}")

    if dry_run:
        print("\n── DRY RUN – generated block (file not written) ─────────────────────")
        print(scala_block)
        print("─────────────────────────────────────────────────────────────────────")
    else:
        REGISTRY_PATH.write_text(content, encoding="utf-8")
        print(f"\n  ✓ Wrote {REGISTRY_PATH.relative_to(REPO_ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate Scala exercise block from JSON and patch the registry."
    )
    parser.add_argument("json_file", help="Path to exercise JSON definition file")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print generated code without modifying the registry",
    )
    args = parser.parse_args()

    json_path = Path(args.json_file)
    if not json_path.exists():
        print(f"ERROR: File not found: {json_path}", file=sys.stderr)
        sys.exit(1)

    with open(json_path, encoding="utf-8") as f:
        ex = json.load(f)

    for field in ("scalaName", "exerciseId", "title", "statement", "config"):
        if field not in ex:
            print(f"ERROR: Missing required field '{field}' in {json_path}", file=sys.stderr)
            sys.exit(1)

    for sub in ("en", "de"):
        for top in ("title", "statement"):
            if sub not in ex[top]:
                print(f"ERROR: Missing {top}.{sub} in {json_path}", file=sys.stderr)
                sys.exit(1)

    print(f"\nProcessing: {ex['scalaName']}  ({ex['exerciseId']})")
    print(f"Registry  : {REGISTRY_PATH.relative_to(REPO_ROOT)}\n")

    patch_registry(ex, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
