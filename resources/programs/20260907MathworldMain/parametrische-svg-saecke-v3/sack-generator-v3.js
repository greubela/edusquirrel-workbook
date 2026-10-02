/*
 * Parametrische Saatgut- und Düngersäcke als SVG.
 * Keine nicht-uniforme Skalierung kompletter Illustrationen:
 * Die Silhouette wird aus width/height neu berechnet, während Knoten,
 * Embleme und Konturen ihre Proportionen behalten.
 */
(function (global) {
  'use strict';

  let uid = 0;

  const PALETTE = Object.freeze({
    outline: '#2A170B',
    creamTop: '#FFF3D6',
    creamBottom: '#EAD5A8',
    tan: '#D9B27C',
    tanHighlight: '#F7E7C5',
    shadow: '#BFA77A',
    green1: '#C9DB8E',
    green2: '#B8D77E',
    rope1: '#C69155',
    rope2: '#D2A065',
    ropeHighlight: '#F0D4A1',
    soilTop: '#7A5B3F',
    soilBottom: '#5A3D29',
    soilDark: '#382418'
  });

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function n(value) {
    return Number(value.toFixed(2)).toString();
  }

  function escapeXml(value) {
    return String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&apos;');
  }

  function normalizedOptions(options) {
    const width = clamp(Number(options.width ?? 320) || 320, 140, 1000);
    const height = clamp(Number(options.height ?? 420) || 420, 180, 1200);
    const kind = options.kind === 'fertilizer' ? 'fertilizer' : 'seed';
    const plantKinds = new Set(['flower', 'sunflower', 'tulip', 'radish', 'carrot', 'tomato']);
    const plantKind = plantKinds.has(options.plantKind) ? options.plantKind : 'flower';
    const title = options.title || (kind === 'seed' ? 'Saatgutsack' : 'Düngersack');
    const palette = { ...PALETTE, ...(options.palette || {}) };
    const idPrefix = (options.idPrefix || `sack-${++uid}`).replace(/[^a-zA-Z0-9_-]/g, '-');
    return { width, height, kind, plantKind, title, palette, idPrefix };
  }

  function bodyGeometry(width, height, topY, strokeWidth) {
    const s = Math.min(width, height);
    const side = clamp(s * 0.075, 14, 34);
    const leftTop = clamp(width * 0.19, side + 10, width * 0.26);
    const rightTop = width - leftTop;
    const leftMid = clamp(width * 0.105, side, width * 0.16);
    const rightMid = width - leftMid;
    const bottomY = height - strokeWidth * 0.75;
    const bottomShoulder = clamp(s * 0.14, 28, 64);
    const leftBottom = clamp(width * 0.12, side - 2, width * 0.17);
    const rightBottom = width - leftBottom;
    const mid1 = topY + (bottomY - topY) * 0.42;
    const mid2 = bottomY - bottomShoulder;

    const d = [
      `M ${n(leftTop)} ${n(topY)}`,
      `C ${n(leftTop - width * 0.035)} ${n(topY + s * 0.12)}, ${n(leftMid)} ${n(mid1)}, ${n(leftMid)} ${n(mid2)}`,
      `C ${n(leftMid - width * 0.02)} ${n(bottomY - s * 0.04)}, ${n(leftBottom)} ${n(bottomY - s * 0.005)}, ${n(leftBottom + width * 0.07)} ${n(bottomY)}`,
      `Q ${n(width / 2)} ${n(height + s * 0.012)}, ${n(rightBottom - width * 0.07)} ${n(bottomY)}`,
      `C ${n(rightBottom)} ${n(bottomY - s * 0.005)}, ${n(rightMid + width * 0.02)} ${n(bottomY - s * 0.04)}, ${n(rightMid)} ${n(mid2)}`,
      `C ${n(rightMid)} ${n(mid1)}, ${n(rightTop + width * 0.035)} ${n(topY + s * 0.12)}, ${n(rightTop)} ${n(topY)} Z`
    ].join(' ');

    return { d, leftTop, rightTop, leftMid, rightMid, bottomY, s };
  }

  function svgStart({ width, height, title, palette }) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${n(width)}" height="${n(height)}" viewBox="0 0 ${n(width)} ${n(height)}" role="img" aria-label="${escapeXml(title)}" shape-rendering="geometricPrecision">
  <title>${escapeXml(title)}</title>
  <g fill="none" stroke="${palette.outline}" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke">`;
  }

  function plantLogoMarkup(plantKind, palette) {
    const stem = `fill="none" stroke="${palette.green1}"`;
    const leaf = `fill="${palette.green1}" stroke="${palette.outline}"`;
    const lightLeaf = `fill="${palette.green2}" stroke="${palette.outline}"`;

    if (plantKind === 'radish') {
      return `
        <ellipse cx="20" cy="26" rx="7" ry="8" fill="#EF7894"/>
        <path d="M 20 33 L 18 37 M 20 33 L 22 37" fill="none" stroke="#F8E8C8"/>
        <ellipse cx="15" cy="16" rx="6.5" ry="3" transform="rotate(-38 15 16)" ${leaf}/>
        <ellipse cx="21" cy="13" rx="6" ry="2.8" transform="rotate(-82 21 13)" ${lightLeaf}/>
        <ellipse cx="25" cy="17" rx="6.5" ry="3" transform="rotate(35 25 17)" ${leaf}/>`;
    }

    if (plantKind === 'carrot') {
      return `
        <path d="M 14 17 L 26 17 L 20 36 Z" fill="#F39C42"/>
        <path d="M 17 23 L 22 22 M 17 29 L 21 28" fill="none" stroke="#AD6022"/>
        <ellipse cx="15" cy="13" rx="6.5" ry="3" transform="rotate(-35 15 13)" ${leaf}/>
        <ellipse cx="20" cy="11" rx="7" ry="3.2" transform="rotate(-90 20 11)" ${lightLeaf}/>
        <ellipse cx="25" cy="13" rx="6.5" ry="3" transform="rotate(35 25 13)" ${leaf}/>`;
    }

    if (plantKind === 'tomato') {
      return `
        <path d="M 20 35 C 19 29, 21 22, 20 14 M 20 24 C 16 22, 14 20, 12 18 M 20 27 C 24 25, 27 23, 29 20" ${stem}/>
        <ellipse cx="15" cy="16" rx="6.2" ry="2.8" transform="rotate(-28 15 16)" ${leaf}/>
        <ellipse cx="25" cy="18" rx="6.2" ry="2.8" transform="rotate(30 25 18)" ${lightLeaf}/>
        <circle cx="13" cy="24" r="5.3" fill="#E95E51" stroke="${palette.outline}"/>
        <circle cx="27" cy="26" r="5.1" fill="#F27A58" stroke="${palette.outline}"/>
        <circle cx="20" cy="31" r="5.6" fill="#E95E51" stroke="${palette.outline}"/>
        <path d="M 10 20 L 13 23 L 16 20 M 24 22 L 27 25 L 30 22 M 17 27 L 20 30 L 23 27" fill="none" stroke="${palette.green1}"/>
        <path d="M 11 23 Q 12 21 14 21 M 25 25 Q 26 23 28 23 M 18 30 Q 19 28 21 28" fill="none" stroke="#FFD7B6"/>`;
    }

    if (plantKind === 'sunflower') {
      const petals = Array.from(
        { length: 8 },
        (_, petalIndex) =>
          `<ellipse cx="20" cy="7" rx="2.7" ry="5" fill="#F5C84C" transform="rotate(${petalIndex * 45} 20 13)"/>`
      ).join('');

      return `
        <path d="M 20 35 C 19 28, 21 21, 20 15" ${stem}/>
        <ellipse cx="15" cy="27" rx="6.3" ry="3" transform="rotate(-35 15 27)" ${leaf}/>
        <ellipse cx="25" cy="24" rx="6.3" ry="3" transform="rotate(35 25 24)" ${lightLeaf}/>
        ${petals}
        <circle cx="20" cy="13" r="4.4" fill="#754728"/>`;
    }

    if (plantKind === 'tulip') {
      return `
        <path d="M 20 35 C 19 28, 21 21, 20 15" ${stem}/>
        <path d="M 19 29 C 9 26, 10 18, 19 24 Z" ${leaf}/>
        <path d="M 21 27 C 31 23, 30 17, 21 22 Z" ${lightLeaf}/>
        <path d="M 13 13 L 14 6 L 20 10 L 26 5 L 27 13 C 26 20, 14 20, 13 13 Z" fill="#D76F99"/>`;
    }

    return `
      <path d="M 20 34 C 19 28, 21 22, 20 17 M 18 33 C 16 28, 15 24, 13 21 M 22 33 C 24 28, 25 25, 27 22" ${stem}/>
      <ellipse cx="15" cy="25" rx="6.3" ry="3" transform="rotate(-33 15 25)" ${leaf}/>
      <ellipse cx="25" cy="24" rx="6.3" ry="3" transform="rotate(35 25 24)" ${lightLeaf}/>
      <g transform="translate(20 14)">
        <ellipse cx="0" cy="-5" rx="3.1" ry="4.4" fill="#FFF4AF"/>
        <ellipse cx="4.7" cy="-1.4" rx="3.1" ry="4.4" fill="#FFF4AF" transform="rotate(72 4.7 -1.4)"/>
        <ellipse cx="2.8" cy="4.2" rx="3.1" ry="4.4" fill="#FFF4AF" transform="rotate(144 2.8 4.2)"/>
        <ellipse cx="-2.8" cy="4.2" rx="3.1" ry="4.4" fill="#FFF4AF" transform="rotate(-144 -2.8 4.2)"/>
        <ellipse cx="-4.7" cy="-1.4" rx="3.1" ry="4.4" fill="#FFF4AF" transform="rotate(-72 -4.7 -1.4)"/>
        <circle cx="0" cy="0" r="3.2" fill="#F3BD47"/>
      </g>`;
  }

  function seedMarkup(o) {
    const { width: w, height: h, plantKind, palette: p, idPrefix: id } = o;
    const s = Math.min(w, h);
    const sw = clamp(s * 0.024, 5.5, 11);
    const capY = clamp(s * 0.04, 8, 18);
    const capH = clamp(s * 0.24, 54, 96);
    const bandH = clamp(s * 0.09, 20, 34);
    const bandY = capY + capH * 0.77;
    const bodyY = bandY + bandH * 0.86;
    const body = bodyGeometry(w, h, bodyY, sw);
    const capLeft = w * 0.17;
    const capRight = w * 0.83;
    const capD = [
      `M ${n(capLeft)} ${n(capY + capH * 0.40)}`,
      `C ${n(capLeft - w * 0.04)} ${n(capY + capH * 0.04)}, ${n(capLeft + w * 0.01)} ${n(capY)}, ${n(capLeft + w * 0.09)} ${n(capY + capH * 0.22)}`,
      `C ${n(w * 0.36)} ${n(capY - capH * 0.02)}, ${n(w * 0.42)} ${n(capY + capH * 0.03)}, ${n(w * 0.50)} ${n(capY + capH * 0.17)}`,
      `C ${n(w * 0.58)} ${n(capY + capH * 0.30)}, ${n(w * 0.61)} ${n(capY - capH * 0.03)}, ${n(w * 0.72)} ${n(capY + capH * 0.14)}`,
      `C ${n(w * 0.80)} ${n(capY + capH * 0.28)}, ${n(capRight + w * 0.04)} ${n(capY - capH * 0.01)}, ${n(capRight)} ${n(capY + capH * 0.40)}`,
      `C ${n(capRight - w * 0.01)} ${n(capY + capH * 0.83)}, ${n(capRight - w * 0.10)} ${n(capY + capH)}, ${n(w * 0.50)} ${n(capY + capH * 0.91)}`,
      `C ${n(capLeft + w * 0.10)} ${n(capY + capH)}, ${n(capLeft + w * 0.01)} ${n(capY + capH * 0.83)}, ${n(capLeft)} ${n(capY + capH * 0.40)} Z`
    ].join(' ');

    const bandX = w * 0.15;
    const bandW = w * 0.70;
    const extraBodyHeight = Math.max(h - s, 0);
    const colorBandTopY = bodyY + s * 0.05 + extraBodyHeight * 0.22;
    const colorBandHeight = s * 0.20 + extraBodyHeight * 0.325;
    const colorBandBottomY = Math.min(
      colorBandTopY + colorBandHeight,
      body.bottomY - s * 0.28
    );
    const colorBandEdgeCurve = clamp(s * 0.018, 3, 6.5);
    const colorBandD = [
      `M 0 ${n(colorBandTopY + colorBandEdgeCurve)}`,
      `Q ${n(w / 2)} ${n(colorBandTopY - colorBandEdgeCurve)} ${n(w)} ${n(colorBandTopY + colorBandEdgeCurve)}`,
      `L ${n(w)} ${n(colorBandBottomY - colorBandEdgeCurve)}`,
      `Q ${n(w / 2)} ${n(colorBandBottomY + colorBandEdgeCurve)} 0 ${n(colorBandBottomY - colorBandEdgeCurve)} Z`
    ].join(' ');
    const logoCx = w / 2;
    const logoSize = clamp(s * 0.27, 56, 104);
    const logoScale = logoSize / 40;
    const logoVisualCenterY =
      colorBandTopY + Math.min(colorBandHeight * 0.50, s * 0.18);
    const logoX = logoCx - logoScale * 20;
    const logoY = logoVisualCenterY - logoScale * 20;
    const ropeX = bandX + bandW * 0.92;
    const ropeY = bandY + bandH * 0.24;

    return `${svgStart(o)}
    <defs>
      <clipPath id="${id}-body"><path d="${body.d}"/></clipPath>
      <linearGradient id="${id}-cream" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="${p.creamTop}"/><stop offset="1" stop-color="${p.creamBottom}"/>
      </linearGradient>
    </defs>

    <path d="${body.d}" fill="url(#${id}-cream)" stroke-width="${n(sw)}"/>
    <g clip-path="url(#${id}-body)" stroke="none">
      <path d="${colorBandD}" fill="${p.tan}"/>
      <path d="M 0 ${n(colorBandTopY + colorBandHeight * 0.15)} Q ${n(w / 2)} ${n(colorBandTopY + colorBandHeight * 0.02)} ${n(w)} ${n(colorBandTopY + colorBandHeight * 0.15)} L ${n(w)} ${n(colorBandTopY + colorBandHeight * 0.38)} Q ${n(w / 2)} ${n(colorBandTopY + colorBandHeight * 0.24)} 0 ${n(colorBandTopY + colorBandHeight * 0.38)} Z" fill="${p.tanHighlight}" opacity=".52"/>
    </g>
    <path d="M ${n(w * 0.14)} ${n(colorBandTopY + colorBandEdgeCurve * 0.55)} Q ${n(w / 2)} ${n(colorBandTopY - colorBandEdgeCurve * 0.55)} ${n(w * 0.86)} ${n(colorBandTopY + colorBandEdgeCurve * 0.55)}" stroke-width="${n(sw)}"/>
    <path d="M ${n(w * 0.118)} ${n(colorBandBottomY - colorBandEdgeCurve * 0.55)} Q ${n(w / 2)} ${n(colorBandBottomY + colorBandEdgeCurve * 0.55)} ${n(w * 0.882)} ${n(colorBandBottomY - colorBandEdgeCurve * 0.55)}" stroke-width="${n(sw)}"/>
    <path d="M ${n(w * 0.16619)} ${n(h * 0.82112)} q ${n(-w * 0.025)} ${n(h * 0.05714)} ${n(w * 0.04)} ${n(h * 0.09524)}" stroke="${p.shadow}" stroke-width="${n(sw * 0.52)}" opacity=".75"/>
    <path d="M ${n(w * 0.83381)} ${n(h * 0.82112)} q ${n(w * 0.025)} ${n(h * 0.05714)} ${n(-w * 0.04)} ${n(h * 0.09524)}" stroke="${p.shadow}" stroke-width="${n(sw * 0.52)}" opacity=".75"/>

    <path d="${capD}" fill="#F5E4BF" stroke-width="${n(sw)}"/>
    <path d="M ${n(capLeft + s * 0.02)} ${n(capY + capH * 0.45)} Q ${n(capLeft + s * 0.10)} ${n(capY + capH * 0.84)} ${n(w * 0.32)} ${n(capY + capH * 0.58)} Q ${n(w * 0.42)} ${n(capY + capH * 0.92)} ${n(w * 0.54)} ${n(capY + capH * 0.57)} Q ${n(w * 0.66)} ${n(capY + capH * 0.91)} ${n(capRight - s * 0.02)} ${n(capY + capH * 0.52)}" stroke="#CEB68A" stroke-width="${n(sw * 0.6)}" opacity=".8"/>
    <rect x="${n(bandX)}" y="${n(bandY)}" width="${n(bandW)}" height="${n(bandH)}" rx="${n(bandH / 2)}" fill="#F8E8C8" stroke-width="${n(sw)}" transform="rotate(-0.4 ${n(w / 2)} ${n(bandY + bandH / 2)})"/>
    <path d="M ${n(bandX + bandH * 0.55)} ${n(bandY + bandH * 0.66)} Q ${n(w / 2)} ${n(bandY + bandH * 0.30)} ${n(bandX + bandW - bandH * 0.55)} ${n(bandY + bandH * 0.66)}" stroke="#D8C39C" stroke-width="${n(sw * 0.42)}" opacity=".8"/>

    <g transform="translate(${n(ropeX)} ${n(ropeY)}) scale(${n(bandH / 28.8)})" stroke-width="${n((sw * 0.82) / (bandH / 28.8))}">
      <path style="fill:${p.rope1};fill-opacity:1" d="M 31.749944,1.5220775 C 23.341584,1.6253193 8.1900699,2.8067209 7.8125e-5,4.9898438 24.960053,34.109815 43.040023,42.300009 65.92,51.45 86.719979,59.769992 105.8857,47.115302 96.735704,27.985321 86.009153,5.8837811 61.779798,1.1533569 31.749944,1.5220775 Z m 9.733858,9.9829845 C 60.795185,11.704165 76.173594,18.169846 84.546997,29.523599 95.23223,44.186292 78.188536,48.65269 55.382025,36.171036 40.563277,28.06974 34.345227,25.05 21.123793,12.877765 23.85681,12.456294 38.725033,11.476619 41.483802,11.505062 Z"/>
      <path d="M 7.42 6.8 C 34.46 9.9, 71.49 25.53, 70.99 44.76" stroke="${p.ropeHighlight}" stroke-width="${n((sw * 0.38) / (bandH / 28.8))}"/>
      <path d="M 6.3164844,4.6734375 C 4.1692961,4.6861437 2.0586857,4.7899066 7.8125e-5,4.9898438 2.1658253,29.391965 13.536036,50.869234 34.336016,65.849219 55.135995,80.819204 80.699218,69.889351 79.869219,45.759375 79.091095,24.703146 38.524309,4.4828446 6.3164844,4.6734375 Z M 12.052328,15.590705 C 29.971176,16.969856 46.925955,25.25371 60.304766,36.222266 75.687153,50.373371 59.637359,71.513617 39.135061,56.494251 22.807696,42.960181 14.97153,30.811294 12.052328,15.590705 Z" fill="${p.rope2}"/>
      <path d="M 5.5398656,8.1942655 C 55.236294,13.884266 76.651326,33.715729 75.731735,49.6037" fill="none" stroke="${p.ropeHighlight}" stroke-width="${n((sw * 0.38) / (bandH / 28.8))}"/>
    </g>

    <g transform="translate(${n(logoX)} ${n(logoY)}) scale(${n(logoScale)})" stroke-width="${n((sw * 0.72) / logoScale)}">
      ${plantLogoMarkup(plantKind, p)}
    </g>
  </g>
</svg>`;
  }

  function fertilizerMarkup(o) {
    const { width: w, height: h, palette: p, idPrefix: id } = o;
    const s = Math.min(w, h);
    const sw = clamp(s * 0.024, 5.5, 11);
    const moundTop = clamp(s * 0.035, 6, 15);
    const moundH = clamp(s * 0.22, 48, 88);
    const lipH = clamp(s * 0.115, 24, 42);
    const lipY = moundTop + moundH * 0.76;
    const bodyY = lipY + lipH * 0.88;
    const body = bodyGeometry(w, h, bodyY, sw);
    const moundD = [
      `M ${n(w * 0.19)} ${n(lipY + lipH * 0.18)}`,
      `C ${n(w * 0.16)} ${n(moundTop + moundH * 0.72)}, ${n(w * 0.21)} ${n(moundTop + moundH * 0.46)}, ${n(w * 0.29)} ${n(moundTop + moundH * 0.45)}`,
      `C ${n(w * 0.30)} ${n(moundTop + moundH * 0.10)}, ${n(w * 0.42)} ${n(moundTop)}, ${n(w * 0.50)} ${n(moundTop + moundH * 0.20)}`,
      `C ${n(w * 0.57)} ${n(moundTop - moundH * 0.02)}, ${n(w * 0.70)} ${n(moundTop + moundH * 0.06)}, ${n(w * 0.72)} ${n(moundTop + moundH * 0.40)}`,
      `C ${n(w * 0.82)} ${n(moundTop + moundH * 0.40)}, ${n(w * 0.86)} ${n(moundTop + moundH * 0.70)}, ${n(w * 0.80)} ${n(lipY + lipH * 0.18)}`,
      `Q ${n(w * 0.50)} ${n(lipY + lipH * 0.46)} ${n(w * 0.19)} ${n(lipY + lipH * 0.18)} Z`
    ].join(' ');
    const lipX = w * 0.15;
    const lipW = w * 0.70;
    const stitchY = bodyY + clamp(s * 0.105, 22, 42);
    const stitchLeft = w * 0.27;
    const stitchRight = w * 0.75;
    const count = Math.max(3, Math.min(7, Math.floor((stitchRight - stitchLeft) / (s * 0.18))));
    const stitchSize = clamp(s * 0.06, 12, 24);
    const stitches = Array.from({ length: count }, (_, i) => {
      const x = count === 1 ? w / 2 : stitchLeft + i * (stitchRight - stitchLeft) / (count - 1);
      const y = stitchY + (i % 2) * stitchSize * 0.18;
      return `<path d="M ${n(x - stitchSize)} ${n(y - stitchSize * 0.65)} L ${n(x + stitchSize)} ${n(y + stitchSize * 0.65)} M ${n(x + stitchSize * 0.82)} ${n(y - stitchSize * 0.78)} L ${n(x - stitchSize * 0.82)} ${n(y + stitchSize * 0.78)}"/>`;
    }).join('\n      ');

    return `${svgStart(o)}
    <defs>
      <clipPath id="${id}-body"><path d="${body.d}"/></clipPath>
      <clipPath id="${id}-mound"><path d="${moundD}"/></clipPath>
      <linearGradient id="${id}-cream" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF1D1"/><stop offset="1" stop-color="#E6CFA2"/></linearGradient>
      <linearGradient id="${id}-soil" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${p.soilTop}"/><stop offset="1" stop-color="${p.soilBottom}"/></linearGradient>
    </defs>

    <path d="${body.d}" fill="url(#${id}-cream)" stroke-width="${n(sw)}"/>
    <g clip-path="url(#${id}-body)" stroke="none" transform="matrix(0.95913475,0,0,1,${n(w * 0.020432625)},0)">
      <path d="M 0 ${n(bodyY + s * 0.015)} Q ${n(w / 2)} ${n(bodyY - s * 0.035)} ${n(w)} ${n(bodyY + s * 0.015)} L ${n(w)} ${n(bodyY + s * 0.14)} Q ${n(w / 2)} ${n(bodyY + s * 0.07)} 0 ${n(bodyY + s * 0.14)} Z" fill="#F7E5BE"/>
    </g>
    <path d="${moundD}" fill="url(#${id}-soil)" stroke-width="${n(sw)}"/>

    <g clip-path="url(#${id}-mound)" stroke-width="${n(sw * 0.58)}">
      <ellipse cx="${n(w * 0.35)}" cy="${n(moundTop + moundH * 0.50)}" rx="${n(s * 0.034)}" ry="${n(s * 0.021)}" fill="${p.soilDark}"/>
      <ellipse cx="${n(w * 0.59)}" cy="${n(moundTop + moundH * 0.38)}" rx="${n(s * 0.042)}" ry="${n(s * 0.026)}" fill="#B7D56F" transform="rotate(-28 ${n(w * 0.59)} ${n(moundTop + moundH * 0.38)})"/>
      <path d="M ${n(w * 0.57)} ${n(moundTop + moundH * 0.40)} L ${n(w * 0.61)} ${n(moundTop + moundH * 0.34)}"/>
      <ellipse cx="${n(w * 0.44)}" cy="${n(moundTop + moundH * 0.72)}" rx="${n(s * 0.045)}" ry="${n(s * 0.027)}" fill="#C7DE82" transform="rotate(20 ${n(w * 0.44)} ${n(moundTop + moundH * 0.72)})"/>
      <path d="M ${n(w * 0.42)} ${n(moundTop + moundH * 0.69)} L ${n(w * 0.47)} ${n(moundTop + moundH * 0.75)}"/>
      <circle cx="${n(w * 0.68)}" cy="${n(moundTop + moundH * 0.66)}" r="${n(s * 0.022)}" fill="#3E2A1C"/>
      <circle cx="${n(w * 0.29)}" cy="${n(moundTop + moundH * 0.76)}" r="${n(s * 0.019)}" fill="#4A3020"/>
    </g>

    <rect x="${n(lipX)}" y="${n(lipY)}" width="${n(lipW)}" height="${n(lipH)}" rx="${n(lipH * 0.48)}" fill="#F8E8C8" stroke-width="${n(sw)}" transform="rotate(0.5 ${n(w / 2)} ${n(lipY + lipH / 2)})"/>
    <path d="M ${n(lipX + lipH * 0.55)} ${n(lipY + lipH * 0.38)} Q ${n(w / 2)} ${n(lipY + lipH * 0.78)} ${n(lipX + lipW - lipH * 0.55)} ${n(lipY + lipH * 0.38)}" stroke="#D8C197" stroke-width="${n(sw * 0.46)}" opacity=".8"/>

    <g stroke-width="${n(sw * 0.5)}">
      ${stitches}
    </g>
    <path d="M ${n(body.leftMid + s * 0.045)} ${n(body.bottomY - s * 0.22)} Q ${n(body.leftMid + s * 0.015)} ${n(body.bottomY - s * 0.13)} ${n(body.leftMid + s * 0.08)} ${n(body.bottomY - s * 0.08)}" stroke="${p.shadow}" stroke-width="${n(sw * 0.52)}" opacity=".75"/>
    <path d="M ${n(body.rightMid - s * 0.045)} ${n(body.bottomY - s * 0.22)} Q ${n(body.rightMid - s * 0.015)} ${n(body.bottomY - s * 0.13)} ${n(body.rightMid - s * 0.08)} ${n(body.bottomY - s * 0.08)}" stroke="${p.shadow}" stroke-width="${n(sw * 0.52)}" opacity=".75"/>
  </g>
</svg>`;
  }

  function markup(options = {}) {
    const o = normalizedOptions(options);
    return o.kind === 'fertilizer' ? fertilizerMarkup(o) : seedMarkup(o);
  }

  function mount(target, options = {}) {
    const element = typeof target === 'string' ? document.querySelector(target) : target;
    if (!element) throw new Error('SackGraphics.mount: Ziel-Element wurde nicht gefunden.');
    element.innerHTML = markup(options);
    return element.querySelector('svg');
  }

  global.SackGraphics = Object.freeze({ markup, mount, palette: PALETTE });
})(typeof window !== 'undefined' ? window : globalThis);
