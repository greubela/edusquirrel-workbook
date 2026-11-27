"""Generates the call stack figure in section 1 of the monks workbook (German and English).

Example: sortieren("7294") with the monks' selection sort; every frame is one monk.
Usage: python3 tools/dev/monks-callstack-figure.py resources/workbookresources/monks/callstack
"""
import sys
from html import escape

W, H = 1000, 930
# Rendered size of the figure; the drawing itself keeps the W x H coordinates of the viewBox.
DISPLAY_W, DISPLAY_H = 720, 670
PANEL = 196
X0 = 10
FRAME_W, FRAME_H, SLOT = 182, 52, 56
MONO = "ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace"
SANS = "system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif"

C = {
    "bg": "#ffffff", "ink": "#1f2937", "muted": "#5b6472", "wall": "#4b5563",
    "frame": "#eef1f5", "frame_stroke": "#7b8796",
    "active": "#dbeafe", "active_stroke": "#2563eb",
    "base": "#fef3c7", "base_stroke": "#b45309",
    "ret": "#15803d",
}

TEXT = {
    "de": {
        "call": 'sortieren("{}")', "var": "groesste = '{}'", "ret_empty": 'return ""',
        "monk": ["1. Mönch", "2. Mönch", "3. Mönch", "4. Mönch"], "traveller": "Reisender",
        "row1": "Hinweg: Jeder Aufruf legt einen neuen Rahmen oben auf den Aufrufstapel.",
        "row2": "Rückweg: Der oberste Rahmen gibt sein Ergebnis zurück und verschwindet. Der Rahmen darunter rechnet weiter.",
        "cap1": ["1. Aufruf", "2. Aufruf", "3. Aufruf", "4. Aufruf", "5. Aufruf: Abbruch"],
        "cap2": ["1. Rückgabe", "2. Rückgabe", "3. Rückgabe", "4. Rückgabe", "Stapel leer"],
        "result": "Ergebnis", "result_sub": "des ersten Aufrufs",
        "legend": ["aktiver Aufruf (oberster Rahmen)", "Abbruchbedingung: leeres Tablett",
                   "wartender Aufruf: behält seine Karte groesste", "Rückgabewert"],
    },
    "en": {
        "call": 'sort("{}")', "var": "largest = '{}'", "ret_empty": 'return ""',
        "monk": ["Monk 1", "Monk 2", "Monk 3", "Monk 4"], "traveller": "Traveller",
        "row1": "Way there: every call puts a new frame on top of the call stack.",
        "row2": "Way back: the top frame returns its result and disappears. The frame below continues its work.",
        "cap1": ["1st call", "2nd call", "3rd call", "4th call", "5th call: base case"],
        "cap2": ["1st return", "2nd return", "3rd return", "4th return", "Stack empty"],
        "result": "Result", "result_sub": "of the first call",
        "legend": ["active call (top frame)", "base case: empty tray",
                   "waiting call: keeps its card largest", "return value"],
    },
}

# (cards, largest) per monk; the fifth call gets the empty tray.
CALLS = [("7294", "9"), ("724", "7"), ("24", "4"), ("2", "2")]
# Value handed back to monk k and the value monk k returns.
RETURNS = [('""', '"2"'), ('"2"', '"42"'), ('"42"', '"742"'), ('"742"', '"9742"')]


def t(x, y, s, size=14, family=SANS, fill=None, weight="normal", anchor="start"):
    return (f'<text x="{x}" y="{y}" font-family="{family}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill or C["ink"]}" text-anchor="{anchor}">{escape(s)}</text>')


def bucket(x, top, bottom):
    l, r = x + 3, x + PANEL - 3
    return (f'<path d="M {l} {top} L {l} {bottom} L {r} {bottom} L {r} {top}" fill="none" '
            f'stroke="{C["wall"]}" stroke-width="3" stroke-linejoin="round"/>')


def frame(x, bottom, slot, line1, line2, tag, kind):
    fx, fy = x + 7, bottom - 6 - (slot + 1) * SLOT
    fill, stroke = {"wait": (C["frame"], C["frame_stroke"]), "active": (C["active"], C["active_stroke"]),
                    "base": (C["base"], C["base_stroke"])}[kind]
    out = [f'<rect x="{fx}" y="{fy}" width="{FRAME_W}" height="{FRAME_H}" rx="6" fill="{fill}" stroke="{stroke}" '
           f'stroke-width="{2.5 if kind != "wait" else 1.5}"/>',
           t(fx + 8, fy + 21, line1, 14, MONO, weight="bold"),
           t(fx + 8, fy + 42, line2, 13.5, MONO, fill=C["ret"] if "→" in line2 else None)]
    if tag:
        out.append(t(fx + FRAME_W - 8, fy + 42, tag, 11, SANS, fill=C["muted"], anchor="end"))
    return out, fy


def figure(lang):
    L = TEXT[lang]
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{DISPLAY_W}" height="{DISPLAY_H}" role="img">',
           f'<rect x="0" y="0" width="{W}" height="{H}" rx="14" fill="{C["bg"]}"/>',
           '<defs><marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" '
           f'orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="{C["ret"]}"/></marker></defs>']

    def wait_frame(x, bottom, k):
        cards, largest = CALLS[k]
        return frame(x, bottom, k, L["call"].format(cards), L["var"].format(largest), L["monk"][k], "wait")[0]

    # Row 1: the stack grows.
    out.append(t(X0, 36, L["row1"], 16, weight="bold"))
    top, bottom = 56, 360
    for k in range(5):
        x = X0 + k * PANEL
        out.append(bucket(x, top, bottom))
        for j in range(k):
            out += wait_frame(x, bottom, j)
        if k < 4:
            cards, largest = CALLS[k]
            out += frame(x, bottom, k, L["call"].format(cards), L["var"].format(largest), L["monk"][k], "active")[0]
        else:
            out += frame(x, bottom, k, L["call"].format(""), L["ret_empty"], L["traveller"], "base")[0]
        out.append(t(x + PANEL / 2, bottom + 26, L["cap1"][k], 13.5, anchor="middle", fill=C["muted"]))

    # Row 2: the stack shrinks; every return value is handed to the frame below.
    out.append(t(X0, 446, L["row2"], 16, weight="bold"))
    top, bottom = 466, 770
    for m in range(5):
        x = X0 + m * PANEL
        out.append(bucket(x, top, bottom))
        receiver = 3 - m
        for j in range(max(receiver, 0)):
            out += wait_frame(x, bottom, j)
        if m < 4:
            received, returned = RETURNS[m]
            largest = CALLS[receiver][1]
            parts, fy = frame(x, bottom, receiver, L["call"].format(CALLS[receiver][0]),
                              f"'{largest}' + {received} → {returned}", None, "active")
            out += parts
            ax = x + 40
            out.append(f'<line x1="{ax}" y1="{fy - 40}" x2="{ax}" y2="{fy - 4}" stroke="{C["ret"]}" stroke-width="2.5" '
                       'marker-end="url(#arrow)"/>')
            out.append(t(ax + 10, fy - 16, received, 15, MONO, fill=C["ret"], weight="bold"))
        else:
            cy = (top + bottom) / 2
            out.append(t(x + PANEL / 2, cy - 22, L["result"], 15, anchor="middle", weight="bold"))
            out.append(t(x + PANEL / 2, cy - 4, L["result_sub"], 12.5, anchor="middle", fill=C["muted"]))
            out.append(t(x + PANEL / 2, cy + 30, '"9742"', 26, MONO, fill=C["ret"], weight="bold", anchor="middle"))
        out.append(t(x + PANEL / 2, bottom + 26, L["cap2"][m], 13.5, anchor="middle", fill=C["muted"]))

    # Legend.
    ly = 850
    swatches = [("active", C["active"], C["active_stroke"]), ("base", C["base"], C["base_stroke"]),
                ("wait", C["frame"], C["frame_stroke"])]
    positions = [(X0 + 10, ly), (X0 + 500, ly), (X0 + 10, ly + 36), (X0 + 500, ly + 36)]
    for (kind, fill, stroke), (px, py), label in zip(swatches, positions, L["legend"]):
        out.append(f'<rect x="{px}" y="{py - 15}" width="34" height="20" rx="4" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
        out.append(t(px + 44, py, label, 14))
    px, py = positions[3]
    out.append(f'<line x1="{px}" y1="{py - 5}" x2="{px + 34}" y2="{py - 5}" stroke="{C["ret"]}" stroke-width="2.5" marker-end="url(#arrow)"/>')
    out.append(t(px + 44, py, L["legend"][3], 14))

    out.append("</svg>")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    target = sys.argv[1]
    for lang in ("de", "en"):
        with open(f"{target}/callstack-{lang}.svg", "w", encoding="utf-8") as f:
            f.write(figure(lang))
