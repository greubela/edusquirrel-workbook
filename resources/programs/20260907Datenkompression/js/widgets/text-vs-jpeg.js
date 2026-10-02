function setupTextVsJpegWidget(source) {
  const t = source || getWidgetLang("textVsJpeg");
  const container = document.getElementById("widget-s2-text-vs-jpeg");
  if (!container) { return; }

  const textBytes = window.S2_TASK5_TEXT_BYTES || 0;
  const jpgFileBytes = window.S2_TASK5_JPG_BYTES || 0;
  const imgSrc = window.S2_TASK5_JPG_DATA_URL || "";

  let origPixels = null;
  let yChannel = null;
  let cbChannel = null;
  let crChannel = null;
  let imgW = 0;
  let imgH = 0;

  function formatBytes(n) {
    if (n >= 1024 * 1024) {
      return (n / (1024 * 1024)).toLocaleString("de-DE", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
      }) + " MB";
    }
    if (n >= 1024) {
      return (n / 1024).toLocaleString("de-DE", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
      }) + " KB";
    }
    return n.toLocaleString("de-DE") + " Bytes";
  }

  container.classList.remove("widget-placeholder");
  container.style.cssText =
    "border:1px solid var(--border);background:#fffdf8;padding:0.8rem 1rem 1rem;border-radius:3px;margin:0.75rem 0";
  container.innerHTML =
    '<div id="tvj-image-wrap" style="margin-bottom:8px">' +
      '<div id="tvj-viewport" style="overflow:auto;max-height:340px;text-align:center;border:1px solid var(--border);border-radius:3px;background:#f5f5f5;padding:8px 0">' +
        '<canvas id="tvj-canvas" style="display:inline-block;max-width:none;height:auto;vertical-align:top"></canvas>' +
      "</div>" +
      '<div style="display:flex;align-items:center;gap:10px;margin-top:8px;flex-wrap:wrap">' +
        '<label for="tvj-zoom" style="font-size:13px;font-family:sans-serif;flex-shrink:0">' + t.zoomLabel + "</label>" +
        '<input id="tvj-zoom" type="range" min="100" max="400" step="10" value="100" style="flex:1;min-width:120px;accent-color:var(--accent)">' +
        '<span id="tvj-zoom-val" style="font-family:monospace;font-size:13px;color:var(--accent);font-weight:600;min-width:3.5em">100 %</span>' +
      "</div>" +
      '<p style="font-size:11px;color:var(--muted);margin:6px 0 0;font-family:sans-serif">' + t.zoomHint + "</p>" +
    "</div>" +
    '<div style="display:grid;gap:10px">' +
      '<div>' +
        '<div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:4px">' +
          '<label for="tvj-brightness" style="font-size:13px;font-family:sans-serif">' + t.brightnessLabel + "</label>" +
          '<span id="tvj-brightness-val" style="font-family:monospace;font-size:13px;color:var(--accent);font-weight:600">' + t.blockSingle + "</span>" +
        "</div>" +
        '<input id="tvj-brightness" type="range" min="1" max="8" step="1" value="8" style="width:100%;accent-color:var(--accent)">' +
      "</div>" +
      '<div>' +
        '<div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:4px">' +
          '<label for="tvj-colorbits" style="font-size:13px;font-family:sans-serif">' + t.colorLabel + "</label>" +
          '<span id="tvj-colorbits-val" style="font-family:monospace;font-size:13px;color:var(--accent);font-weight:600">' + t.blockSingle + "</span>" +
        "</div>" +
        '<input id="tvj-colorbits" type="range" min="1" max="8" step="1" value="8" style="width:100%;accent-color:var(--accent)">' +
      "</div>" +
      '<p id="tvj-size-estimate" style="font-size:12px;color:var(--muted);margin:0;font-family:sans-serif"></p>' +
      '<p id="tvj-text-compare" style="font-size:12px;color:var(--muted);margin:4px 0 0;font-family:sans-serif"></p>' +
    "</div>";

  const canvas = document.getElementById("tvj-canvas");
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  const srcCanvas = document.createElement("canvas");
  const srcCtx = srcCanvas.getContext("2d", { willReadFrequently: true });
  const brightSlider = document.getElementById("tvj-brightness");
  const colorSlider = document.getElementById("tvj-colorbits");
  const brightVal = document.getElementById("tvj-brightness-val");
  const colorVal = document.getElementById("tvj-colorbits-val");
  const imageWrap = document.getElementById("tvj-image-wrap");
  const viewport = document.getElementById("tvj-viewport");
  const zoomSlider = document.getElementById("tvj-zoom");
  const zoomVal = document.getElementById("tvj-zoom-val");
  const sizeEstimate = document.getElementById("tvj-size-estimate");
  const textCompare = document.getElementById("tvj-text-compare");

  let zoom = 1;
  const ZOOM_MIN = 1;
  const ZOOM_MAX = 4;
  const MAX_DISPLAY_HEIGHT = 320;
  let zoomRetryRaf = 0;
  let zoomRetryCount = 0;
  const ZOOM_RETRY_MAX = 30;

  function applyZoom() {
    if (!imgW || !viewport) { return; }
    const wrapW = viewport.clientWidth || (imageWrap && imageWrap.clientWidth) || 0;
    if (wrapW <= 0) {
      if (zoomRetryCount < ZOOM_RETRY_MAX) {
        zoomRetryCount += 1;
        if (zoomRetryRaf) {
          window.cancelAnimationFrame(zoomRetryRaf);
        }
        zoomRetryRaf = window.requestAnimationFrame(function() {
          zoomRetryRaf = 0;
          applyZoom();
        });
      }
      return;
    }
    zoomRetryCount = 0;
    const aspect = imgH / imgW;
    let baseW = wrapW;
    let baseH = baseW * aspect;
    if (baseH > MAX_DISPLAY_HEIGHT) {
      baseH = MAX_DISPLAY_HEIGHT;
      baseW = baseH / aspect;
    }
    canvas.style.width = Math.round(baseW * zoom) + "px";
    canvas.style.height = Math.round(baseH * zoom) + "px";
    if (zoomVal) {
      zoomVal.textContent = Math.round(zoom * 100) + " %";
    }
    if (zoomSlider) {
      zoomSlider.value = String(Math.round(zoom * 100));
    }
  }

  function setZoom(nextZoom) {
    zoom = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, nextZoom));
    applyZoom();
  }

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

  function updateTextCompare(metrics) {
    if (!textBytes || !textCompare) { return; }

    const estimated = metrics.estimatedBytes;
    const diff = estimated - textBytes;
    let verdict;

    if (Math.abs(diff) < 512) {
      verdict = t.compareEqual;
    } else if (diff > 0) {
      verdict = formatLang(t.compareLarger, { diff: formatBytes(diff) });
    } else {
      verdict = formatLang(t.compareSmaller, { diff: formatBytes(Math.abs(diff)) });
    }

    textCompare.textContent = formatLang(t.textCompare, {
      textSize: formatBytes(textBytes),
      jpegSize: formatBytes(estimated),
      verdict: verdict
    });
    textCompare.style.color = diff > 512 ? "#8b3a3a" : diff < -512 ? "#2f6b3a" : "var(--muted)";
  }

  function onSliderInput() {
    updateLabels();
    render();
    const yBlock = sliderToBlockSize(brightSlider.value);
    const cBlock = sliderToBlockSize(colorSlider.value);
    const sizePct = estimateSizePct(yBlock, cBlock);
    const estimatedBytes = jpgFileBytes > 0
      ? Math.round(jpgFileBytes * sizePct / 100)
      : 0;
    if (sizeEstimate) {
      sizeEstimate.textContent = t.sizeEstimate + " " + formatLang(t.sizePct, { pct: sizePct });
    }
    updateTextCompare({ estimatedBytes: estimatedBytes });
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
    applyZoom();
  }

  function showLoadError() {
    imageWrap.innerHTML =
      '<p style="padding:1rem;color:#a92f2f;font-family:sans-serif;font-size:0.9rem;margin:0">' +
        t.loadError +
      "</p>";
  }

  brightSlider.addEventListener("input", onSliderInput);
  colorSlider.addEventListener("input", onSliderInput);

  if (zoomSlider) {
    zoomSlider.addEventListener("input", function() {
      setZoom(parseInt(zoomSlider.value, 10) / 100);
    });
  }
  if (viewport) {
    viewport.addEventListener("wheel", function(e) {
      e.preventDefault();
      setZoom(zoom + (e.deltaY < 0 ? 0.1 : -0.1));
    }, { passive: false });
  }
  window.addEventListener("resize", applyZoom);

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
}
