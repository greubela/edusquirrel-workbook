function setupBlockAvgWidget(source) {
  const t = source || getWidgetLang("blockAvg");
  const container = document.getElementById("widget-s2-blockavg");
  if (!container) { return; }

  let origPixels = null;
  let yChannel = null;
  let cbChannel = null;
  let crChannel = null;
  let imgW = 0;
  let imgH = 0;

  container.classList.remove("widget-placeholder");
  container.style.cssText = "border:1px solid var(--border);background:#fffdf8;padding:0.8rem 1rem 1rem;border-radius:3px;margin:0.75rem 0";
  container.innerHTML = `
    <div id="bav-image-wrap" style="text-align:center;margin-bottom:14px">
      <canvas id="bav-canvas" style="display:block;width:100%;max-width:100%;height:auto;border:1px solid var(--border);border-radius:3px;margin:0 auto"></canvas>
    </div>

    <div style="display:grid;gap:10px">

      <div>
        <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:4px">
          <label for="bav-brightness" style="font-size:13px;font-family:sans-serif">
            ${t.brightnessLabel}
          </label>
          <span id="bav-brightness-val" style="font-family:monospace;font-size:13px;color:var(--accent);font-weight:600">${t.blockSingle}</span>
        </div>
        <input id="bav-brightness" type="range" min="1" max="8" step="1" value="8"
               style="width:100%;accent-color:var(--accent)">
      </div>

      <div>
        <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:4px">
          <label for="bav-colorbits" style="font-size:13px;font-family:sans-serif">
            ${t.colorLabel}
          </label>
          <span id="bav-colorbits-val" style="font-family:monospace;font-size:13px;color:var(--accent);font-weight:600">${t.blockSingle}</span>
        </div>
        <input id="bav-colorbits" type="range" min="1" max="8" step="1" value="8"
               style="width:100%;accent-color:var(--accent)">
      </div>

      <p id="bav-size-estimate" style="font-size:12px;color:var(--muted);margin:0;font-family:sans-serif"></p>

    </div>
  `;

  const canvas = document.getElementById("bav-canvas");
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  const srcCanvas = document.createElement("canvas");
  const srcCtx = srcCanvas.getContext("2d", { willReadFrequently: true });
  const brightSlider = document.getElementById("bav-brightness");
  const colorSlider = document.getElementById("bav-colorbits");
  const brightVal = document.getElementById("bav-brightness-val");
  const colorVal = document.getElementById("bav-colorbits-val");
  const imageWrap = document.getElementById("bav-image-wrap");
  const sizeEstimate = document.getElementById("bav-size-estimate");

  function estimateSizePct(yBlock, cBlock) {
    if (yBlock <= 1 && cBlock <= 1) { return 100; }
    return Math.round(100 * 16 / (yBlock * cBlock + 15));
  }

  function sliderToBlockSize(sliderValue) {
    return 1 << (8 - parseInt(sliderValue, 10));
  }

  function blockSizeLabel(blockSize) {
    return blockSize === 1
      ? t.blockSingle
      : formatLang(t.blockPlural, { size: blockSize });
  }

  function rgbToYCbCr(r, g, b) {
    return {
      Y:  0.299 * r + 0.587 * g + 0.114 * b,
      Cb: 128 - 0.168736 * r - 0.331264 * g + 0.5 * b,
      Cr: 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
    };
  }

  function ycbcrToRgb(Y, Cb, Cr) {
    const r = Y + 1.402 * (Cr - 128);
    const g = Y - 0.344136 * (Cb - 128) - 0.714136 * (Cr - 128);
    const b = Y + 1.772 * (Cb - 128);
    return [
      Math.min(255, Math.max(0, Math.round(r))),
      Math.min(255, Math.max(0, Math.round(g))),
      Math.min(255, Math.max(0, Math.round(b)))
    ];
  }

  function blockAverageChannel(channel, blockSize) {
    const out = new Float32Array(channel);
    if (blockSize <= 1) { return out; }

    for (let by = 0; by < imgH; by += blockSize) {
      const maxY = Math.min(by + blockSize, imgH);
      for (let bx = 0; bx < imgW; bx += blockSize) {
        const maxX = Math.min(bx + blockSize, imgW);
        let sum = 0;
        let count = 0;

        for (let y = by; y < maxY; y++) {
          for (let x = bx; x < maxX; x++) {
            sum += channel[y * imgW + x];
            count++;
          }
        }

        const avg = sum / count;
        for (let y = by; y < maxY; y++) {
          for (let x = bx; x < maxX; x++) {
            out[y * imgW + x] = avg;
          }
        }
      }
    }

    return out;
  }

  function buildYCbCrChannels() {
    const n = imgW * imgH;
    yChannel = new Float32Array(n);
    cbChannel = new Float32Array(n);
    crChannel = new Float32Array(n);

    for (let i = 0; i < n; i++) {
      const off = i * 4;
      const ycc = rgbToYCbCr(origPixels[off], origPixels[off + 1], origPixels[off + 2]);
      yChannel[i] = ycc.Y;
      cbChannel[i] = ycc.Cb;
      crChannel[i] = ycc.Cr;
    }
  }

  function render() {
    if (!yChannel) { return; }

    const yBlock = sliderToBlockSize(brightSlider.value);
    const cBlock = sliderToBlockSize(colorSlider.value);
    const yOut = blockAverageChannel(yChannel, yBlock);
    const cbOut = blockAverageChannel(cbChannel, cBlock);
    const crOut = blockAverageChannel(crChannel, cBlock);

    const out = ctx.createImageData(imgW, imgH);
    const d = out.data;

    for (let i = 0; i < imgW * imgH; i++) {
      const rgb = ycbcrToRgb(yOut[i], cbOut[i], crOut[i]);
      const off = i * 4;
      d[off]     = rgb[0];
      d[off + 1] = rgb[1];
      d[off + 2] = rgb[2];
      d[off + 3] = 255;
    }

    ctx.putImageData(out, 0, 0);
  }

  function updateLabels() {
    brightVal.textContent = blockSizeLabel(sliderToBlockSize(brightSlider.value));
    colorVal.textContent = blockSizeLabel(sliderToBlockSize(colorSlider.value));
  }

  function getMetrics() {
    const yBlock = sliderToBlockSize(brightSlider.value);
    const cBlock = sliderToBlockSize(colorSlider.value);
    return {
      sizePct: estimateSizePct(yBlock, cBlock),
      yBlock: yBlock,
      cBlock: cBlock
    };
  }

  function resetWidget() {
    brightSlider.value = "8";
    colorSlider.value = "8";
    onSliderInput();
  }

  function onSliderInput() {
    updateLabels();
    render();
    const metrics = getMetrics();
    if (sizeEstimate) {
      sizeEstimate.textContent = t.sizeEstimate + " " + formatLang(t.sizePct, { pct: metrics.sizePct });
    }
    refreshWidgetChallenges("widget-s2-blockavg");
  }

  function loadImageFromBitmap() {
    if (!img.naturalWidth) { return; }

    imgW = img.naturalWidth;
    imgH = img.naturalHeight;

    srcCanvas.width = imgW;
    srcCanvas.height = imgH;
    canvas.width = imgW;
    canvas.height = imgH;

    srcCtx.drawImage(img, 0, 0, imgW, imgH);
    origPixels = new Uint8ClampedArray(srcCtx.getImageData(0, 0, imgW, imgH).data);
    buildYCbCrChannels();
    onSliderInput();
  }

  function showLoadError() {
    imageWrap.innerHTML = `<p style="padding:1rem;color:#a92f2f;font-family:sans-serif;font-size:0.9rem;margin:0">
      ${t.loadError}
    </p>`;
  }

  brightSlider.addEventListener("input", onSliderInput);
  colorSlider.addEventListener("input", onSliderInput);

  const imgSrc = window.KATZE_DATA_URL;
  if (!imgSrc) {
    showLoadError();
    return;
  }

  const img = new Image();
  img.onload = loadImageFromBitmap;
  img.onerror = showLoadError;
  img.src = imgSrc;
  if (img.complete && img.naturalWidth) {
    loadImageFromBitmap();
  }

  registerWidgetChallenges("widget-s2-blockavg", {
    challenges: [
      { prompt: t.challenges.sizeUnder20, test: function(m) { return m.sizePct <= 20; } },
      { prompt: t.challenges.faceDetails, test: function(m) { return m.yBlock <= 4; } },
      { prompt: t.challenges.eyeColor, test: function(m) { return m.cBlock <= 32; } }
    ],
    getMetrics: getMetrics,
    onReset: resetWidget
  });
}
