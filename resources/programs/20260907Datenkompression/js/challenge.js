window.WidgetChallengeRegistry = {};

function createChallengeBanner(container, options) {
  const opts = options || {};
  const successText = opts.successText || getLangValue(window.LANG_DE, "common.challengeSuccess") || "";
  const el = document.createElement("div");
  el.className = "challenge-banner";
  el.setAttribute("role", "status");
  el.setAttribute("aria-live", "polite");
  el.hidden = true;

  if (container instanceof HTMLElement) {
    container.insertBefore(el, container.firstChild);
  }

  return {
    element: el,
    check: function(metrics) {
      const results = (opts.challenges || []).map(function(ch) {
        return { challenge: ch, passed: !!ch.test(metrics) };
      });
      const passed = results.filter(function(r) { return r.passed; });
      const failed = results.filter(function(r) { return !r.passed; });

      if (opts.mode === "all" && passed.length === results.length && results.length > 0) {
        el.hidden = false;
        el.className = "challenge-banner challenge-banner--success";
        el.textContent = successText;
        return true;
      }
      if (opts.mode === "any" && passed.length > 0) {
        el.hidden = false;
        el.className = "challenge-banner challenge-banner--success";
        el.textContent = passed[0].challenge.successText || successText;
        return true;
      }
      if (failed.length > 0 && opts.showPending !== false) {
        el.hidden = false;
        el.className = "challenge-banner challenge-banner--pending";
        el.textContent = failed[0].challenge.prompt || opts.pendingText || "";
        return false;
      }
      el.hidden = true;
      return false;
    },
    reset: function() {
      el.hidden = true;
      el.textContent = "";
      el.className = "challenge-banner";
    }
  };
}

function createChallengePanel(container, options) {
  const opts = options || {};
  const challenges = opts.challenges || [];
  const title = opts.title || getLangValue(window.LANG_DE, "common.challengeTitle") || "";
  const resetLabel = opts.resetLabel || getLangValue(window.LANG_DE, "common.challengeReset") || "";
  const successText = opts.successText || getLangValue(window.LANG_DE, "common.challengeSuccess") || "";

  function escChallengeHtml(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  container.className = (container.className + " challenge-sidebar-inner").trim();
  container.innerHTML =
    '<p class="challenge-sidebar-title">' + escChallengeHtml(title) + '</p>' +
    '<div class="challenge-banner challenge-banner--success" hidden role="status" aria-live="polite"></div>' +
    '<ul class="challenge-sidebar-list"></ul>' +
    '<button type="button" class="challenge-sidebar-reset">' + escChallengeHtml(resetLabel) + '</button>';

  const listEl = container.querySelector(".challenge-sidebar-list");
  const resetBtn = container.querySelector(".challenge-sidebar-reset");
  const successBanner = container.querySelector(".challenge-banner");
  const itemEls = [];
  let wasAllPassed = false;

  challenges.forEach(function(ch) {
    const li = document.createElement("li");
    li.className = "challenge-sidebar-item";
    li.innerHTML =
      '<span class="challenge-sidebar-status" aria-hidden="true">○</span>' +
      '<span class="challenge-sidebar-text">' + escChallengeHtml(ch.prompt || "") + '</span>';
    listEl.appendChild(li);
    itemEls.push({
      el: li,
      status: li.querySelector(".challenge-sidebar-status"),
      challenge: ch,
      completed: false,
      wasShownPassed: false
    });
  });

  const stickyComplete = !!opts.stickyComplete;

  function setSuccessBanner(visible) {
    if (!successBanner) { return; }
    successBanner.hidden = !visible;
    successBanner.textContent = visible ? successText : "";
  }

  const api = {
    check: function(metrics) {
      let allPassed = true;
      itemEls.forEach(function(item) {
        const passed = !!item.challenge.test(metrics);
        if (stickyComplete && passed) {
          item.completed = true;
        }
        const showPassed = stickyComplete ? item.completed : passed;
        item.el.classList.toggle("challenge-sidebar-item--done", showPassed);
        item.el.classList.toggle("challenge-sidebar-item--failed", !showPassed);

        if (showPassed && !item.wasShownPassed) {
          item.el.classList.remove("challenge-sidebar-item--just-done");
          void item.el.offsetWidth;
          item.el.classList.add("challenge-sidebar-item--just-done");
        }
        if (!showPassed) {
          item.el.classList.remove("challenge-sidebar-item--just-done");
        }
        item.wasShownPassed = showPassed;

        item.status.textContent = showPassed ? "✓" : "✗";
        if (!showPassed) { allPassed = false; }
      });

      const newlyAllPassed = allPassed && !wasAllPassed && itemEls.length > 0;
      wasAllPassed = allPassed;
      setSuccessBanner(allPassed && itemEls.length > 0);

      if (typeof opts.onAllCompleteChange === "function") {
        opts.onAllCompleteChange(allPassed, newlyAllPassed);
      }

      return allPassed;
    },
    reset: function() {
      wasAllPassed = false;
      setSuccessBanner(false);
      itemEls.forEach(function(item) {
        item.completed = false;
        item.wasShownPassed = false;
        item.el.classList.remove(
          "challenge-sidebar-item--done",
          "challenge-sidebar-item--failed",
          "challenge-sidebar-item--just-done"
        );
        item.status.textContent = "○";
      });
      if (typeof opts.onReset === "function") {
        opts.onReset();
      }
    }
  };

  resetBtn.addEventListener("click", function() {
    api.reset();
  });

  return api;
}

function registerWidgetChallenges(widgetId, config) {
  if (!widgetId || !config) { return; }
  window.WidgetChallengeRegistry[widgetId] = config;
}

function refreshWidgetChallenges(widgetId) {
  const entry = window.WidgetChallengeRegistry[widgetId];
  if (!entry || !entry.panel) { return; }
  const metrics = entry.getMetrics ? entry.getMetrics() : {};
  entry.panel.check(metrics);
}
