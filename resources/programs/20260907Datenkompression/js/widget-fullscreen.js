function setShellPreviewMode(body, enabled) {
  if (!body) { return; }
  if (enabled) {
    body.classList.add("widget-shell-body--preview");
    body.setAttribute("inert", "");
  } else {
    body.classList.remove("widget-shell-body--preview");
    body.removeAttribute("inert");
  }
}

function setupWidgetFullscreen(langData) {
  const openLabel = getLangValue(langData, "common.openSimulation") || "";
  const closeLabel = getLangValue(langData, "common.closeSimulation") || "";
  const challengeTitle = getLangValue(langData, "common.challengeTitle") || "";
  const challengeReset = getLangValue(langData, "common.challengeReset") || "";
  const challengeCloseWarning = getLangValue(langData, "common.challengeCloseWarning") || "";
  const challengeCloseOpen = getLangValue(langData, "common.challengeCloseOpen") || closeLabel;

  document.querySelectorAll(".widget-shell").forEach(function(shell) {
    if (shell.hasAttribute("data-no-fullscreen")) { return; }

    const body = shell.querySelector(".widget-shell-body");
    if (!body) { return; }

    setShellPreviewMode(body, true);

    function open() {
      openWidgetOverlay(shell, {
        closeLabel: closeLabel,
        challengeTitle: challengeTitle,
        challengeReset: challengeReset,
        challengeCloseWarning: challengeCloseWarning,
        challengeCloseOpen: challengeCloseOpen
      });
    }

    const scenario = shell.closest(".fss-scenario");
    if (scenario) {
      const launchBtn = scenario.querySelector(".fss-scenario-open");
      if (launchBtn) {
        launchBtn.addEventListener("click", function(e) {
          e.preventDefault();
          open();
        });
      }
      return;
    }

    let toolbar = shell.querySelector(".widget-shell-toolbar");
    if (!toolbar) {
      toolbar = document.createElement("div");
      toolbar.className = "widget-shell-toolbar";
      shell.insertBefore(toolbar, body);
    }

    let openBtn = toolbar.querySelector(".widget-fullscreen-open");
    if (!openBtn) {
      openBtn = document.createElement("button");
      openBtn.type = "button";
      openBtn.className = "widget-fullscreen-open";
      openBtn.textContent = openLabel;
      toolbar.appendChild(openBtn);
    }

    openBtn.addEventListener("click", function(e) {
      e.stopPropagation();
      open();
    });
    shell.addEventListener("click", function(e) {
      if (e.target.closest(".widget-shell-toolbar")) { return; }
      open();
    });
  });
}

function findWidgetIdInShell(body) {
  if (!body) { return null; }
  const el = body.querySelector("[id^='widget-']");
  return el ? el.id : null;
}

function openWidgetOverlay(shell, options) {
  if (document.getElementById("widget-fullscreen-overlay")) { return; }

  const opts = options || {};
  const closeLabel = opts.closeLabel || "";
  const challengeTitle = opts.challengeTitle || "";
  const challengeReset = opts.challengeReset || "";
  const challengeCloseWarning = opts.challengeCloseWarning || "";
  const challengeCloseOpen = opts.challengeCloseOpen || closeLabel;

  const body = shell.querySelector(".widget-shell-body");
  if (!body) { return; }

  setShellPreviewMode(body, false);

  const widgetId = findWidgetIdInShell(body);
  const challengeEntry = widgetId && window.WidgetChallengeRegistry
    ? window.WidgetChallengeRegistry[widgetId]
    : null;

  const overlay = document.createElement("div");
  overlay.id = "widget-fullscreen-overlay";
  overlay.className = "widget-fullscreen-overlay";
  overlay.setAttribute("role", "dialog");
  overlay.setAttribute("aria-modal", "true");

  const panel = document.createElement("div");
  panel.className = "widget-fullscreen-panel" + (challengeEntry ? " widget-fullscreen-panel--with-sidebar" : "");

  const header = document.createElement("div");
  header.className = "widget-fullscreen-header";

  const closeBtn = document.createElement("button");
  closeBtn.type = "button";
  closeBtn.className = "widget-fullscreen-close";
  closeBtn.textContent = closeLabel;
  header.appendChild(closeBtn);

  const bodyRow = document.createElement("div");
  bodyRow.className = "widget-fullscreen-body-row";

  let sidebarPanel = null;

  function updateCloseButton() {
    if (!challengeEntry) {
      closeBtn.textContent = closeLabel;
      closeBtn.classList.remove("widget-fullscreen-close--challenges-open");
      return;
    }
    const done = !!challengeEntry.allCompletedThisSession;
    closeBtn.textContent = done ? closeLabel : challengeCloseOpen;
    closeBtn.classList.toggle("widget-fullscreen-close--challenges-open", !done);
    panel.classList.toggle("widget-fullscreen-panel--challenges-done", done);
  }

  if (challengeEntry && challengeEntry.challenges) {
    const sidebar = document.createElement("aside");
    sidebar.className = "challenge-sidebar";
    sidebar.setAttribute("aria-label", challengeTitle);
    bodyRow.appendChild(sidebar);

    const userOnReset = challengeEntry.onReset;
    sidebarPanel = createChallengePanel(sidebar, {
      title: challengeTitle,
      resetLabel: challengeReset,
      challenges: challengeEntry.challenges,
      stickyComplete: challengeEntry.stickyComplete,
      onReset: function() {
        challengeEntry.allCompletedThisSession = false;
        updateCloseButton();
        if (typeof userOnReset === "function") {
          userOnReset();
        }
      },
      onAllCompleteChange: function(allPassed, newlyAllPassed) {
        if (allPassed) {
          challengeEntry.allCompletedThisSession = true;
        }
        if (newlyAllPassed) {
          panel.classList.remove("widget-fullscreen-panel--challenges-flash");
          void panel.offsetWidth;
          panel.classList.add("widget-fullscreen-panel--challenges-flash");
        }
        updateCloseButton();
      }
    });
    challengeEntry.panel = sidebarPanel;
  }

  const content = document.createElement("div");
  content.className = "widget-fullscreen-content";
  bodyRow.appendChild(content);

  panel.appendChild(header);
  panel.appendChild(bodyRow);
  overlay.appendChild(panel);
  document.body.appendChild(overlay);

  const placeholder = document.createElement("div");
  placeholder.className = "widget-shell-body widget-shell-body--overlay";
  content.appendChild(placeholder);

  while (body.firstChild) {
    placeholder.appendChild(body.firstChild);
  }

  shell._fsPlaceholder = placeholder;
  shell._fsOriginalBody = body;
  shell._fsWidgetId = widgetId;

  document.body.classList.add("widget-fullscreen-active");
  updateCloseButton();

  function syncChallenges() {
    if (!challengeEntry || !sidebarPanel) { return; }
    const metrics = challengeEntry.getMetrics ? challengeEntry.getMetrics() : {};
    sidebarPanel.check(metrics);
  }

  if (challengeEntry && sidebarPanel) {
    syncChallenges();
    shell._fsChallengeInterval = window.setInterval(syncChallenges, 400);
  }

  // Let canvas widgets remeasure after the overlay layout is ready
  window.requestAnimationFrame(function() {
    window.dispatchEvent(new Event("resize"));
  });

  function doClose() {
    if (shell._fsChallengeInterval) {
      window.clearInterval(shell._fsChallengeInterval);
      shell._fsChallengeInterval = null;
    }
    if (challengeEntry) {
      challengeEntry.panel = null;
    }
    const ph = shell._fsPlaceholder;
    const orig = shell._fsOriginalBody;
    if (ph && orig) {
      while (ph.firstChild) {
        orig.appendChild(ph.firstChild);
      }
      setShellPreviewMode(orig, true);
    }
    overlay.remove();
    document.body.classList.remove("widget-fullscreen-active");
    document.removeEventListener("keydown", onKey);
    shell._fsPlaceholder = null;
    shell._fsOriginalBody = null;
    shell._fsWidgetId = null;
    window.requestAnimationFrame(function() {
      window.dispatchEvent(new Event("resize"));
    });
  }

  function requestClose() {
    if (challengeEntry && !challengeEntry.allCompletedThisSession && challengeCloseWarning) {
      if (!window.confirm(challengeCloseWarning)) {
        return;
      }
    }
    doClose();
  }

  function onKey(e) {
    if (e.key === "Escape") { requestClose(); }
  }

  closeBtn.addEventListener("click", requestClose);
  overlay.addEventListener("click", function(e) {
    if (e.target === overlay) { requestClose(); }
  });
  document.addEventListener("keydown", onKey);
  closeBtn.focus();
}
