function applyAnswerSkeletons(source) {
  document.querySelectorAll("[data-answer-skeleton]").forEach(function(textarea) {
    var path = textarea.dataset.answerSkeleton;
    var skeleton = typeof getLangValue === "function" ? getLangValue(source, path) : undefined;
    if (typeof skeleton !== "string") {
      return;
    }
    // Leeres Feld oder noch unausgefülltes Gerüst (# TODO) → aktuelles Gerüst setzen.
    // So überschreibt die Browser-Wiederherstellung keine veraltete Gerüstversion,
    // ausgefüllte Schülerantworten bleiben aber erhalten.
    if (!textarea.value || textarea.value.indexOf("# TODO") !== -1) {
      textarea.value = skeleton;
      textarea.defaultValue = skeleton;
    }
  });
}

function setupTextareaTabIndent(selector) {
  const indent = "  ";

  document.querySelectorAll(selector || ".assumption-textarea").forEach(function(textarea) {
    textarea.addEventListener("keydown", function(e) {
      if (e.key !== "Tab") { return; }
      e.preventDefault();

      const value = textarea.value;
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const lineStart = value.lastIndexOf("\n", start - 1) + 1;
      const lineEndIndex = value.indexOf("\n", end);
      const blockEnd = lineEndIndex === -1 ? value.length : lineEndIndex;
      const lines = value.slice(lineStart, blockEnd).split("\n");

      if (e.shiftKey) {
        const updated = lines.map(function(line) {
          if (line.startsWith(indent)) { return line.slice(indent.length); }
          if (line.startsWith("\t")) { return line.slice(1); }
          return line.replace(/^ /, "");
        });
        const newBlock = updated.join("\n");
        const removed = value.slice(lineStart, blockEnd).length - newBlock.length;
        textarea.value = value.slice(0, lineStart) + newBlock + value.slice(blockEnd);
        textarea.selectionStart = Math.max(lineStart, start - Math.min(removed, indent.length));
        textarea.selectionEnd = Math.max(lineStart, end - removed);
      } else if (start === end) {
        textarea.value = value.slice(0, start) + indent + value.slice(end);
        const pos = start + indent.length;
        textarea.selectionStart = pos;
        textarea.selectionEnd = pos;
      } else {
        const newBlock = lines.map(function(line) { return indent + line; }).join("\n");
        textarea.value = value.slice(0, lineStart) + newBlock + value.slice(blockEnd);
        textarea.selectionStart = lineStart;
        textarea.selectionEnd = lineStart + newBlock.length;
      }

      textarea.dispatchEvent(new Event("input", { bubbles: true }));
    });
  });
}

function setupHiddenFollowups() {
  document.querySelectorAll("[data-reveals]").forEach(function(textarea) {
    var targetId = textarea.dataset.reveals;
    var target = document.getElementById(targetId);
    if (!target) { return; }
    textarea.addEventListener("input", function() {
      if (textarea.value.trim().length > 0) {
        target.removeAttribute("hidden");
      }
    });
  });
}

function setupGlossTooltips() {
  document.querySelectorAll(".gloss").forEach(function(el) {
    el.addEventListener("mouseenter", function() {
      var after = window.getComputedStyle(this, "::after");
      // force layout so ::after is rendered
      var origDisplay = this.style.display;
      this.classList.remove("gloss-tt-bottom", "gloss-tt-left", "gloss-tt-right");

      var rect = this.getBoundingClientRect();
      var tooltipWidth = Math.min(420, window.innerWidth * 0.85);

      // Check horizontal overflow
      var centerLeft = rect.left + rect.width / 2 - tooltipWidth / 2;
      if (centerLeft < 8) {
        this.classList.add("gloss-tt-left");
      } else if (centerLeft + tooltipWidth > window.innerWidth - 8) {
        this.classList.add("gloss-tt-right");
      }

      // Check vertical overflow (tooltip appears above by default)
      if (rect.top < 100) {
        this.classList.add("gloss-tt-bottom");
      }
    });
  });
}

function setupMaterialFolderDownload() {
  var zipPath = "Material.zip";

  function triggerDownload() {
    var link = document.createElement("a");
    link.href = zipPath;
    link.download = "Material.zip";
    link.rel = "noopener";
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  function onActivate(event) {
    var target = event.target.closest(".gloss-material-folder");
    if (!target) { return; }
    event.preventDefault();
    triggerDownload();
  }

  document.addEventListener("click", onActivate);
  document.addEventListener("keydown", function(event) {
    if (event.key !== "Enter" && event.key !== " ") { return; }
    onActivate(event);
  });
}

function setupHelpToggles(source) {
  document.querySelectorAll(".help-toggle").forEach(function(toggle) {
    var targetId = toggle.getAttribute("aria-controls");
    if (!targetId) { return; }
    var box = document.getElementById(targetId);
    if (!box) { return; }
    toggle.addEventListener("click", function() {
      var isHidden = box.hasAttribute("hidden");
      if (isHidden) {
        box.removeAttribute("hidden");
        toggle.setAttribute("aria-expanded", "true");
        var hideLabel = getLangValue(source, "common.hideHint");
        if (hideLabel) { toggle.textContent = hideLabel; }
      } else {
        box.setAttribute("hidden", "");
        toggle.setAttribute("aria-expanded", "false");
        var showLabel = getLangValue(source, "common.showHint");
        if (showLabel) { toggle.textContent = showLabel; }
      }
    });
  });
}
