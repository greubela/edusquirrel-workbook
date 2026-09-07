function setupSortWidget(containerId, source) {
  const shared = getWidgetLang("sortWidget");
  const t = Object.assign({}, shared, source || {});
  const container = document.getElementById(containerId);
  if (!container) { return; }

  const items = t.items || [];
  const columns = t.columns || [];
  const placements = {};
  const guesses = {};
  const reasons = {};
  const revealed = {};
  let submitted = false;
  let errorCount = 0;

  container.classList.remove("widget-placeholder");
  container.innerHTML =
    '<div class="sort-widget">' +
      '<div class="sort-widget-board" id="' + containerId + '-board"></div>' +
      '<div class="sort-widget-pool" id="' + containerId + '-pool">' +
        '<p class="sort-widget-pool-label">' + escHtml(t.poolLabel) + '</p>' +
        '<div class="sort-widget-cards" id="' + containerId + '-cards"></div>' +
      '</div>' +
      '<div class="sort-widget-actions">' +
        '<button type="button" class="sort-widget-submit">' + escHtml(t.submitButton) + '</button>' +
        '<button type="button" class="sort-widget-reset">' + escHtml(t.resetButton) + '</button>' +
        '<span class="sort-widget-errors" id="' + containerId + '-errors" hidden></span>' +
      '</div>' +
      '<div class="sort-widget-summary" id="' + containerId + '-summary" hidden></div>' +
    '</div>';

  const boardEl = document.getElementById(containerId + "-board");
  const cardsEl = document.getElementById(containerId + "-cards");
  const errorsEl = document.getElementById(containerId + "-errors");
  const summaryEl = document.getElementById(containerId + "-summary");
  const submitBtn = container.querySelector(".sort-widget-submit");
  const resetBtn = container.querySelector(".sort-widget-reset");

  function showError(message) {
    const overlay = document.getElementById("widget-fullscreen-overlay");
    if (overlay && overlay.contains(container)) {
      let banner = overlay.querySelector(".widget-fullscreen-banner");
      if (!banner) {
        banner = document.createElement("div");
        banner.className = "widget-fullscreen-banner";
        banner.setAttribute("role", "alert");
        const header = overlay.querySelector(".widget-fullscreen-header");
        const closeBtn = overlay.querySelector(".widget-fullscreen-close");
        if (header && closeBtn) {
          header.insertBefore(banner, closeBtn);
        }
      }
      banner.hidden = !message;
      banner.textContent = message || "";
    }
    if (errorsEl && message && submitted) {
      errorsEl.hidden = false;
      errorsEl.textContent = message;
    }
  }

  function clearError() {
    showError("");
    if (errorsEl && !submitted) {
      errorsEl.hidden = true;
      errorsEl.textContent = "";
    }
  }

  function escHtml(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function columnLabel(id) {
    const col = columns.find(function(c) { return c.id === id; });
    return col ? col.label : id;
  }

  function renderBoard() {
    let html = '<div class="sort-widget-columns">';
    columns.forEach(function(col) {
      html +=
        '<div class="sort-widget-column" data-column="' + escHtml(col.id) + '">' +
          '<p class="sort-widget-column-title">' + escHtml(col.label) + '</p>' +
          '<div class="sort-widget-dropzone" data-drop="' + escHtml(col.id) + '"></div>' +
        '</div>';
    });
    html += '</div>';
    boardEl.innerHTML = html;

    boardEl.querySelectorAll(".sort-widget-dropzone").forEach(function(zone) {
      zone.addEventListener("dragover", function(e) {
        e.preventDefault();
        zone.classList.add("sort-widget-dropzone--over");
      });
      zone.addEventListener("dragleave", function() {
        zone.classList.remove("sort-widget-dropzone--over");
      });
      zone.addEventListener("drop", function(e) {
        e.preventDefault();
        zone.classList.remove("sort-widget-dropzone--over");
        if (submitted) { return; }
        const itemId = e.dataTransfer.getData("text/plain");
        if (itemId) { assignItem(itemId, zone.dataset.drop); }
      });
    });
  }

  function renderCard(item) {
    const placed = placements[item.id];
    const rev = revealed[item.id];
    const isCorrect = rev && placed === item.correct;
    const needsGuess = item.needsGuess && !guesses[item.id];

    let status = "";
    if (rev) {
      status = isCorrect
        ? '<span class="sort-widget-status sort-widget-status--ok">✓</span>'
        : '<span class="sort-widget-status sort-widget-status--bad">✗</span>';
    }

    const card = document.createElement("div");
    card.className = "sort-widget-card" +
      (rev ? (isCorrect ? " sort-widget-card--ok" : " sort-widget-card--bad") : "") +
      (placed ? " sort-widget-card--placed" : "");
    card.draggable = !submitted && !placed;
    card.dataset.itemId = item.id;

    card.innerHTML =
      '<div class="sort-widget-card-head">' +
        '<strong>' + escHtml(item.label) + '</strong>' + status +
      '</div>' +
      '<p class="sort-widget-card-desc">' + escHtml(item.desc || "") + '</p>';

    if (item.needsGuess && !submitted) {
      const guessWrap = document.createElement("div");
      guessWrap.className = "sort-widget-guess sort-widget-guess--required";
      guessWrap.innerHTML =
        '<p class="sort-widget-guess-note">' + escHtml(t.guessNote) + '</p>' +
        '<label class="sort-widget-guess-label">' + escHtml(t.guessPrompt) + '</label>' +
        '<textarea class="sort-widget-guess-input" rows="2" data-guess-for="' + escHtml(item.id) + '">' +
          escHtml(guesses[item.id] || "") +
        '</textarea>';
      card.appendChild(guessWrap);
      const ta = guessWrap.querySelector("textarea");
      ta.addEventListener("input", function() {
        guesses[item.id] = ta.value;
      });
    }

    if (!submitted && !placed) {
      const reasonWrap = document.createElement("div");
      reasonWrap.className = "sort-widget-guess";
      reasonWrap.innerHTML =
        '<label class="sort-widget-guess-label">' + escHtml(t.reasonPrompt) + '</label>' +
        '<textarea class="sort-widget-reason-input" rows="2" data-reason-for="' + escHtml(item.id) + '">' +
          escHtml(reasons[item.id] || "") +
        '</textarea>';
      card.appendChild(reasonWrap);
      const rta = reasonWrap.querySelector("textarea");
      rta.addEventListener("input", function() {
        reasons[item.id] = rta.value;
      });
    }

    if (rev) {
      const fb = document.createElement("div");
      fb.className = "sort-widget-feedback";
      fb.textContent = isCorrect ? item.feedbackCorrect : item.feedbackWrong;
      card.appendChild(fb);
    }

    if (!submitted && !placed) {
      card.addEventListener("dragstart", function(e) {
        e.dataTransfer.setData("text/plain", item.id);
        card.classList.add("sort-widget-card--dragging");
      });
      card.addEventListener("dragend", function() {
        card.classList.remove("sort-widget-card--dragging");
      });
      columns.forEach(function(col) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "sort-widget-assign-btn";
        btn.textContent = (t.columnPrefix || "") + col.label;
        btn.addEventListener("click", function() { assignItem(item.id, col.id); });
        card.appendChild(btn);
      });
    }

    return card;
  }

  function assignItem(itemId, columnId) {
    if (submitted) { return; }
    const item = items.find(function(i) { return i.id === itemId; });
    if (!item) { return; }
    if (item.needsGuess && !(guesses[itemId] || "").trim()) {
      showError(t.guessRequired);
      return;
    }
    if (!(reasons[itemId] || "").trim()) {
      showError(t.reasonRequired);
      return;
    }
    placements[itemId] = columnId;
    clearError();
    renderAll();
  }

  function renderPlacedCards() {
    boardEl.querySelectorAll(".sort-widget-dropzone").forEach(function(zone) {
      zone.innerHTML = "";
      const colId = zone.dataset.drop;
      items.forEach(function(item) {
        if (placements[item.id] === colId) {
          const mini = document.createElement("div");
          mini.className = "sort-widget-placed-card";
          mini.textContent = item.label;
          if (revealed[item.id]) {
            mini.classList.add(placements[item.id] === item.correct
              ? "sort-widget-placed-card--ok"
              : "sort-widget-placed-card--bad");
          }
          zone.appendChild(mini);
        }
      });
    });
  }

  function renderAll() {
    cardsEl.innerHTML = "";
    items.forEach(function(item) {
      if (!placements[item.id]) {
        cardsEl.appendChild(renderCard(item));
      }
    });
    renderPlacedCards();
  }

  function handleSubmit() {
    const unplaced = items.filter(function(i) { return !placements[i.id]; });
    if (unplaced.length > 0) {
      showError(t.placeAllFirst);
      return;
    }
    const missingGuess = items.some(function(i) {
      return i.needsGuess && !(guesses[i.id] || "").trim();
    });
    if (missingGuess) {
      showError(t.guessRequired);
      return;
    }

    const missingReason = items.some(function(i) {
      return !(reasons[i.id] || "").trim();
    });
    if (missingReason) {
      showError(t.reasonRequired);
      return;
    }

    submitted = true;
    clearError();
    errorCount = items.filter(function(i) { return placements[i.id] !== i.correct; }).length;
    items.forEach(function(i) { revealed[i.id] = true; });

    errorsEl.hidden = false;
    errorsEl.textContent = formatLang(t.errorCountTemplate, { count: errorCount });

    const correctCount = items.length - errorCount;
    let wrongHtml = "";
    if (errorCount > 0 && t.summaryWrongLine) {
      wrongHtml = '<ul class="sort-widget-wrong-list">';
      items.forEach(function(i) {
        if (placements[i.id] !== i.correct) {
          wrongHtml += "<li>" + formatLang(t.summaryWrongLine, {
            label: i.label,
            answer: columnLabel(placements[i.id]),
            correct: columnLabel(i.correct)
          }) + "<br><span class=\"sort-widget-wrong-hint\">" + escHtml(i.feedbackWrong || "") + "</span></li>";
        }
      });
      wrongHtml += "</ul>";
    }

    summaryEl.hidden = false;
    summaryEl.innerHTML =
      '<strong>' + formatLang(t.summaryCorrect, {
        correct: correctCount,
        total: items.length
      }) + '</strong>' +
      (errorCount === 0
        ? '<p class="sort-widget-summary-all">' + escHtml(t.summaryAllCorrect) + '</p>'
        : wrongHtml);

    renderAll();
  }

  function handleReset() {
    submitted = false;
    errorCount = 0;
    Object.keys(placements).forEach(function(k) { delete placements[k]; });
    Object.keys(revealed).forEach(function(k) { delete revealed[k]; });
    Object.keys(reasons).forEach(function(k) { delete reasons[k]; });
    Object.keys(guesses).forEach(function(k) { delete guesses[k]; });
    errorsEl.hidden = true;
    summaryEl.hidden = true;
    clearError();
    renderAll();
  }

  submitBtn.addEventListener("click", handleSubmit);
  resetBtn.addEventListener("click", handleReset);

  renderBoard();
  renderAll();
}

function setupEfficiencyWidget(source) {
  const t = source || getWidgetLang("efficiency");
  const adapted = Object.assign({}, t, {
    columns: t.columns || [
      { id: "rle", label: t.optionRle },
      { id: "dict", label: t.optionDict },
      { id: "none", label: t.optionNone }
    ],
    items: t.items || t.files || []
  });
  setupSortWidget("widget-s1-efficiency", adapted);
}

function setupLossyClosingWidget(source) {
  const t = source || getWidgetLang("lossyClosing");
  const adapted = Object.assign({}, t, {
    columns: t.columns || [
      { id: "lossy", label: t.optionLossy },
      { id: "lossless", label: t.optionLossless },
      { id: "none", label: t.optionNone }
    ],
    items: t.items || t.files || []
  });
  setupSortWidget("widget-s2-lossy-closing", adapted);
}
