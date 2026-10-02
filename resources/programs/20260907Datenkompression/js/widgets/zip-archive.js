function setupZipArchiveWidget(source) {
  const t = source || getWidgetLang("zipArchive");
  var container = document.getElementById("widget-s3-zip");
  if (!container) { return; }

  var ZIP_OVERHEAD = 320;
  var PROCESS_STEPS = t.processSteps || [
    "Speicher reservieren",
    "Datei öffnen",
    "Daten kopieren",
    "Datei schließen"
  ];
  var STEP_MS = 250;
  var COPY_STEP_INDEX = 2;
  // Archiv fertig, wenn beim Einzelversand etwa notiz_05 durch ist
  var ARCHIVE_DONE_AFTER_FILES = 5;

  var FILES = (t.files || []).map(function (f) {
    return {
      name: f.name,
      text: f.text,
      bytes: new Blob([f.text]).size
    };
  });

  var totalBytes = FILES.reduce(function (sum, f) { return sum + f.bytes; }, 0);
  var archiveBytes = totalBytes + ZIP_OVERHEAD;

  function formatBytes(n) {
    if (n >= 1024) {
      return (n / 1024).toLocaleString("de-DE", { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + " KB";
    }
    return n.toLocaleString("de-DE") + " Bytes";
  }

  function formatTotalHint(n) {
    if (n >= 1024) {
      return "~" + Math.round(n / 1024).toLocaleString("de-DE") + " KB";
    }
    return "~" + n.toLocaleString("de-DE") + " Bytes";
  }

  var overheadGlossHtml =
    '<span class="gloss" title="' + t.overheadGloss + '">' + t.overheadLabel + '</span>';
  var pendingMark = t.pendingMark || getWidgetLang("common").emptyValue || "";
  var processIdle = t.processIdle || "Bereit";
  var processDone = t.processDone || "Fertig";
  var faqVars = {
    sizeHint: formatTotalHint(totalBytes),
    overhead: overheadGlossHtml
  };

  var faqItems = (t.faq || []).map(function (item) {
    return {
      q: item.q,
      a: formatLang(item.a, faqVars)
    };
  });

  var faqHtml = faqItems.map(function (item) {
    return (
      '<details class="format-info-details zip-faq-item">' +
        "<summary>" + item.q + "</summary>" +
        '<p class="zip-faq-answer">' + item.a + "</p>" +
      "</details>"
    );
  }).join("");

  container.classList.remove("widget-placeholder");
  container.classList.add("zip-archive-widget");

  var fileRowsHtml = FILES.map(function (f, i) {
    return (
      '<div id="zip-file-' + i + '" class="zip-file-row">' +
        '<span class="zip-file-status">' + pendingMark + "</span>" +
        '<span class="zip-file-icon" aria-hidden="true">\uD83D\uDCC4</span>' +
        '<span class="zip-file-name">' + f.name + "</span>" +
        '<span class="zip-file-size">' + formatBytes(f.bytes) + "</span>" +
      "</div>"
    );
  }).join("");

  var treeRowsHtml = FILES.map(function (f, i) {
    var branch = i < FILES.length - 1 ? "\u251C\u2500" : "\u2514\u2500";
    return (
      '<div id="zip-inner-' + i + '" class="zip-inner-row">' +
        '<span class="zip-inner-status"></span>' +
        '<span class="zip-inner-branch">' + branch + "</span>" +
        '<span class="zip-inner-name">' + f.name + "</span>" +
      "</div>"
    );
  }).join("");

  container.innerHTML =
    '<div class="zip-sim-controls">' +
      '<button id="zip-sim-btn" type="button" class="zip-sim-btn">' + t.simulateButton + "</button>" +
    "</div>" +

    '<div id="zip-columns" class="zip-columns">' +

      '<div class="zip-panel">' +
        '<div class="zip-panel-head">' +
          '<div class="zip-panel-title">' + t.scenarioA + "</div>" +
          '<div class="zip-panel-desc">' + t.scenarioADesc + "</div>" +
        "</div>" +
        '<div class="zip-process" id="zip-process-a">' +
          '<span class="zip-process-label">' + processIdle + "</span>" +
        "</div>" +
        '<div class="zip-panel-body">' + fileRowsHtml + "</div>" +
        '<div id="zip-stats-a" class="zip-stats">' +
          '<div><span class="zip-stat-label">' + t.transfers + "</span> <strong id=\"zip-stat-a-transfers\">10</strong></div>" +
          '<div class="zip-stat-size"><span class="zip-stat-label">' + t.totalSize + "</span> <strong id=\"zip-stat-a-size\">" + formatBytes(totalBytes) + "</strong></div>" +
        "</div>" +
      "</div>" +

      '<div class="zip-panel">' +
        '<div class="zip-panel-head">' +
          '<div class="zip-panel-title">' + t.scenarioB + "</div>" +
          '<div class="zip-panel-desc">' + t.scenarioBDesc + "</div>" +
        "</div>" +
        '<div class="zip-process" id="zip-process-b">' +
          '<span class="zip-process-label">' + processIdle + "</span>" +
        "</div>" +
        '<div class="zip-panel-body zip-panel-body--archive">' +
          '<div id="zip-archive-header" class="zip-archive-header">' +
            '<div class="zip-archive-header-main">' +
              '<span id="zip-archive-status" class="zip-file-status">' + pendingMark + "</span>" +
              '<span class="zip-archive-icon" aria-hidden="true">\uD83D\uDCE6</span>' +
              '<span class="zip-archive-name">' + t.archiveName + "</span>" +
            "</div>" +
            '<span id="zip-archive-size" class="zip-file-size">' + formatBytes(archiveBytes) + "</span>" +
          "</div>" +
          '<div class="zip-tree">' + treeRowsHtml + "</div>" +
        "</div>" +
        '<div id="zip-stats-b" class="zip-stats">' +
          '<div><span class="zip-stat-label">' + t.transfers + "</span> <strong id=\"zip-stat-b-transfers\">1</strong></div>" +
          '<div class="zip-stat-size"><span class="zip-stat-label">' + t.totalSize + "</span> <strong id=\"zip-stat-b-size\">" + formatBytes(archiveBytes) + "</strong></div>" +
        "</div>" +
      "</div>" +

    "</div>" +

    '<details class="format-info-details zip-faq">' +
      "<summary>" + (t.faqSummary || "Hinweise") + "</summary>" +
      '<div class="zip-faq-list">' + faqHtml + "</div>" +
    "</details>";

  var simBtn = document.getElementById("zip-sim-btn");
  var archiveHeader = document.getElementById("zip-archive-header");
  var archiveStatus = document.getElementById("zip-archive-status");
  var statsA = document.getElementById("zip-stats-a");
  var statsB = document.getElementById("zip-stats-b");
  var processA = document.getElementById("zip-process-a");
  var processB = document.getElementById("zip-process-b");
  var animating = false;
  var timers = [];

  function clearTimers() {
    timers.forEach(function (timer) { clearTimeout(timer); });
    timers = [];
  }

  function setProcess(el, text, active) {
    el.innerHTML = '<span class="zip-process-label' + (active ? " zip-process-label--active" : "") + '">' + text + "</span>";
  }

  function resetVisuals() {
    for (var i = 0; i < FILES.length; i++) {
      var row = document.getElementById("zip-file-" + i);
      var status = row.querySelector(".zip-file-status");
      row.classList.remove("zip-row--done", "zip-row--active");
      status.textContent = pendingMark;
      status.classList.remove("zip-status--done");

      var inner = document.getElementById("zip-inner-" + i);
      var innerStatus = inner.querySelector(".zip-inner-status");
      inner.classList.remove("zip-row--done");
      innerStatus.textContent = "";
      innerStatus.classList.remove("zip-status--done");
    }
    archiveHeader.classList.remove("zip-archive-header--done", "zip-archive-header--active");
    archiveStatus.textContent = pendingMark;
    archiveStatus.classList.remove("zip-status--done");
    statsA.classList.remove("zip-stats--highlight");
    statsB.classList.remove("zip-stats--highlight");
    setProcess(processA, processIdle, false);
    setProcess(processB, processIdle, false);
  }

  function runSimulation() {
    if (animating) { return; }
    animating = true;
    simBtn.disabled = true;
    simBtn.classList.add("zip-sim-btn--busy");
    clearTimers();
    resetVisuals();

    var delay = 0;
    var fileCycleMs = PROCESS_STEPS.length * STEP_MS;
    var archiveTargetMs = ARCHIVE_DONE_AFTER_FILES * fileCycleMs;
    var shortStepsBeforeCopy = COPY_STEP_INDEX;
    var shortStepsAfterCopy = PROCESS_STEPS.length - COPY_STEP_INDEX - 1;
    var copyDurationMs = Math.max(
      STEP_MS,
      archiveTargetMs - (shortStepsBeforeCopy + shortStepsAfterCopy) * STEP_MS
    );

    // Scenario B: kurze Schritte normal, "Daten kopieren" stark gestreckt
    var bTime = 0;
    PROCESS_STEPS.forEach(function (stepLabel, stepIdx) {
      timers.push(setTimeout(function () {
        setProcess(processB, stepLabel, true);
        if (stepIdx === 0) {
          archiveHeader.classList.add("zip-archive-header--active");
        }
      }, bTime));
      bTime += stepIdx === COPY_STEP_INDEX ? copyDurationMs : STEP_MS;
    });

    var archiveDoneMs = bTime;
    timers.push(setTimeout(function () {
      archiveHeader.classList.remove("zip-archive-header--active");
      archiveHeader.classList.add("zip-archive-header--done");
      archiveStatus.textContent = "\u2713";
      archiveStatus.classList.add("zip-status--done");
      for (var j = 0; j < FILES.length; j++) {
        var inner = document.getElementById("zip-inner-" + j);
        var innerStatus = inner.querySelector(".zip-inner-status");
        inner.classList.add("zip-row--done");
        innerStatus.textContent = "\u2713";
        innerStatus.classList.add("zip-status--done");
      }
      setProcess(processB, processDone, false);
    }, archiveDoneMs));

    // Scenario A: process sequence for each file
    FILES.forEach(function (_f, i) {
      var fileStart = delay;
      PROCESS_STEPS.forEach(function (stepLabel, stepIdx) {
        timers.push(setTimeout(function () {
          var row = document.getElementById("zip-file-" + i);
          row.classList.add("zip-row--active");
          setProcess(processA, stepLabel, true);
        }, fileStart + stepIdx * STEP_MS));
      });

      timers.push(setTimeout(function () {
        var row = document.getElementById("zip-file-" + i);
        var status = row.querySelector(".zip-file-status");
        row.classList.remove("zip-row--active");
        row.classList.add("zip-row--done");
        status.textContent = "\u2713";
        status.classList.add("zip-status--done");
      }, fileStart + fileCycleMs));

      delay += fileCycleMs;
    });

    timers.push(setTimeout(function () {
      setProcess(processA, processDone, false);
      statsA.classList.add("zip-stats--highlight");
      statsB.classList.add("zip-stats--highlight");
      animating = false;
      simBtn.disabled = false;
      simBtn.classList.remove("zip-sim-btn--busy");
      simBtn.textContent = t.simulateAgain;
    }, delay + 200));
  }

  container.querySelectorAll(".gloss").forEach(function (g) {
    var tooltip = g.getAttribute("title");
    if (tooltip) {
      g.setAttribute("data-tooltip", tooltip);
      g.setAttribute("aria-label", tooltip);
      g.removeAttribute("title");
    }
  });

  simBtn.addEventListener("click", runSimulation);

  function updateColumns() {
    var cols = document.getElementById("zip-columns");
    if (!cols) { return; }
    cols.classList.toggle("zip-columns--stacked", window.matchMedia("(max-width: 640px)").matches);
  }
  updateColumns();
  window.addEventListener("resize", updateColumns);
}
