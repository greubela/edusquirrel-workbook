function setupJPEGIntroSlideshow(source) {
  const lang = source || window.LANG_DE || {};
  const container = document.getElementById("widget-s2-jpeg-slideshow");
  if (!container || typeof setupStepSlideshow !== "function") { return; }

  const slideTexts = getLangValue(lang, "lossy.task2.slides") || [];
  const slideLabels = getLangValue(lang, "lossy.task2.slideLabels") || {};
  const slideshowLabels = {
    prev: getLangValue(lang, "nav.prev") || "",
    next: getLangValue(lang, "nav.next") || ""
  };
  if (slideTexts.length === 0) { return; }

  container.classList.remove("widget-placeholder");

  const imgSrc = window.KATZE_DATA_URL;
  if (!imgSrc) {
    setupStepSlideshow("widget-s2-jpeg-slideshow", buildFallbackSteps(slideTexts, slideLabels), slideshowLabels);
    return;
  }

  const img = new Image();
  img.onload = function() {
    setupStepSlideshow("widget-s2-jpeg-slideshow", buildSteps(img, slideTexts, slideLabels), slideshowLabels);
  };
  img.onerror = function() {
    setupStepSlideshow("widget-s2-jpeg-slideshow", buildFallbackSteps(slideTexts, slideLabels), slideshowLabels);
  };
  img.src = imgSrc;
  if (img.complete && img.naturalWidth) {
    img.onload();
  }
}

function jpegSlideCanvasStyle() {
  return "max-width:100%;height:auto;border:1px solid var(--border);border-radius:3px";
}

function makeSlideCanvas(width, height, drawFn) {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  canvas.style.cssText = jpegSlideCanvasStyle();
  drawFn(canvas.getContext("2d"), width, height);
  return canvas;
}

function getFullImageFrame(img) {
  return {
    sx: 0,
    sy: 0,
    width: img.naturalWidth,
    height: img.naturalHeight
  };
}

function getDisplaySize(frame, maxW) {
  const scale = maxW / frame.width;
  return {
    width: maxW,
    height: Math.max(1, Math.round(frame.height * scale))
  };
}

function drawFullImage(ctx, img, frame, destW, destH) {
  ctx.drawImage(img, frame.sx, frame.sy, frame.width, frame.height, 0, 0, destW, destH);
}

function drawBlockGrid(ctx, img, frame, destW, destH, blockSize) {
  drawFullImage(ctx, img, frame, destW, destH);
  const blockPxW = blockSize * destW / frame.width;
  const blockPxH = blockSize * destH / frame.height;
  ctx.strokeStyle = "rgba(255,255,255,0.85)";
  ctx.lineWidth = 1;
  for (let x = 0; x <= destW; x += blockPxW) {
    ctx.beginPath();
    ctx.moveTo(x + 0.5, 0);
    ctx.lineTo(x + 0.5, destH);
    ctx.stroke();
  }
  for (let y = 0; y <= destH; y += blockPxH) {
    ctx.beginPath();
    ctx.moveTo(0, y + 0.5);
    ctx.lineTo(destW, y + 0.5);
    ctx.stroke();
  }
}

function getImageData(img, frame) {
  const off = document.createElement("canvas");
  off.width = frame.width;
  off.height = frame.height;
  const offCtx = off.getContext("2d", { willReadFrequently: true });
  offCtx.drawImage(img, frame.sx, frame.sy, frame.width, frame.height, 0, 0, frame.width, frame.height);
  return offCtx.getImageData(0, 0, frame.width, frame.height).data;
}

function averageRegion(data, imgW, x, y, w, h) {
  let r = 0;
  let g = 0;
  let b = 0;
  let count = 0;

  for (let py = y; py < y + h; py++) {
    for (let px = x; px < x + w; px++) {
      const i = (py * imgW + px) * 4;
      r += data[i];
      g += data[i + 1];
      b += data[i + 2];
      count++;
    }
  }

  return {
    r: Math.round(r / count),
    g: Math.round(g / count),
    b: Math.round(b / count)
  };
}

function drawBlockAveraged(ctx, img, frame, destW, destH, blockSize) {
  const data = getImageData(img, frame);
  const scaleX = destW / frame.width;
  const scaleY = destH / frame.height;

  for (let by = 0; by < frame.height; by += blockSize) {
    for (let bx = 0; bx < frame.width; bx += blockSize) {
      const bw = Math.min(blockSize, frame.width - bx);
      const bh = Math.min(blockSize, frame.height - by);
      const avg = averageRegion(data, frame.width, bx, by, bw, bh);
      ctx.fillStyle = "rgb(" + avg.r + "," + avg.g + "," + avg.b + ")";
      ctx.fillRect(bx * scaleX, by * scaleY, bw * scaleX, bh * scaleY);
    }
  }
}

function pickSampleBlock(frame, blockSize) {
  const blocksX = Math.floor(frame.width / blockSize);
  const blocksY = Math.floor(frame.height / blockSize);
  return {
    x: Math.max(0, Math.floor(blocksX / 2)) * blockSize,
    y: Math.max(0, Math.floor(blocksY / 2)) * blockSize
  };
}

function buildSteps(img, slideTexts, slideLabels) {
  const labels = slideLabels || {};
  const MAX_W = 520;
  const COMPARE_W = 240;
  const JPEG_BLOCK = 8;
  const frame = getFullImageFrame(img);
  const display = getDisplaySize(frame, MAX_W);
  const compareDisplay = getDisplaySize(frame, COMPARE_W);
  const sample = pickSampleBlock(frame, JPEG_BLOCK);

  return [
    {
      canvas: makeSlideCanvas(display.width, display.height, function(ctx, w, h) {
        drawFullImage(ctx, img, frame, w, h);
      }),
      caption: slideTexts[0].caption
    },
    {
      canvas: makeSlideCanvas(display.width, display.height, function(ctx, w, h) {
        drawBlockGrid(ctx, img, frame, w, h, JPEG_BLOCK);
      }),
      caption: slideTexts[1].caption
    },
    {
      canvas: makeSlideCanvas(display.width, display.height, function(ctx, w, h) {
        const half = w / 2;
        const data = getImageData(img, frame);
        const pixelSize = Math.max(1, Math.floor(Math.min(half, h) / JPEG_BLOCK));
        const gridSize = pixelSize * JPEG_BLOCK;
        const leftX = (half - gridSize) / 2;
        const leftY = (h - gridSize) / 2;

        ctx.imageSmoothingEnabled = false;
        for (let py = 0; py < JPEG_BLOCK; py++) {
          for (let px = 0; px < JPEG_BLOCK; px++) {
            const i = ((sample.y + py) * frame.width + (sample.x + px)) * 4;
            ctx.fillStyle = "rgb(" + data[i] + "," + data[i + 1] + "," + data[i + 2] + ")";
            ctx.fillRect(leftX + px * pixelSize, leftY + py * pixelSize, pixelSize, pixelSize);
          }
        }

        const avg = averageRegion(data, frame.width, sample.x, sample.y, JPEG_BLOCK, JPEG_BLOCK);
        const rightX = half + (half - gridSize) / 2;
        const rightY = leftY;
        ctx.fillStyle = "rgb(" + avg.r + "," + avg.g + "," + avg.b + ")";
        ctx.fillRect(rightX, rightY, gridSize, gridSize);
        ctx.strokeStyle = "#c8b898";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(half + 0.5, 0);
        ctx.lineTo(half + 0.5, h);
        ctx.stroke();
        ctx.fillStyle = "#555";
        ctx.font = "11px sans-serif";
        ctx.textAlign = "center";
        ctx.fillText(labels.pixels8 || "", half / 2, h - 8);
        ctx.fillText(labels.average || "", half + half / 2, h - 8);
      }),
      caption: slideTexts[2].caption
    },
    {
      html: (function() {
        const urlA = makeSlideCanvas(compareDisplay.width, compareDisplay.height, function(ctx, w, h) {
          drawBlockAveraged(ctx, img, frame, w, h, JPEG_BLOCK);
        }).toDataURL();
        const urlB = makeSlideCanvas(compareDisplay.width, compareDisplay.height, function(ctx, w, h) {
          drawBlockAveraged(ctx, img, frame, w, h, 32);
        }).toDataURL();
        return (
          '<div style="display:flex;gap:12px;align-items:center;justify-content:center;flex-wrap:wrap;width:100%">' +
            '<div style="text-align:center">' +
              '<img src="' + urlA + '" alt="" style="' + jpegSlideCanvasStyle() + '">' +
              '<p style="margin:0.35rem 0 0;font-size:0.78rem;color:var(--muted)">' + (labels.blocks8 || "") + '</p>' +
            '</div>' +
            '<div style="text-align:center">' +
              '<img src="' + urlB + '" alt="" style="' + jpegSlideCanvasStyle() + '">' +
              '<p style="margin:0.35rem 0 0;font-size:0.78rem;color:var(--muted)">' + (labels.blocks32 || "") + '</p>' +
            '</div>' +
          '</div>'
        );
      })(),
      caption: slideTexts[3].caption
    },
    {
      html:
        '<div style="display:flex;align-items:center;justify-content:center;gap:6px;flex-wrap:wrap;font-size:0.82rem;font-family:sans-serif;width:100%">' +
          '<span style="background:#f7efe1;border:1px solid var(--border);padding:0.45rem 0.6rem;border-radius:3px;text-align:center">' + (labels.flowBlocks || "") + '</span>' +
          '<span style="color:var(--muted)">\u2192</span>' +
          '<span style="background:#f7efe1;border:1px solid var(--border);padding:0.45rem 0.6rem;border-radius:3px;text-align:center">' + (labels.flowTransform || "") + '</span>' +
          '<span style="color:var(--muted)">\u2192</span>' +
          '<span style="background:#f7efe1;border:1px solid var(--border);padding:0.45rem 0.6rem;border-radius:3px;text-align:center">' + (labels.flowLossless || "") + '</span>' +
          '<span style="color:var(--muted)">\u2192</span>' +
          '<span style="background:#e8f4ea;border:1px solid #3f8f4b;padding:0.45rem 0.6rem;border-radius:3px;font-weight:600;text-align:center">.jpg</span>' +
        '</div>',
      caption: slideTexts[4].caption
    },
    {
      html:
        '<div style="display:flex;gap:4px;align-items:center;justify-content:center;width:100%">' +
          [0, 1, 2, 3].map(function(i) {
            return '<div style="flex:0 0 auto;width:80px;height:80px;border:2px solid var(--border);border-radius:2px;overflow:hidden;background:#eee">' +
              '<img src="' + window.KATZE_DATA_URL + '" alt="" style="width:100%;height:100%;object-fit:contain">' +
            '</div>';
          }).join("") +
          '<span style="font-size:1.4rem;color:var(--muted);margin-left:2px">\u2026</span>' +
        '</div>',
      caption: slideTexts[5].caption
    }
  ];
}

function buildFallbackSteps(slideTexts, slideLabels) {
  const fallback = (slideLabels && slideLabels.fallbackSlide) || "";
  return slideTexts.map(function(slide, i) {
    return {
      html: '<p style="margin:0;text-align:center;color:var(--muted);font-size:0.9rem">' +
        fallback.replace("{n}", String(i + 1)) + '</p>',
      caption: slide.caption
    };
  });
}
