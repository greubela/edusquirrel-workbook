function setupPhotoScenarioWidget(source) {
  const t = source || getWidgetLang("photoScenario");
  const container = document.getElementById("widget-s2-photo-scenario");
  if (!container) { return; }

  const scenarios = t.scenarios || [];
  const levels = t.levels || [];
  const choices = {};
  const touched = {};

  container.classList.remove("widget-placeholder");
  container.innerHTML =
    '<div class="photo-scenario-grid" id="photo-scenario-grid"></div>';

  const grid = document.getElementById("photo-scenario-grid");

  function escHtml(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function previewStyle(levelId) {
    const level = levels.find(function(l) { return l.id === levelId; }) || levels[0];
    const blur = level.blur || 0;
    const px = level.pixel || 1;
    const opacity = level.id === "high" ? 1 : level.id === "mid" ? 0.92 : 0.85;
    return "filter: blur(" + blur + "px); opacity: " + opacity + "; image-rendering: " + (px > 1 ? "pixelated" : "auto") + ";";
  }

  function previewHtml(scenarioId) {
    if (scenarioId === "tatort") {
      return (
        '<div class="photo-scenario-mock photo-scenario-mock--tatort">' +
          '<div class="photo-scenario-sky"></div>' +
          '<div class="photo-scenario-building"></div>' +
          '<div class="photo-scenario-plate">B-XY 4821</div>' +
        '</div>'
      );
    }
    if (scenarioId === "person") {
      return (
        '<div class="photo-scenario-mock photo-scenario-mock--person">' +
          '<div class="photo-scenario-face"></div>' +
          '<div class="photo-scenario-shoulders"></div>' +
        '</div>'
      );
    }
    return (
      '<div class="photo-scenario-mock photo-scenario-mock--doc">' +
        '<div class="photo-scenario-doc-line photo-scenario-doc-line--title"></div>' +
        '<div class="photo-scenario-doc-line"></div>' +
        '<div class="photo-scenario-doc-line"></div>' +
        '<div class="photo-scenario-doc-line photo-scenario-doc-line--short"></div>' +
      '</div>'
    );
  }

  function estimateSize(baseKb, levelId) {
    const level = levels.find(function(l) { return l.id === levelId; }) || levels[0];
    return Math.round(baseKb * (level.sizeFactor || 1));
  }

  function warningFor(scenario, levelId) {
    const min = scenario.minLevel || "low";
    const order = { high: 3, mid: 2, low: 1 };
    if ((order[levelId] || 0) < (order[min] || 0)) {
      return scenario.warnBelowMin || "";
    }
    return "";
  }

  function renderCard(scenario) {
    const levelId = choices[scenario.id] || "high";
    const sizeKb = estimateSize(scenario.baseSizeKb || 4000, levelId);
    const warn = warningFor(scenario, levelId);

    let buttons = "";
    levels.forEach(function(level) {
      const active = levelId === level.id ? " photo-scenario-level--active" : "";
      buttons +=
        '<button type="button" class="photo-scenario-level' + active + '" data-scenario="' +
        escHtml(scenario.id) + '" data-level="' + escHtml(level.id) + '">' +
        escHtml(level.label) + '</button>';
    });

    return (
      '<article class="photo-scenario-card" data-scenario="' + escHtml(scenario.id) + '">' +
        '<h4 class="photo-scenario-title">' + escHtml(scenario.label) + '</h4>' +
        '<p class="photo-scenario-desc">' + escHtml(scenario.description) + '</p>' +
        '<div class="photo-scenario-preview-wrap">' +
          '<div class="photo-scenario-preview-inner photo-scenario-level-visual--' + escHtml(levelId) + '" style="' + previewStyle(levelId) + '">' +
            previewHtml(scenario.id) +
          '</div>' +
        '</div>' +
        '<div class="photo-scenario-levels">' + buttons + '</div>' +
        '<p class="photo-scenario-meta">' +
          escHtml(t.sizeLabel || "Geschätzte Größe:") + ' <strong>' + sizeKb + ' KB</strong>' +
        '</p>' +
        '<p class="photo-scenario-critical">' + escHtml(scenario.criticalLabel) + '</p>' +
        (warn ? '<p class="photo-scenario-warn" role="status">' + escHtml(warn) + '</p>' : "") +
      '</article>'
    );
  }

  function render() {
    grid.innerHTML = scenarios.map(renderCard).join("");
    grid.querySelectorAll(".photo-scenario-level").forEach(function(btn) {
      btn.addEventListener("click", function() {
        choices[btn.dataset.scenario] = btn.dataset.level;
        touched[btn.dataset.scenario] = true;
        render();
        refreshWidgetChallenges("widget-s2-photo-scenario");
      });
    });
  }

  function getMetrics() {
    var allTouched = scenarios.every(function(s) { return touched[s.id]; });
    var hasWarning = scenarios.some(function(s) {
      return warningFor(s, choices[s.id] || "high") !== "";
    });
    return {
      allTouched: allTouched,
      hasWarning: hasWarning
    };
  }

  function resetWidget() {
    scenarios.forEach(function(s) {
      choices[s.id] = "high";
      touched[s.id] = false;
    });
    render();
    refreshWidgetChallenges("widget-s2-photo-scenario");
  }

  scenarios.forEach(function(s) {
    choices[s.id] = "high";
    touched[s.id] = false;
  });
  render();

  registerWidgetChallenges("widget-s2-photo-scenario", {
    challenges: [
      { prompt: "Wähle für jedes Szenario eine Kompressionsstufe.", test: function(m) { return m.allTouched; } },
      { prompt: "Probiere mindestens eine Stufe aus, die einen Warnhinweis auslöst.", test: function(m) { return m.hasWarning; } }
    ],
    getMetrics: getMetrics,
    onReset: resetWidget
  });
}
