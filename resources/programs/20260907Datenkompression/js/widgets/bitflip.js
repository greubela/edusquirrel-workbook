function setupBitflipWidget(source) {

  const t = source || getWidgetLang("bitflip");

  const container = document.getElementById("widget-s2-bitflip");

  if (!container) { return; }



  const SIZE = 128;

  const TOTAL_PIXELS = SIZE * SIZE;

  const PW_FLIP_BITS = Array.isArray(t.flipBitsPassword) ? t.flipBitsPassword : [47, 12, 83, 31, 96];

  const IMG_FLIP_BITS = Array.isArray(t.flipBitsImage) ? t.flipBitsImage : [8192, 1337, 12000, 2048, 10000];

  let pwFlipIndex = 0;

  let imgFlipIndex = 0;

  let pwFlipped = -1;

  let flippedBit = -1;



  const origPixels = new Uint8Array(TOTAL_PIXELS);

  const cx = SIZE / 2;

  const cy = SIZE / 2;

  const r = 52;

  const t2 = 4;

  for (let y = 0; y < SIZE; y++) {

    for (let x = 0; x < SIZE; x++) {

      const dist = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2);

      if (dist >= r - t2 && dist <= r + t2) {

        origPixels[y * SIZE + x] = 1;

      }

    }

  }



  let pixels = origPixels.slice();

  let pwBits = [];



  const PW_ORIG = t.password || "M3inGeh3im!";

  const encodingLabel = t.encodingLabel || "UTF-8";



  function strToBits(s) {

    const bits = [];

    for (const c of s) {

      const code = c.charCodeAt(0);

      for (let i = 7; i >= 0; i--) { bits.push((code >> i) & 1); }

    }

    return bits;

  }



  function bitsToStr(bits) {

    let out = "";

    for (let i = 0; i < bits.length; i += 8) {

      let b = 0;

      for (let j = 0; j < 8; j++) { b = (b << 1) | (bits[i + j] || 0); }

      out += String.fromCharCode(b);

    }

    return out;

  }



  pwBits = strToBits(PW_ORIG);

  const pwOrigBits = strToBits(PW_ORIG);



  container.classList.remove("widget-placeholder");

  container.innerHTML =

    '<div class="bitflip-widget">' +

      '<div class="bitflip-panel">' +

        '<p class="bitflip-label">' + t.passwordLabel + ' <span class="bitflip-encoding">(' + encodingLabel + ')</span></p>' +

        '<div id="bfw-pw-val" class="bitflip-value"></div>' +

        '<div class="bitflip-actions">' +

          '<button id="bfw-pw-flip" type="button">' + t.flipButton + '</button>' +

          '<button id="bfw-pw-reset" type="button">' + t.resetButton + '</button>' +

        '</div>' +

      '</div>' +

      '<div class="bitflip-panel">' +

        '<p class="bitflip-label">' + t.imageLabel + '</p>' +

        '<div class="bitflip-media">' +

        '<canvas id="bfw-canvas" width="128" height="128" class="bitflip-canvas"></canvas>' +

        '</div>' +

        '<div class="bitflip-actions">' +

          '<button id="bfw-img-flip" type="button">' + t.flipButton + '</button>' +

          '<button id="bfw-img-reset" type="button">' + t.resetButton + '</button>' +

        '</div>' +

      '</div>' +

    '</div>';



  const canvas = document.getElementById("bfw-canvas");

  const ctx = canvas.getContext("2d");



  function drawImg() {

    const id = ctx.createImageData(SIZE, SIZE);

    for (let i = 0; i < TOTAL_PIXELS; i++) {

      const v = pixels[i] ? 0 : 255;

      id.data[i * 4] = v;

      id.data[i * 4 + 1] = v;

      id.data[i * 4 + 2] = v;

      id.data[i * 4 + 3] = 255;

    }

    ctx.putImageData(id, 0, 0);

  }



  function updatePw() {
    document.getElementById("bfw-pw-val").textContent = bitsToStr(pwBits);
  }

  function updateImg() {
    drawImg();
  }



  function resetAll() {

    pwBits = strToBits(PW_ORIG);

    pwFlipped = -1;

    pwFlipIndex = 0;

    pixels = origPixels.slice();

    flippedBit = -1;

    imgFlipIndex = 0;

    updatePw();

    updateImg();

  }



  document.getElementById("bfw-pw-flip").addEventListener("click", function() {

    if (pwFlipped >= 0) { pwBits[pwFlipped] ^= 1; }

    pwFlipped = PW_FLIP_BITS[pwFlipIndex % PW_FLIP_BITS.length] % pwBits.length;

    pwFlipIndex++;

    pwBits[pwFlipped] ^= 1;

    updatePw();

  });



  document.getElementById("bfw-pw-reset").addEventListener("click", function() {

    pwBits = strToBits(PW_ORIG);

    pwFlipped = -1;

    pwFlipIndex = 0;

    updatePw();

  });



  document.getElementById("bfw-img-flip").addEventListener("click", function() {

    if (flippedBit >= 0) { pixels[flippedBit] ^= 1; }

    flippedBit = IMG_FLIP_BITS[imgFlipIndex % IMG_FLIP_BITS.length] % TOTAL_PIXELS;

    imgFlipIndex++;

    pixels[flippedBit] ^= 1;

    updateImg();

  });



  document.getElementById("bfw-img-reset").addEventListener("click", function() {

    pixels = origPixels.slice();

    flippedBit = -1;

    imgFlipIndex = 0;

    updateImg();

  });



  updatePw();

  updateImg();

}

