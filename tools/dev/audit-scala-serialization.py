#!/usr/bin/env python3
"""Inventory production Scala case classes, sealed traits and enums.

This is a source audit, not a Scala compiler: custom-codec detection is local to
its file and does not prove wire compatibility. Run the Scala round-trip suites
as well. Runtime exceptions are reviewed in docs/serialization-exceptions.json.
"""
from pathlib import Path
import json
import re

def mask(s):
    out = list(s)
    i = 0
    while i < len(s):
        start = i
        if s.startswith('/*', i):
            depth = 1
            i += 2
            while i < len(s) and depth:
                if s.startswith('/*', i):
                    depth += 1
                    i += 2
                elif s.startswith('*/', i):
                    depth -= 1
                    i += 2
                else:
                    i += 1
        elif s.startswith('//', i):
            j = s.find('\n', i)
            i = len(s) if j < 0 else j
        elif s.startswith('"""', i):
            j = s.find('"""', i + 3)
            i = len(s) if j < 0 else j + 3
        elif s[i] == '"':
            i += 1
            while i < len(s):
                if s[i] == '\\':
                    i += 2
                elif s[i] == '"':
                    i += 1
                    break
                else:
                    i += 1
        elif s[i] == "'" and re.match("'(?:\\\\.|[^'\\n])'", s[i:]):
            i += len(re.match("'(?:\\\\.|[^'\\n])'", s[i:]).group())
        else:
            i += 1
            continue
        for j in range(start, min(i, len(s))):
            if s[j] != '\n':
                out[j] = ' '
    return ''.join(out)

def declarations(path, s):
    masked = mask(s)
    rows = []
    for m in re.finditer('\\b(case\\s+class|sealed\\s+trait|enum)\\s+(\\w+)', masked):
        i = m.end()
        stack = []
        while i < len(masked):
            c = masked[i]
            if not stack:
                if c in '{=:}':
                    break
                if c == '\n':
                    nxt = masked[i + 1:].lstrip()
                    prev = masked[m.end():i].rstrip()
                    if not re.match('(extends\\b|with\\b|derives\\b|\\(|\\[|,)', nxt) and (not prev.endswith(('extends', 'with', ','))):
                        break
                if re.match('\\b(case|def|object|val|class|trait|final|sealed)\\b', masked[i:]) and i > m.end():
                    break
            if c in '([':
                stack.append(c)
            elif c in ')]':
                if stack:
                    stack.pop()
            elif stack and c == '{':
                stack.append(c)
            elif stack and c == '}':
                stack.pop()
            i += 1
        end = i
        while end > m.end() and s[end - 1].isspace():
            end -= 1
        rows.append({'file': str(path), 'line': s.count('\n', 0, m.start()) + 1, 'kind': m.group(1), 'name': m.group(2), 'start': m.start(), 'end': end, 'header': s[m.start():end]})
    return rows

def inventory(root):
    rows = []
    for p in sorted((root / 'modules').rglob('*.scala')):
        if '/src/main/' not in p.as_posix():
            continue
        relative = p.relative_to(root).as_posix()
        source = p.read_text()
        masked_source = mask(source)
        counts = {}
        for row in declarations(relative, source):
            name = row['name']
            counts[name] = counts.get(name, 0) + 1
            row['key'] = f'{relative}#{name}:{counts[name]}'
            header = mask(row['header'])
            if re.search('\\bderives\\b[^{}]*\\bReadWriter\\b', header):
                row['status'] = 'derived'
            elif re.search('(?:ReadWriter|RW)\\s*\\[\\s*' + re.escape(name) + '(?:\\s*\\[|\\s*\\])', masked_source):
                row['status'] = 'custom'
            else:
                row['status'] = 'missing'
            for field in ('start', 'end', 'header'):
                row.pop(field)
            rows.append(row)
    return rows

def main():
    import argparse
    from collections import Counter
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--json', action='store_true', help='Print the full per-declaration inventory')
    parser.add_argument('--check', action='store_true', help='Fail on missing codecs or stale runtime exceptions')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    exceptions = json.loads((root / 'docs/serialization-exceptions.json').read_text())
    rows = inventory(root)
    missing = []
    for row in rows:
        key = row['key']
        if row['status'] == 'missing' and key in exceptions:
            row['status'] = 'exception'
            row['reason'] = exceptions[key]
        elif row['status'] == 'missing':
            missing.append(row)
    statuses = {row['key']: row['status'] for row in rows}
    stale = sorted(key for key in exceptions if statuses.get(key) != 'exception')
    if args.json:
        print(json.dumps(rows, indent=2))
    else:
        print(f"Inventoried {len(rows)} declarations: {dict(sorted(Counter((r['status'] for r in rows)).items()))}")
        for row in missing:
            print(f"Missing: {row['file']}:{row['line']} {row['kind']} {row['name']}")
        for key in stale:
            print(f'Stale exception: {key}')
    if args.check and (missing or stale):
        return 1
    return 0
if __name__ == '__main__':
    raise SystemExit(main())
