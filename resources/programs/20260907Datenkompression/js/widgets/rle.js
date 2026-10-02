function setupRLEWidget(source) {
  const t = source || getWidgetLang("rle");
  const container = document.getElementById("widget-s1-rle");
  if (!container) { return; }

  const DEFAULT = t.default || "AAABBBCCDDDDDA";

  function computeRLE(text) {
    const tuples = [];
    let i = 0;
    while (i < text.length) {
      const sym = text[i];
      let len = 1;
      while (i + len < text.length && text[i + len] === sym) len++;
      tuples.push([sym, len]);
      i += len;
    }
    return tuples;
  }

  function tupleCost(len) {
    return 1 + String(len).length;
  }

  function formatTuple([sym, len]) {
    return "(" + sym + "," + len + ")";
  }

  container.classList.remove("widget-placeholder");

  container.innerHTML = `
    <div style="padding:0.75rem 0">
      <div style="margin-bottom:10px">
        <label for="rle-input" style="display:block;font-size:12px;color:var(--muted);margin-bottom:5px">
          ${t.inputLabel}
        </label>
        <input id="rle-input" type="text" spellcheck="false" autocomplete="off"
          style="width:100%;font-family:monospace;font-size:1.1rem;letter-spacing:0.06em;
            border:1px solid var(--border);background:#fffdf8;padding:0.55rem 0.75rem;border-radius:3px"
          value="${DEFAULT}" maxlength="120" />
        <div style="display:flex;gap:8px;margin-top:8px;flex-wrap:wrap">
          <button id="rle-reset" type="button" style="font-size:13px;padding:4px 12px">${t.loadExample}</button>
        </div>
      </div>

      <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:12px">
        <div style="background:var(--box);border:1px solid var(--border);border-radius:3px;padding:10px 12px">
          <div style="font-size:12px;color:var(--muted);margin-bottom:4px">${t.originalSize}</div>
          <div id="rle-orig-size" style="font-size:20px;font-weight:500"></div>
        </div>
        <div style="background:var(--box);border:1px solid var(--border);border-radius:3px;padding:10px 12px">
          <div style="font-size:12px;color:var(--muted);margin-bottom:4px">${t.rleSize}</div>
          <div id="rle-comp-size" style="font-size:20px;font-weight:500"></div>
        </div>
      </div>

      <div style="background:var(--box);border:1px solid var(--border);border-radius:3px;padding:10px 12px;margin-bottom:10px">
        <div style="font-size:12px;color:var(--muted);margin-bottom:4px">${t.saving}</div>
        <div id="rle-saving" style="font-size:20px;font-weight:500"></div>
      </div>

      <div style="background:var(--paper);border:1px solid var(--border);border-radius:3px;padding:10px 12px">
        <div style="font-size:12px;color:var(--muted);margin-bottom:6px">${t.tuples}</div>
        <div id="rle-tuples" style="font-family:monospace;font-size:0.95rem;line-height:1.8;word-break:break-all"></div>
      </div>
    </div>`;

  const input = document.getElementById("rle-input");

  function getMetrics() {
    const text = input.value;
    const tuples = computeRLE(text);
    const origUnits = text.length;
    const rleUnits = tuples.reduce(function(sum, tup) { return sum + tupleCost(tup[1]); }, 0);
    const saving = origUnits - rleUnits;
    const hasLongRun = /(.)\1{9,}/.test(text);
    return {
      len: origUnits,
      saving: saving,
      savingPct: origUnits > 0 ? (saving / origUnits) * 100 : 0,
      hasLongRun: hasLongRun
    };
  }

  function resetWidget() {
    input.value = DEFAULT;
    update();
  }

  function update() {
    const text = input.value;
    const tuples = computeRLE(text);
    const origUnits = text.length;
    const rleUnits = tuples.reduce(function(sum, tup) { return sum + tupleCost(tup[1]); }, 0);
    const saving = origUnits - rleUnits;
    const pct = origUnits > 0 ? ((saving / origUnits) * 100).toFixed(1) : "0.0";

    document.getElementById("rle-orig-size").textContent = origUnits + " " + t.chars;
    document.getElementById("rle-comp-size").textContent = rleUnits + " " + t.chars;

    const savEl = document.getElementById("rle-saving");
    if (saving > 0) {
      savEl.textContent = formatLang(t.savingPositive, { count: saving, pct });
      savEl.style.color = "#3f8f4b";
    } else if (saving < 0) {
      savEl.textContent = formatLang(t.savingNegative, { count: Math.abs(saving), pct: Math.abs(pct) });
      savEl.style.color = "#a92f2f";
    } else {
      savEl.textContent = t.savingZero;
      savEl.style.color = "var(--muted)";
    }

    const tupleEl = document.getElementById("rle-tuples");
    if (text.length === 0) {
      tupleEl.textContent = t.emptyValue || getWidgetLang("common").emptyValue || "";
    } else if (tuples.length > 80) {
      tupleEl.innerHTML = tuples.slice(0, 80).map(function(tup) {
        const cls = tup[1] > 1 ? "color:var(--accent);font-weight:600" : "";
        return "<span style=\"" + cls + "\">" + formatTuple(tup) + "</span>";
      }).join(" ") + " \u2026";
    } else {
      tupleEl.innerHTML = tuples.map(function(tup) {
        const cls = tup[1] > 1 ? "color:var(--accent);font-weight:600" : "";
        return "<span style=\"" + cls + "\">" + formatTuple(tup) + "</span>";
      }).join(" ");
    }

    refreshWidgetChallenges("widget-s1-rle");
  }

  input.addEventListener("input", update);
  document.getElementById("rle-reset").addEventListener("click", function() {
    resetWidget();
  });

  registerWidgetChallenges("widget-s1-rle", {
    stickyComplete: true,
    challenges: [
      { prompt: t.challenges.save50, test: function(m) { return m.len >= 5 && m.savingPct >= 50; } },
      { prompt: t.challenges.negativeSaving, test: function(m) { return m.len >= 5 && m.saving < 0; } },
      { prompt: t.challenges.longRun, test: function(m) { return m.len >= 5 && m.hasLongRun; } }
    ],
    getMetrics: getMetrics,
    onReset: resetWidget
  });

  update();
}
