function setupIntroCalculator(source) {
  const bitrateInput = document.getElementById("bitrateInput");
  const durationInput = document.getElementById("durationInput");
  const calcButton = document.getElementById("introCalcBtn");
  const result = document.getElementById("introCalcResult");
  const barFill = document.getElementById("sdCardBarFill");
  const barText = document.getElementById("sdCardBarText");

  const photoSizeInput = document.getElementById("photoSizeInput");
  const photoCalcBtn = document.getElementById("photoCalcBtn");
  const photoResult = document.getElementById("photoCalcResult");

  if (!(bitrateInput instanceof HTMLInputElement) ||
      !(durationInput instanceof HTMLInputElement) ||
      !(calcButton instanceof HTMLButtonElement) ||
      !(result instanceof HTMLElement) ||
      !(barFill instanceof HTMLElement) ||
      !(barText instanceof HTMLElement)) {
    return;
  }

  const sizeTemplate = getLangValue(source, "section0.resultTemplate") || "";
  const invalidText = getLangValue(source, "section0.resultInvalid") || "";
  const photoTemplate = getLangValue(source, "section0.photoResultTemplate") || "";
  const photoInvalid = getLangValue(source, "section0.photoResultInvalid") || "";
  const sdCardFill = getLangValue(source, "section0.sdCardFill") || "";
  const cardSizeMb = 2 * 1024 * 8;
  const cardSizeMB = 2000;

  const calculateVideo = () => {
    const bitrate = Number.parseFloat(bitrateInput.value);
    const minutes = Number.parseFloat(durationInput.value);

    if (!Number.isFinite(bitrate) || !Number.isFinite(minutes) || bitrate <= 0 || minutes <= 0) {
      result.textContent = invalidText;
      barFill.style.width = "0%";
      barFill.classList.remove("sd-card-bar-fill--overflow");
      barText.textContent = "";
      return;
    }

    const sizeMb = bitrate * minutes * 60;
    const sizeMB = sizeMb / 8;
    const percent = (sizeMb / cardSizeMb) * 100;
    const videoCount = Math.floor(cardSizeMb / sizeMb);
    const clampedPercent = Math.min(percent, 100);

    result.textContent = sizeTemplate
      .replace("{sizeMb}", sizeMb.toFixed(1))
      .replace("{sizeMB}", sizeMB.toFixed(1))
      .replace("{percent}", percent.toFixed(1))
      .replace("{videoCount}", videoCount.toString());
    barFill.style.width = clampedPercent + "%";
    barFill.classList.toggle("sd-card-bar-fill--overflow", sizeMb > cardSizeMb);
    barText.textContent = sdCardFill.replace("{percent}", percent.toFixed(1));
  };

  const calculatePhotos = () => {
    if (!(photoSizeInput instanceof HTMLInputElement) || !(photoResult instanceof HTMLElement)) {
      return;
    }
    const sizeMB = Number.parseFloat(photoSizeInput.value);
    if (!Number.isFinite(sizeMB) || sizeMB <= 0) {
      photoResult.textContent = photoInvalid;
      return;
    }
    const photoCount = Math.floor(cardSizeMB / sizeMB);
    photoResult.textContent = photoTemplate
      .replace("{photoCount}", photoCount.toString())
      .replace("{sizeMB}", sizeMB.toFixed(1))
      .replace("{totalMB}", (photoCount * sizeMB).toFixed(0));
  };

  calcButton.addEventListener("click", calculateVideo);
  if (photoCalcBtn instanceof HTMLButtonElement) {
    photoCalcBtn.addEventListener("click", calculatePhotos);
  }
}
