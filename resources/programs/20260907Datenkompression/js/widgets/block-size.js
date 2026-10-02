function setupBlockSizeWidget(source) {
  const t = source || getWidgetLang("blockSize");
  const emptyValue = t.emptyValue || getWidgetLang("common").emptyValue || "";
  var container = document.getElementById("widget-s2-blocksize");
  if (!container) { return; }

  var GRID_X = 0;
  var GRID_Y = 0;
  var TOTAL_PIXELS = 0;
  var BITS_PER_PIXEL = 24;
  var ORIG_BITS = 0;

  var patternR = null;
  var patternG = null;
  var patternB = null;
  var patternLoaded = false;

  var BLOCK_SIZES = [1, 2, 4, 8, 16, 32, 64];

  container.classList.remove("widget-placeholder");
  container.style.cssText = "border:1px solid var(--border);background:#fffdf8;padding:0.8rem 1rem 1rem;border-radius:3px;margin:0.75rem 0";

  container.innerHTML =
    '<div style="max-width:576px;margin:0 auto">' +
      '<canvas id="bs-canvas"' +
        ' style="display:block;width:100%;height:auto;border:0.5px solid var(--border);border-radius:4px;image-rendering:auto"></canvas>' +
      '<p id="bs-block-label" style="font-size:12px;color:var(--muted);margin:6px 0 14px;text-align:center;font-family:sans-serif;min-height:1.2em">' + t.loading + '</p>' +
      '<div style="width:100%">' +
        '<div style="margin-bottom:18px">' +
          '<div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:4px;gap:12px">' +
            '<label for="bs-slider" style="font-size:13px;font-family:sans-serif;flex-shrink:0">' + t.blockSizeLabel + '</label>' +
            '<span id="bs-slider-val" style="font-family:monospace;font-size:13px;color:var(--accent);font-weight:600;min-width:4.5em;text-align:right;font-variant-numeric:tabular-nums">1\u00d71</span>' +
          '</div>' +
          '<input id="bs-slider" type="range" min="0" max="6" step="1" value="0" style="width:100%;accent-color:var(--accent)" disabled>' +
          '<div style="display:flex;justify-content:space-between;font-size:11px;color:var(--muted);font-family:monospace;margin-top:2px;font-variant-numeric:tabular-nums">' +
            '<span style="min-width:2.5em;text-align:center">1\u00d71</span>' +
            '<span style="min-width:2.5em;text-align:center">2\u00d72</span>' +
            '<span style="min-width:2.5em;text-align:center">4\u00d74</span>' +
            '<span style="min-width:2.5em;text-align:center">8\u00d78</span>' +
            '<span style="min-width:2.5em;text-align:center">16\u00d716</span>' +
            '<span style="min-width:2.5em;text-align:center">32\u00d732</span>' +
            '<span style="min-width:2.5em;text-align:center">64\u00d764</span>' +
          '</div>' +
        '</div>' +
        '<div style="display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-bottom:12px">' +
          '<div style="background:var(--bg);border-radius:3px;padding:10px 12px;min-width:0">' +
            '<div style="font-size:12px;color:var(--muted);margin-bottom:4px;font-family:sans-serif">' + t.original + '</div>' +
            '<div id="bs-orig" style="font-size:15px;font-weight:500;min-height:2.6em;font-variant-numeric:tabular-nums;line-height:1.3">' + emptyValue + '</div>' +
          '</div>' +
          '<div style="background:var(--bg);border-radius:3px;padding:10px 12px;min-width:0">' +
            '<div style="font-size:12px;color:var(--muted);margin-bottom:4px;font-family:sans-serif">' + t.compressed + '</div>' +
            '<div id="bs-comp" style="font-size:15px;font-weight:500;min-height:2.6em;font-variant-numeric:tabular-nums;line-height:1.3">' + emptyValue + '</div>' +
          '</div>' +
        '</div>' +
        '<div style="margin-top:6px">' +
          '<div style="position:relative;height:16px;border:0.5px solid var(--border);background:var(--bg);overflow:hidden;border-radius:3px">' +
            '<div id="bs-bar-comp" style="position:absolute;inset:0 auto 0 0;background:#3f8f4b;transition:width 150ms ease"></div>' +
          '</div>' +
          '<div style="display:flex;justify-content:space-between;font-size:11px;color:var(--muted);font-family:monospace;margin-top:2px;font-variant-numeric:tabular-nums">' +
            '<span style="min-width:3em">' + t.zeroBit + '</span>' +
            '<span id="bs-bar-max-label" style="min-width:6em;text-align:right">' + emptyValue + '</span>' +
          '</div>' +
        '</div>' +
      '</div>' +
    '</div>';

  var canvas = document.getElementById("bs-canvas");
  var ctx = canvas.getContext("2d", { willReadFrequently: true });
  var slider = document.getElementById("bs-slider");
  var sliderVal = document.getElementById("bs-slider-val");
  var blockLabel = document.getElementById("bs-block-label");
  var origEl = document.getElementById("bs-orig");
  var compEl = document.getElementById("bs-comp");
  var barComp = document.getElementById("bs-bar-comp");
  var barMaxLabel = document.getElementById("bs-bar-max-label");

  function formatBits(bits) {
    return Math.round(bits).toLocaleString("de-DE") + t.bitUnit;
  }

  function formatCompressed(compBits) {
    var pct = ((compBits / ORIG_BITS) * 100).toFixed(1).replace(".", ",");
    return pct + " % (" + formatBits(compBits) + ")";
  }

  function sliderToBlockSize(val) {
    return BLOCK_SIZES[parseInt(val, 10)] || 1;
  }

  function drawPreview(blockSize) {
    var imageData = ctx.createImageData(GRID_X, GRID_Y);
    var pixels = imageData.data;

    if (blockSize === 1) {
      for (var i = 0; i < TOTAL_PIXELS; i++) {
        var off = i * 4;
        pixels[off] = Math.round(patternR[i]);
        pixels[off + 1] = Math.round(patternG[i]);
        pixels[off + 2] = Math.round(patternB[i]);
        pixels[off + 3] = 255;
      }
      ctx.putImageData(imageData, 0, 0);
      return;
    }

    for (var by = 0; by < GRID_Y; by += blockSize) {
      var maxY = Math.min(by + blockSize, GRID_Y);
      for (var bx = 0; bx < GRID_X; bx += blockSize) {
        var maxX = Math.min(bx + blockSize, GRID_X);
        var sumR = 0;
        var sumG = 0;
        var sumB = 0;
        var count = 0;

        for (var y = by; y < maxY; y++) {
          for (var x = bx; x < maxX; x++) {
            var idx = y * GRID_X + x;
            sumR += patternR[idx];
            sumG += patternG[idx];
            sumB += patternB[idx];
            count++;
          }
        }

        var avgR = Math.round(sumR / count);
        var avgG = Math.round(sumG / count);
        var avgB = Math.round(sumB / count);

        for (var py = by; py < maxY; py++) {
          for (var px = bx; px < maxX; px++) {
            var pIdx = py * GRID_X + px;
            var pOff = pIdx * 4;
            pixels[pOff] = avgR;
            pixels[pOff + 1] = avgG;
            pixels[pOff + 2] = avgB;
            pixels[pOff + 3] = 255;
          }
        }
      }
    }

    ctx.putImageData(imageData, 0, 0);

    var blocksX = Math.ceil(GRID_X / blockSize);
    var blocksY = Math.ceil(GRID_Y / blockSize);
    if (blocksX * blocksY <= 400) {
      ctx.strokeStyle = "rgba(0,0,0,0.15)";
      ctx.lineWidth = 1;
      for (var gx = 0; gx <= blocksX; gx++) {
        var lineX = Math.min(gx * blockSize, GRID_X) + 0.5;
        ctx.beginPath();
        ctx.moveTo(lineX, 0);
        ctx.lineTo(lineX, GRID_Y);
        ctx.stroke();
      }
      for (var gy = 0; gy <= blocksY; gy++) {
        var lineY = Math.min(gy * blockSize, GRID_Y) + 0.5;
        ctx.beginPath();
        ctx.moveTo(0, lineY);
        ctx.lineTo(GRID_X, lineY);
        ctx.stroke();
      }
    }
  }

  function computeCompressedBits(blockSize) {
    var blocksX = Math.ceil(GRID_X / blockSize);
    var blocksY = Math.ceil(GRID_Y / blockSize);
    return blocksX * blocksY * BITS_PER_PIXEL;
  }

  function update() {
    if (!patternLoaded) { return; }

    var idx = parseInt(slider.value, 10);
    var blockSize = sliderToBlockSize(idx);
    var compBits = computeCompressedBits(blockSize);

    sliderVal.textContent = blockSize + "\u00d7" + blockSize;
    drawPreview(blockSize);

    origEl.textContent = formatBits(ORIG_BITS);
    compEl.textContent = formatCompressed(compBits);

    var compPct = Math.min((compBits / ORIG_BITS) * 100, 100);
    barComp.style.width = compPct + "%";
    barMaxLabel.textContent = formatBits(ORIG_BITS);
  }

  function loadPatternFromImage() {
    var imgSrc = window.KATZE_DATA_URL;
    if (!imgSrc) {
      blockLabel.textContent = t.imageUnavailable;
      return;
    }

    var img = new Image();

    function finishLoad() {
      if (!img.naturalWidth || !img.naturalHeight) { return; }

      GRID_X = img.naturalWidth;
      GRID_Y = img.naturalHeight;
      TOTAL_PIXELS = GRID_X * GRID_Y;
      ORIG_BITS = TOTAL_PIXELS * BITS_PER_PIXEL;

      canvas.width = GRID_X;
      canvas.height = GRID_Y;

      patternR = new Float32Array(TOTAL_PIXELS);
      patternG = new Float32Array(TOTAL_PIXELS);
      patternB = new Float32Array(TOTAL_PIXELS);

      var srcCanvas = document.createElement("canvas");
      srcCanvas.width = GRID_X;
      srcCanvas.height = GRID_Y;
      var srcCtx = srcCanvas.getContext("2d", { willReadFrequently: true });
      srcCtx.drawImage(img, 0, 0, GRID_X, GRID_Y);

      var srcData = srcCtx.getImageData(0, 0, GRID_X, GRID_Y).data;
      for (var i = 0; i < TOTAL_PIXELS; i++) {
        var off = i * 4;
        patternR[i] = srcData[off];
        patternG[i] = srcData[off + 1];
        patternB[i] = srcData[off + 2];
      }

      patternLoaded = true;
      slider.disabled = false;
      blockLabel.textContent = formatLang(t.imageLoaded, { width: GRID_X, height: GRID_Y });
      update();
    }

    img.onload = finishLoad;
    img.onerror = function() {
      blockLabel.textContent = t.imageLoadError;
    };
    img.src = imgSrc;
    if (img.complete && img.naturalWidth) {
      finishLoad();
    }
  }

  slider.addEventListener("input", update);
  loadPatternFromImage();
}
