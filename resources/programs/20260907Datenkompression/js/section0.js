function setupEthicsForm(source) {
  const form = document.getElementById("ethics-form");
  if (!form) { return; }

  const followupWrap = document.getElementById("ethics-followup-wrap");
  const followupLabel = document.getElementById("ethics-followup-label");
  const followupTextarea = document.getElementById("answer-intro-ethics");

  const followups = {
    yes: getLangValue(source, "intro.ethicsFollowupYes") || "",
    no: getLangValue(source, "intro.ethicsFollowupNo") || "",
    maybe: getLangValue(source, "intro.ethicsFollowupMaybe") || ""
  };

  form.querySelectorAll("input[name=ethics-choice]").forEach(function(radio) {
    radio.addEventListener("change", function() {
      const value = radio.value;
      if (followupWrap) { followupWrap.removeAttribute("hidden"); }
      if (followupLabel) { followupLabel.textContent = followups[value] || ""; }
      if (followupTextarea instanceof HTMLTextAreaElement) {
        followupTextarea.focus();
      }
    });
  });
}
