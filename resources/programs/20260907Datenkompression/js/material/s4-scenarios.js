window.buildS4Scenarios = function (lang) {
  var MB = 1024 * 1024;
  var meta = (lang && lang.meta) || {};

  function logGroupName(start, end) {
    var template = lang && lang.logGroupName;
    if (!template) { return ""; }
    return template
      .replace("{start}", String(start).padStart(4, "0"))
      .replace("{end}", String(end).padStart(4, "0"));
  }

  function buildLogGroups(totalFiles, groupSize, bytesPerFile) {
    var groups = [];
    var remaining = totalFiles;
    var idx = 1;
    while (remaining > 0) {
      var n = Math.min(groupSize, remaining);
      var start = idx;
      var end = idx + n - 1;
      groups.push({
        name: logGroupName(start, end),
        bytes: n * bytesPerFile,
        type: "text",
        count: n,
        metadata: []
      });
      idx += n;
      remaining -= n;
    }
    return groups;
  }

  function file(name, bytes, type, extra) {
    var f = { name: name, bytes: bytes, type: type, metadata: [] };
    if (extra) {
      Object.keys(extra).forEach(function (k) { f[k] = extra[k]; });
    }
    if (!f.metadata) { f.metadata = []; }
    return f;
  }

  return [
    {
      id: "s0",
      layout: "s1",
      tutorial: true,
      files: [
        file("demo_gps.mp4", 18000 * MB, "video", { metadata: [meta.gpsParkSouth] }),
        file("kamera_probe.mp4", 2000 * MB, "video"),
        file("uebung_notiz.docx", 200 * MB, "text", { metadata: [meta.authorEditorial] }),
        file("readme.txt", 80 * MB, "text")
      ]
    },
    {
      id: "s1",
      layout: "s1",
      files: [
        file("interview_keller.mp4", 5200 * MB, "video", { metadata: [meta.authorKeller] }),
        file("ueberwachung_hof.mp4", 5000 * MB, "video"),
        file("treffen_park.mp4", 4800 * MB, "video", { metadata: [meta.gpsParkSouth] }),
        file("hintereingang.mp4", 4600 * MB, "video"),
        file("parkplatz_nacht.mp4", 4400 * MB, "video", { metadata: [meta.cameraIdNorth] }),
        file("empfang_halle.mp4", 4200 * MB, "video", { metadata: [meta.authorSecurityNorth] }),
        file("fluchtweg.mp4", 4000 * MB, "video"),
        file("notiz_01.txt", 450 * MB, "text"),
        file("notiz_02.txt", 420 * MB, "text"),
        file("notiz_03.txt", 380 * MB, "text"),
        file("notiz_04.txt", 350 * MB, "text"),
        file("notiz_05.txt", 320 * MB, "text"),
        file("notiz_06.txt", 290 * MB, "text"),
        file("notiz_07.txt", 260 * MB, "text"),
        file("notiz_08.txt", 230 * MB, "text"),
        file("kontakte.csv", 750 * MB, "text"),
        file("zeitplan.csv", 680 * MB, "text"),
        file("abrechnung.csv", 620 * MB, "text"),
        file("protokoll.csv", 580 * MB, "text")
      ]
    },
    {
      id: "s2",
      layout: "s2",
      files: (function () {
        var files = buildLogGroups(4200, 100, 8 * MB);
        files.push(
          file("beweis_foto_01.raw", 2400 * MB, "image", { metadata: [meta.cameraNikon, meta.iso6400] }),
          file("beweis_foto_02.raw", 2100 * MB, "image"),
          file("beweis_foto_03.raw", 1900 * MB, "image", { metadata: [meta.captureTime] }),
          file("beweis_foto_04.raw", 1600 * MB, "image"),
          file("bericht_entwurf.docx", 220 * MB, "text", { metadata: [meta.authorEditorial, meta.editedByWeber] }),
          file("zusammenfassung.docx", 195 * MB, "text", { metadata: [meta.authorUnknown] }),
          file("protokoll_sitzung.docx", 180 * MB, "text"),
          file("notizen_redaktion.docx", 165 * MB, "text", { metadata: [meta.comments14] }),
          file("anhang_office.docx", 150 * MB, "text"),
          file("kontakte.csv", 140 * MB, "text"),
          file("zeitplan.csv", 125 * MB, "text"),
          file("abrechnung.csv", 110 * MB, "text"),
          file("auswertung.csv", 95 * MB, "text")
        );
        return files;
      })()
    },
    {
      id: "s3",
      layout: "s3",
      files: [
        file("bericht_1.txt", 1200 * MB, "text"),
        file("bericht_2.txt", 1100 * MB, "text"),
        file("protokoll.txt", 1000 * MB, "text"),
        file("notizen.txt", 950 * MB, "text"),
        file("anhang.txt", 900 * MB, "text"),
        file("kontakte.csv", 850 * MB, "text"),
        file("zusammenfassung.txt", 800 * MB, "text"),
        file("anmerkungen.txt", 750 * MB, "text"),
        file("bericht_redaktion.docx", 480 * MB, "text", { metadata: [meta.authorHoffmann] }),
        file("notizen_office.docx", 420 * MB, "text"),
        file("protokoll_final.docx", 390 * MB, "text", { metadata: [meta.authorSecretariat] }),
        file("anhang_word.docx", 360 * MB, "text", { metadata: [meta.authorGuest] }),
        file("planung.csv", 340 * MB, "text"),
        file("kosten.csv", 310 * MB, "text"),
        file("auswertung.csv", 280 * MB, "text"),
        file("beweis_01.tiff", 620 * MB, "image", { metadata: [meta.gpsWorkyard] }),
        file("beweis_02.tiff", 580 * MB, "image"),
        file("beweis_03.tiff", 540 * MB, "image", { metadata: [meta.scannerFujitsu] }),
        file("scan.psd", 1800 * MB, "image", { metadata: [meta.layers28, meta.editHistory96] }),
        file("montage.psd", 1500 * MB, "image", { metadata: [meta.layers12] }),
        file("gespraech_01.mp4", 3200 * MB, "video", { metadata: [meta.authorPhoneRecording] }),
        file("gespraech_02.mp4", 2800 * MB, "video"),
        file("aufnahme_hof.mp4", 2500 * MB, "video", { metadata: [meta.gpsEmbedded] }),
        file("nachtaufnahme.mp4", 2200 * MB, "video"),
        file("dokumente.enc.7z", 4200 * MB, "encrypted"),
        file("datenbanken.enc.zip", 3600 * MB, "encrypted"),
        file("schluessel.enc.rar", 3100 * MB, "encrypted")
      ]
    }
  ];
};

window.s4IsProblematicMeta = function (line) {
  var s = String(line);
  if (/GPS/i.test(s)) { return true; }
  if (/Bearbeitet von:/i.test(s)) { return true; }
  if (/Kamera-ID:/i.test(s)) { return true; }
  if (/Verzeichnis/i.test(s)) { return true; }
  if (/^Inhalt:/i.test(s)) { return true; }
  if (/Bearbeitungshistorie/i.test(s)) { return true; }
  if (/Kommentare/i.test(s)) { return true; }
  if (/^Autor:/i.test(s) && !/Autor:\s*Unbekannt/i.test(s)) { return true; }
  return false;
};

window.s4StripProblematicMeta = function (metadata) {
  return (metadata || []).filter(function (line) { return !s4IsProblematicMeta(line); });
};

window.s4HasProblematicMeta = function (file) {
  return !!(file && file.metadata && file.metadata.some(s4IsProblematicMeta));
};

window.s4EnsureResolution = function (metadata, resolution) {
  var lines = metadata ? metadata.slice() : [];
  if (!lines.some(function (l) { return /Auflösung:/i.test(l); })) {
    lines.push(resolution);
  }
  return lines;
};

window.s4ComputeDisplayShares = function (files) {
  var n = files.length;
  if (!n) { return []; }
  var total = files.reduce(function (s, f) { return s + f.bytes; }, 0);
  if (!total) {
    return files.map(function () { return 100 / n; });
  }
  var minPct = Math.min(4.2, 72 / n);
  var reserved = minPct * n;
  var flexible = 100 - reserved;
  return files.map(function (f) {
    return minPct + (f.bytes / total) * flexible;
  });
};

window.s4ComputeGroupShares = function (groups) {
  var totals = groups.map(function (g) {
    return g.reduce(function (s, f) { return s + f.bytes; }, 0);
  });
  var n = totals.length;
  if (!n) { return []; }
  var total = totals.reduce(function (s, b) { return s + b; }, 0);
  if (!total) {
    return totals.map(function () { return 100 / n; });
  }
  var minPct = Math.min(14, 52 / n);
  var reserved = minPct * n;
  var flexible = 100 - reserved;
  return totals.map(function (b) {
    return minPct + (b / total) * flexible;
  });
};

window.getS4MetadataTooltip = function (file, fsLang) {
  var lines = file && file.metadata;
  if (!lines || !lines.length) { return ""; }
  var label = fsLang && fsLang.tooltipMetadata;
  if (!label) { return ""; }
  return label + "<br>" + lines.map(function (line) {
    var text = String(line)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
    if (typeof s4IsProblematicMeta === "function" && s4IsProblematicMeta(line)) {
      return '<span class="s4-meta-bad">' + text + "</span>";
    }
    return '<span class="s4-meta-ok-line">' + text + "</span>";
  }).join("<br>");
};
