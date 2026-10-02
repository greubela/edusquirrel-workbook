function setupReflectionCompare() {
  var introAnswer = document.getElementById("answer-intro");
  var compareEl = document.getElementById("reflection-compare-intro");
  if (!introAnswer || !compareEl) { return; }

  var lang = window.LANG_DE || {};
  var emptyText = getLangValue(lang, "final.task1.b.emptyIntro") || "";

  function sync() {
    var text = introAnswer.value.trim();
    compareEl.textContent = text || emptyText;
    compareEl.classList.toggle("reflection-compare--empty", !text);
  }

  introAnswer.addEventListener("input", sync);

  var finalSection = document.getElementById("final");
  if (finalSection) {
    var observer = new MutationObserver(sync);
    observer.observe(finalSection, { attributes: true, attributeFilter: ["hidden"] });
  }

  sync();
}
