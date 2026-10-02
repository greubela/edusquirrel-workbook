function setupRLETextWidget(source) {
  const t = source || getWidgetLang("rleText");
  const container = document.getElementById("widget-s1-rle-text");
  if (!container) { return; }

  const SAMPLE_TEXT = t.sampleText || "";

  function computeRLE(text) {
    const tuples = [];
    let i = 0;
    while (i < text.length) {
      const ch = text[i];
      let len = 1;
      while (i + len < text.length && text[i + len] === ch) len++;
      tuples.push([ch, len]);
      i += len;
    }
    return tuples;
  }

  const tuples = computeRLE(SAMPLE_TEXT);
  const origChars = SAMPLE_TEXT.length;
  const rleUnits = tuples.length * 2;
  const saving = origChars - rleUnits;
  const pct = ((saving / origChars) * 100).toFixed(1);

  function buildHighlightedText() {
    let html = "";
    for (const [ch, len] of tuples) {
      const display = ch === " " ? "·" : ch === "\n" ? "↵" : ch;
      if (len > 1) {
        html += `<span style="background:rgba(28,93,140,0.13);border-radius:2px" title="${len}×">${display.repeat(len)}</span>`;
      } else {
        html += display === ch ? ch : `<span style="color:var(--muted)">${display}</span>`;
      }
    }
    return html;
  }

  function buildTupleList() {
    const MAX = 80;
    const parts = tuples.slice(0, MAX).map(([ch, len]) => {
      const display = ch === " " ? "␣" : ch;
      if (len > 1) {
        return `<span style="color:var(--accent);font-weight:600">(${display},${len})</span>`;
      }
      return `(${display},1)`;
    });
    if (tuples.length > MAX) {
      parts.push(`<span style="color:var(--muted)">${formatLang(t.moreTuples, { count: tuples.length - MAX })}</span>`);
    }
    return parts.join(" ");
  }

  const savingHtml = saving > 0
    ? formatLang(t.savingSmaller, { count: saving, pct })
    : formatLang(t.savingLarger, { count: Math.abs(saving), pct: Math.abs(pct) });

  container.innerHTML = `
    <div style="padding:0.75rem 0">

      <div style="margin-bottom:10px">
        <div style="font-size:12px;color:var(--muted);margin-bottom:5px;font-family:sans-serif">
          ${t.originalLabel}
        </div>
        <div style="
          font-family:monospace;font-size:0.82rem;line-height:1.8;
          border:1px solid var(--border);background:#fffdf8;
          padding:0.6rem 0.8rem;border-radius:3px;
          word-break:break-all;
        ">${buildHighlightedText()}</div>
      </div>

      <div style="margin-bottom:10px">
        <div style="font-size:12px;color:var(--muted);margin-bottom:5px;font-family:sans-serif">
          ${t.tuplesLabel}
        </div>
        <div style="
          font-family:monospace;font-size:0.82rem;line-height:1.9;
          border:1px solid var(--border);background:#fffdf8;
          padding:0.6rem 0.8rem;border-radius:3px;
          word-break:break-all;
        ">${buildTupleList()}</div>
      </div>

      <div style="
        display:flex;gap:16px;flex-wrap:wrap;align-items:center;
        border:1px solid var(--border);background:#fffdf8;
        padding:0.55rem 0.8rem;border-radius:3px;
        font-size:0.88rem;font-family:sans-serif;
      ">
        <span>${formatLang(t.originalChars, { count: origChars })}</span>
        <span style="color:var(--muted)">→</span>
        <span>${formatLang(t.rleChars, { tuples: tuples.length, units: rleUnits })}</span>
        <span style="color:${saving > 0 ? "#3f8f4b" : "#a92f2f"};font-weight:600">
          ${savingHtml}
        </span>
      </div>

    </div>`;
}
