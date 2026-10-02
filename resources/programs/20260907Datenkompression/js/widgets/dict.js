function setupDictWidget(source) {
  const t = source || getWidgetLang("dict");
  const emptyValue = t.emptyValue || getWidgetLang("common").emptyValue || "…";
  const container = document.getElementById("widget-s1-dict");
  if (!container) { return; }

  const SAMPLE_TEXT = t.sampleText || "";

  function tokenise(text) {
    const tokens = [];
    const re = /(\s+|[^\s]+)/g;
    let m;
    while ((m = re.exec(text)) !== null) {
      tokens.push({ raw: m[0], isSpace: /^\s+$/.test(m[0]) });
    }
    return tokens;
  }

  const tokens = tokenise(SAMPLE_TEXT);

  tokens.forEach((tok, i) => { tok.tokenIndex = i; });

  function buildSteps() {
    const steps = [];
    const dict = {};
    let codeCounter = 1;
    const compressed = [];

    tokens.forEach((tok) => {
      if (tok.isSpace) { return; }
      const word = tok.raw;
      if (!(word in dict)) {
        dict[word] = "W" + codeCounter++;
        compressed.push({ tokenIndex: tok.tokenIndex, code: null, isNew: true, word });
      } else {
        compressed.push({ tokenIndex: tok.tokenIndex, code: dict[word], isNew: false, word });
      }
      steps.push({
        tokenIndex: tok.tokenIndex,
        dict: Object.assign({}, dict),
        compressedSoFar: compressed.slice(),
        isNew: compressed[compressed.length - 1].isNew,
        word
      });
    });
    return steps;
  }

  const steps = buildSteps();

  const usedWords = new Set(
    steps.filter(s => !s.isNew).map(s => s.word)
  );

  let currentStep = -1;
  let playing = false;
  let timer = null;
  let speedMs = 700;
  const HEAD_ROWS = 3;
  const TAIL_ROWS = 2;
  let tableExpanded = false;

  container.classList.remove("widget-placeholder");
  container.innerHTML = `
    <div style="padding:0.75rem 0">

      <div style="margin-bottom:12px">
        <div style="font-size:12px;color:var(--muted);margin-bottom:5px;font-family:sans-serif">${t.originalLabel}</div>
        <div id="dict-text" style="
          line-height:1.9;
          font-size:0.95rem;
          border:1px solid var(--border);
          background:#fffdf8;
          padding:0.6rem 0.8rem;
          border-radius:3px;
        "></div>
      </div>

      <div style="display:grid;grid-template-columns:minmax(180px,260px) 1fr;gap:12px;margin-bottom:12px">

        <div>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:5px">
          <span style="font-size:12px;color:var(--muted);font-family:sans-serif">${t.dictTableLabel}</span>
          <button id="dict-table-toggle" type="button" style="font-size:11px;padding:2px 8px">${t.tableToggleExpand}</button>
        </div>
          <div style="border:1px solid var(--border);background:#fffdf8;border-radius:3px;overflow:hidden;min-height:80px">
            <table style="width:100%;border-collapse:collapse;font-size:0.88rem">
              <thead>
                <tr style="background:var(--bg)">
                  <th style="padding:5px 8px;text-align:left;border-bottom:1px solid var(--border);font-family:sans-serif;font-weight:600">${t.codeHeader}</th>
                  <th style="padding:5px 8px;text-align:left;border-bottom:1px solid var(--border);font-family:sans-serif;font-weight:600">${t.wordHeader}</th>
                </tr>
              </thead>
              <tbody id="dict-table-body"></tbody>
            </table>
          </div>
        </div>

        <div>
          <div style="font-size:12px;color:var(--muted);margin-bottom:5px;font-family:sans-serif">${t.compressedLabel}</div>
          <div id="dict-compressed" style="
            line-height:1.9;
            font-size:0.9rem;
            font-family:monospace;
            border:1px solid var(--border);
            background:#fffdf8;
            padding:0.6rem 0.8rem;
            border-radius:3px;
            min-height:80px;
            word-break:break-word;
          "></div>
        </div>
      </div>

      <div id="dict-stats" style="
        display:flex;gap:16px;flex-wrap:wrap;margin-bottom:12px;
        border:1px solid var(--border);background:#fffdf8;padding:0.55rem 0.8rem;border-radius:3px;
        font-size:0.88rem;font-family:sans-serif;align-items:center;
      ">
        ${t.originalChars}
        <span style="color:var(--muted)">→</span>
        ${t.compressedChars}
        <span id="dict-saving-badge" style="margin-left:4px"></span>
      </div>

      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:8px">
        <button id="dict-play-pause" type="button" style="padding:5px 14px;font-size:13px;min-width:80px">${t.playStart}</button>
        <button id="dict-step-back" type="button" style="padding:5px 14px;font-size:13px">${t.stepBackButton}</button>
        <button id="dict-step" type="button" style="padding:5px 14px;font-size:13px">${t.stepButton}</button>
        <button id="dict-reset" type="button" style="padding:5px 14px;font-size:13px">${t.resetButton}</button>
        <label style="font-size:12px;color:var(--muted);display:inline-flex;align-items:center;gap:6px;margin-left:4px;flex:1;min-width:180px">
          <span>${t.speedSlow}</span>
          <input id="dict-speed" type="range" min="200" max="1400" step="100" value="700" style="flex:1;accent-color:var(--accent)">
          <span>${t.speedFast}</span>
        </label>
        <span id="dict-progress" style="font-size:12px;color:var(--muted);margin-left:4px;font-family:sans-serif"></span>
      </div>
    </div>`;

  function escHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function renderText(activeTokenIndex) {
    const el = document.getElementById("dict-text");
    let html = "";
    tokens.forEach((tok) => {
      if (tok.isSpace) {
        html += tok.raw;
        return;
      }
      const isActive = tok.tokenIndex === activeTokenIndex;
      const processed = currentStep >= 0 &&
        steps.findIndex(s => s.tokenIndex === tok.tokenIndex) <= currentStep;
      let style = "";
      if (isActive) {
        style = "background:#fde68a;border-radius:2px;padding:1px 2px;font-weight:700";
      } else if (processed) {
        style = "color:var(--muted)";
      }
      html += style ? `<span style="${style}">${escHtml(tok.raw)}</span>` : escHtml(tok.raw);
    });
    el.innerHTML = html;
  }

  function renderDict(dict, newWord) {
    const tbody = document.getElementById("dict-table-body");
    const entries = Object.entries(dict);

    function rowHtml(word, code, isNew) {
      const rowStyle = isNew ? "background:rgba(28,93,140,0.12);" : "";
      return `<tr style="${rowStyle}">
        <td style="padding:4px 8px;border-bottom:1px solid var(--border);font-family:monospace;color:var(--accent);font-weight:600">${escHtml(code)}</td>
        <td style="padding:4px 8px;border-bottom:1px solid var(--border)">${escHtml(word)}</td>
      </tr>`;
    }

    let html = "";
    if (tableExpanded || entries.length <= HEAD_ROWS + TAIL_ROWS + 1) {
      entries.forEach(function(pair) {
        html += rowHtml(pair[0], pair[1], pair[0] === newWord);
      });
    } else {
      entries.slice(0, HEAD_ROWS).forEach(function(pair) {
        html += rowHtml(pair[0], pair[1], pair[0] === newWord);
      });
      html += `<tr><td colspan="2" style="padding:4px 8px;text-align:center;color:var(--muted);font-style:italic;border-bottom:1px solid var(--border)">…</td></tr>`;
      entries.slice(-TAIL_ROWS).forEach(function(pair) {
        html += rowHtml(pair[0], pair[1], pair[0] === newWord);
      });
    }

    tbody.innerHTML = html || `<tr><td colspan="2" style="padding:6px 8px;color:var(--muted);font-size:0.85rem">${t.noEntries}</td></tr>`;
  }

  function renderCompressed(compressedSoFar) {
    const el = document.getElementById("dict-compressed");
    if (!compressedSoFar || compressedSoFar.length === 0) {
      el.innerHTML = `<span style="color:var(--muted);font-size:0.85rem">${t.notCompressed}</span>`;
      return;
    }
    let html = "";
    let cIdx = 0;
    tokens.forEach((tok) => {
      if (tok.isSpace) {
        if (cIdx > 0 && cIdx <= compressedSoFar.length) html += " ";
        return;
      }
      if (cIdx >= compressedSoFar.length) return;
      const entry = compressedSoFar[cIdx];
      const isLast = cIdx === compressedSoFar.length - 1;
      const display = entry.code !== null ? entry.code : entry.word;
      const wasReplaced = entry.code !== null;
      let style = "";
      if (isLast) {
        style = wasReplaced
          ? "background:rgba(28,93,140,0.18);border-radius:2px;padding:1px 3px;font-weight:700;color:var(--accent)"
          : "background:#fde68a;border-radius:2px;padding:1px 3px;font-weight:700";
      } else if (wasReplaced) {
        style = "color:var(--accent)";
      }
      html += style ? `<span style="${style}">${escHtml(display)}</span>` : escHtml(display);
      cIdx++;
    });
    el.innerHTML = html;
  }

  function renderStats(compressedSoFar) {
    const origLen = SAMPLE_TEXT.length;
    document.getElementById("dict-orig-len").textContent = origLen;

    if (!compressedSoFar || compressedSoFar.length === 0) {
      document.getElementById("dict-comp-len").textContent = emptyValue;
      document.getElementById("dict-saving-badge").textContent = "";
      return;
    }

    let compStr = "";
    let cIdx = 0;
    let firstToken = true;
    tokens.forEach((tok) => {
      if (tok.isSpace) {
        if (!firstToken) compStr += " ";
        return;
      }
      if (cIdx < compressedSoFar.length) {
        const entry = compressedSoFar[cIdx];
        compStr += (entry.code !== null ? entry.code : entry.word);
        firstToken = false;
        cIdx++;
      }
    });

    const compLen = compStr.length;
    const saving = origLen - compLen;
    const pct = ((saving / origLen) * 100).toFixed(1);
    document.getElementById("dict-comp-len").textContent = compLen;
    const badge = document.getElementById("dict-saving-badge");
    if (saving > 0) {
      badge.innerHTML = `<span style="color:#3f8f4b;font-weight:600">${formatLang(t.savingSmaller, { count: saving, pct })}</span>`;
    } else if (saving < 0) {
      badge.innerHTML = `<span style="color:#a92f2f;font-weight:600">${formatLang(t.savingLarger, { count: Math.abs(saving), pct: Math.abs(pct) })}</span>`;
    } else {
      badge.innerHTML = `<span style="color:var(--muted)">${t.savingZero}</span>`;
    }
  }

  function renderProgress() {
    const el = document.getElementById("dict-progress");
    if (currentStep < 0) {
      el.textContent = formatLang(t.progressStart, { total: steps.length });
    } else {
      el.textContent = formatLang(t.progress, { current: currentStep + 1, total: steps.length });
    }
  }

  function render() {
    if (currentStep < 0) {
      renderText(undefined);
      renderDict({}, null);
      renderCompressed([]);
      renderStats([]);
      renderProgress();
      return;
    }
    const step = steps[currentStep];
    renderText(step.tokenIndex);
    const isFinal = currentStep === steps.length - 1;
    renderDict(step.dict, step.isNew ? step.word : null);
    renderCompressed(step.compressedSoFar);
    renderStats(step.compressedSoFar);
    renderProgress();
  }

  function advance() {
    if (currentStep < steps.length - 1) {
      currentStep++;
      render();
      return true;
    }
    return false;
  }

  function stepBack() {
    if (currentStep >= 0) {
      currentStep--;
      render();
    }
  }

  function stopPlay() {
    playing = false;
    clearInterval(timer);
    timer = null;
    const btn = document.getElementById("dict-play-pause");
    if (btn) btn.textContent = currentStep >= steps.length - 1 ? t.playDone : t.playContinue;
  }

  function startPlay() {
    playing = true;
    document.getElementById("dict-play-pause").textContent = t.playPause;
    timer = setInterval(() => {
      const hasMore = advance();
      if (!hasMore) { stopPlay(); }
    }, speedMs);
  }

  document.getElementById("dict-speed").addEventListener("input", function(e) {
    const raw = parseInt(e.target.value, 10) || 700;
    speedMs = 1600 - raw;
    if (playing) {
      stopPlay();
      startPlay();
    }
  });

  document.getElementById("dict-table-toggle").addEventListener("click", function() {
    tableExpanded = !tableExpanded;
    this.textContent = tableExpanded
      ? t.tableToggleCollapse
      : t.tableToggleExpand;
    render();
  });

  document.getElementById("dict-play-pause").addEventListener("click", () => {
    if (playing) {
      stopPlay();
    } else {
      if (currentStep >= steps.length - 1) { return; }
      startPlay();
    }
  });

  document.getElementById("dict-step-back").addEventListener("click", () => {
    if (playing) { stopPlay(); }
    stepBack();
  });

  document.getElementById("dict-step").addEventListener("click", () => {
    if (playing) { stopPlay(); }
    advance();
  });

  document.getElementById("dict-reset").addEventListener("click", () => {
    if (playing) { stopPlay(); }
    currentStep = -1;
    document.getElementById("dict-play-pause").textContent = t.playStart;
    render();
  });

  render();
}
