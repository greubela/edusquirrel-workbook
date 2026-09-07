function setupFilesystemSimulator(source) {
  const t = source || getWidgetLang("filesystemSimulator");
  const s4Lang = getWidgetLang("s4Scenarios") || {};
  var CARD_BYTES = 16 * 1024 * 1024 * 1024;
  var MB = 1024 * 1024;
  var GB = 1024 * 1024 * 1024;
  var ARCHIVE_OVERHEAD = 48 * MB;

  var FILE_COLORS = {
    video: "#c0392b",
    text: "#2980b9",
    spreadsheet: "#27ae60",
    image: "#8e44ad",
    encrypted: "#2c3e50",
    archive: "#7f5539"
  };

  var TOOL_COLORS = {
    convert: "#d35400",
    lossless: "#27ae60",
    lossy: "#c0392b",
    archive: "#6c3483"
  };

  var TYPE_LABELS = (getWidgetLang("filesystem").typeLabels || {});

  var SCENARIO_DEFS = (window.S4_SCENARIOS || []).map(function (def) {
    return {
      id: def.id,
      layout: def.layout,
      tutorial: !!def.tutorial,
      label: (t.scenarios && t.scenarios[def.id]) || def.id
    };
  });

  var deltaTimers = {};
  var pendingDelta = {};
  function cloneApplied(source) {
    var a = source || {};
    return { convert: !!a.convert, lossless: !!a.lossless, lossy: !!a.lossy };
  }

  function cloneFiles(files) {
    return files.map(function (f) {
      var c = {
        name: f.name,
        bytes: f.bytes,
        type: f.type,
        metadata: f.metadata ? f.metadata.slice() : [],
        applied: cloneApplied(f.applied)
      };
      if (f.count) { c.count = f.count; }
      return c;
    });
  }

  function getInitialFiles(id) {
    var def = (window.S4_SCENARIOS || []).find(function (s) { return s.id === id; });
    return def ? cloneFiles(def.files) : [];
  }

  function formatSize(bytes) {
    if (bytes >= GB) {
      return (bytes / GB).toLocaleString("de-DE", { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + " GB";
    }
    if (bytes >= MB) {
      return (bytes / MB).toLocaleString("de-DE", { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + " MB";
    }
    if (bytes >= 1024) {
      return (bytes / 1024).toLocaleString("de-DE", { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + " KB";
    }
    return bytes.toLocaleString("de-DE") + " Bytes";
  }

  function formatGb(bytes) {
    return (bytes / GB).toLocaleString("de-DE", { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + " GB";
  }

  function sumBytes(files) {
    return files.reduce(function (s, f) { return s + f.bytes; }, 0);
  }

  function countFiles(files) {
    return files.reduce(function (n, f) { return n + (f.count || 1); }, 0);
  }

  function pct(part, total) {
    return total > 0 ? (part / total) * 100 : 0;
  }

  function fileKind(name) {
    if (/\.enc\.(7z|zip|rar)$/i.test(name)) { return "encrypted"; }
    if (/\.mp4$/i.test(name)) { return "mp4"; }
    if (/\.docx$/i.test(name)) { return "docx"; }
    if (/\.csv$/i.test(name)) { return "csv"; }
    if (/\.txt$/i.test(name)) { return "txt"; }
    if (/\.raw$/i.test(name)) { return "raw"; }
    if (/\.tiff$/i.test(name)) { return "tiff"; }
    if (/\.psd$/i.test(name)) { return "psd"; }
    if (/\.(png|jpg|jpeg)$/i.test(name)) { return "image_done"; }
    if (/\.(zip|7z|rar)$/i.test(name) && !/\.enc\./i.test(name)) { return "archive"; }
    return "other";
  }

  function replaceExt(name, ext) {
    return name.replace(/\.[^.]+$/, "." + ext);
  }

  function isEncryptedFile(file) {
    return file.type === "encrypted" || fileKind(file.name) === "encrypted";
  }

  function canApplyTool(file, tool) {
    if (tool === "archive") {
      return true;
    }
    if (isEncryptedFile(file)) {
      return false;
    }
    if (file.type === "archive") {
      return false;
    }
    if ((tool === "convert" || tool === "lossless" || tool === "lossy") &&
        file.applied && file.applied[tool]) {
      return false;
    }
    var kind = fileKind(file.name);
    if (tool === "convert") {
      if (file.type === "archive" || kind === "csv") {
        return false;
      }
      return true;
    }
    if (tool === "lossless") {
      return kind === "txt" || kind === "csv" || kind === "docx" || kind === "raw" || kind === "tiff" || kind === "mp4";
    }
    if (tool === "lossy") {
      return kind === "mp4" || kind === "raw" || kind === "tiff" || kind === "psd";
    }
    return false;
  }

  function applySingleOp(file, tool) {
    var kind = fileKind(file.name);
    var next = {
      name: file.name,
      bytes: file.bytes,
      type: file.type,
      metadata: file.metadata ? file.metadata.slice() : [],
      applied: cloneApplied(file.applied)
    };
    if (file.count) { next.count = file.count; }

    if (tool === "convert") {
      if (file.type === "archive" || kind === "encrypted" || kind === "csv") {
        return null;
      }
      next.metadata = [];

      if (kind === "docx") {
        next.name = replaceExt(file.name, "txt");
        next.bytes = Math.round(file.bytes * 0.22);
      } else if (kind === "raw") {
        next.name = replaceExt(file.name, "png");
        next.type = "image";
        next.bytes = Math.round(file.bytes * 0.42);
      } else if (kind === "tiff") {
        next.name = replaceExt(file.name, "png");
        next.bytes = Math.round(file.bytes * 0.52);
      } else if (kind === "psd") {
        next.name = replaceExt(file.name, "png");
        next.type = "image";
        next.bytes = Math.round(file.bytes * 0.14);
      }
      // mp4, txt und andere: Metadaten vollständig entfernen (.csv bleibt unverändert)
    } else if (tool === "lossless") {
      if (kind === "mp4") {
        next.bytes = Math.round(file.bytes * 0.96);
      } else if (kind === "txt" || kind === "csv") {
        next.bytes = Math.round(file.bytes * 0.34);
      } else if (kind === "docx") {
        next.bytes = Math.round(file.bytes * 0.86);
      } else if (kind === "raw" || kind === "tiff") {
        next.name = replaceExt(file.name, "png");
        next.type = "image";
        next.bytes = Math.round(file.bytes * (kind === "raw" ? 0.42 : 0.52));
        next.metadata = typeof s4EnsureResolution === "function"
          ? s4EnsureResolution(file.metadata, kind === "raw" ? s4Lang.resolutionRaw : s4Lang.resolutionTiff)
          : (file.metadata || []).slice();
      } else {
        return null;
      }
    } else if (tool === "lossy") {
      if (kind === "mp4") {
        next.bytes = Math.round(file.bytes * 0.11);
      } else if (kind === "raw" || kind === "tiff" || kind === "psd") {
        next.name = replaceExt(file.name, "jpg");
        next.type = "image";
        next.bytes = Math.round(file.bytes * (kind === "psd" ? 0.05 : 0.09));
      } else {
        return null;
      }
    } else {
      return null;
    }

    if (next.bytes < 1) { next.bytes = 1; }
    next.applied[tool] = true;
    return next;
  }

  function archiveBytes(files) {
    var textBytes = 0;
    var softBytes = 0;
    var hardBytes = 0;
    files.forEach(function (f) {
      var kind = fileKind(f.name);
      if (f.type === "text" || kind === "txt" || kind === "csv") {
        textBytes += f.bytes;
      } else if (f.type === "video" || f.type === "encrypted" || kind === "mp4" ||
          /\.(jpg|jpeg)$/i.test(f.name)) {
        hardBytes += f.bytes;
      } else {
        softBytes += f.bytes;
      }
    });
    return Math.round(textBytes * 0.22 + softBytes * 0.58 + hardBytes * 0.97 + ARCHIVE_OVERHEAD);
  }

  function buildArchiveName(files) {
    var totalCount = files.reduce(function (n, f) { return n + (f.count || 1); }, 0);
    if (files.length === 1 && files[0].count) {
      return "logs_" + files[0].count + "_dateien.rar";
    }
    if (totalCount > files.length) {
      return "logs_" + totalCount + "_dateien.rar";
    }
    if (files.length === 1) {
      return replaceExt(files[0].name, "rar");
    }
    return "paket_" + files.length + "_dateien.rar";
  }

  function segmentStyle(color, extra) {
    return (
      "background:" + color + ";" +
      "border:1px solid rgba(0,0,0,0.12);" +
      "box-sizing:border-box;cursor:pointer;position:relative;" +
      "min-width:12px;min-height:12px;overflow:hidden;" +
      "transition:filter 120ms ease, box-shadow 120ms ease" + (extra || "")
    );
  }

  function flexPctStyle(share, axis) {
    var pct = share.toFixed(4) + "%";
    if (axis === "col") {
      return "flex:0 0 " + pct + ";height:" + pct + ";width:100%;min-height:12px;";
    }
    return "flex:0 0 " + pct + ";width:" + pct + ";height:100%;min-width:12px;";
  }

  function ensureTooltip() {
    var tip = document.getElementById("fss-tooltip");
    if (tip) {
      tip.style.zIndex = "10001";
      return tip;
    }
    tip = document.createElement("div");
    tip.id = "fss-tooltip";
    tip.setAttribute("role", "tooltip");
    tip.hidden = true;
    tip.style.cssText =
      "position:fixed;z-index:10001;pointer-events:none;background:#122;color:#fff;" +
      "padding:8px 10px;border-radius:4px;font-size:12px;line-height:1.5;font-family:sans-serif;" +
      "box-shadow:0 4px 12px rgba(0,0,0,0.2);max-width:min(320px,90vw)";
    document.body.appendChild(tip);
    return tip;
  }

  function fileFromSegment(seg) {
    var metadata = [];
    var rawMeta = seg.getAttribute("data-metadata");
    if (rawMeta) {
      try { metadata = JSON.parse(decodeURIComponent(rawMeta)); } catch (err) { metadata = []; }
    }
    var countRaw = seg.getAttribute("data-count");
    return {
      name: seg.getAttribute("data-name") || "",
      type: seg.getAttribute("data-type") || "",
      bytes: parseInt(seg.getAttribute("data-bytes"), 10) || 0,
      metadata: metadata,
      count: countRaw ? parseInt(countRaw, 10) : 0
    };
  }

  var pointer = { x: 0, y: 0 };
  document.addEventListener("mousemove", function (e) {
    pointer.x = e.clientX;
    pointer.y = e.clientY;
  }, true);

  function hideFssTooltip() {
    ensureTooltip().hidden = true;
  }

  function positionFssTooltip(e) {
    var tooltip = ensureTooltip();
    var clientX = e && e.clientX != null ? e.clientX : pointer.x;
    var clientY = e && e.clientY != null ? e.clientY : pointer.y;
    tooltip.hidden = false;
    var offset = 14;
    var x = clientX + offset;
    var y = clientY + offset;
    tooltip.style.left = x + "px";
    tooltip.style.top = y + "px";
    var rect = tooltip.getBoundingClientRect();
    if (rect.right > window.innerWidth - 8) {
      x = clientX - rect.width - offset;
      tooltip.style.left = Math.max(8, x) + "px";
    }
    if (rect.bottom > window.innerHeight - 8) {
      y = clientY - rect.height - offset;
      tooltip.style.top = Math.max(8, y) + "px";
    }
  }

  function showFssTooltip(e, file) {
    if (!file) { return; }
    var tooltip = ensureTooltip();
    var fsLang = getWidgetLang("filesystem");
    var html =
      "<strong style=\"display:block;margin-bottom:2px;font-family:monospace;font-size:11px\">" + file.name + "</strong>" +
      (TYPE_LABELS[file.type] || file.type) +
      "<br>" + formatSize(file.bytes);
    if (file.count) {
      html += "<br>" + formatLang((fsLang.tooltipFiles || "{count} Dateien"), {
        count: file.count.toLocaleString("de-DE")
      });
    }
    var metaHtml = typeof getS4MetadataTooltip === "function" ? getS4MetadataTooltip(file, fsLang) : "";
    if (metaHtml) {
      html += "<br><span style=\"opacity:0.92\">" + metaHtml + "</span>";
    }
    tooltip.innerHTML = html;
    positionFssTooltip(e);
  }

  function fileForSegment(seg, scenarioId) {
    var state = states[scenarioId];
    if (!state) { return null; }
    var idx = parseInt(seg.getAttribute("data-index"), 10);
    return state.files[idx] || fileFromSegment(seg);
  }

  function refreshTooltipUnderPointer(host, scenarioId) {
    var el = document.elementFromPoint(pointer.x, pointer.y);
    var seg = el && el.closest(".fss-segment");
    if (!seg || !host.contains(seg)) {
      hideFssTooltip();
      return;
    }
    var file = fileForSegment(seg, scenarioId);
    if (file) {
      showFssTooltip({ clientX: pointer.x, clientY: pointer.y }, file);
      seg.style.filter = "brightness(1.12)";
    }
  }

  function renderLeaf(file, flexStyle, extraStyle, index, selected) {
    var color = file.type === "archive" ? FILE_COLORS.archive : (FILE_COLORS[file.type] || "#888");
    var selectedClass = selected ? " fss-segment--selected" : "";
    var countAttr = file.count ? ' data-count="' + file.count + '"' : "";
    var metaAttr = "";
    if (file.metadata && file.metadata.length) {
      metaAttr = ' data-metadata="' + encodeURIComponent(JSON.stringify(file.metadata)) + '"';
    }
    return (
      '<div class="fss-segment' + selectedClass + '" tabindex="0" data-index="' + index + '"' +
        ' data-name="' + file.name + '"' +
        ' data-type="' + file.type + '"' +
        ' data-bytes="' + file.bytes + '"' +
        countAttr + metaAttr +
        ' style="' + segmentStyle(color) + flexStyle + (extraStyle || "") + '"' +
        ' aria-label="' + file.name + ", " + formatSize(file.bytes) + '"' +
        (selected ? ' aria-pressed="true"' : "") +
      '></div>'
    );
  }

  function renderTreemap(files, layout, selectedSet) {
    if (!files.length) {
      return '<div style="display:flex;align-items:center;justify-content:center;width:100%;height:100%;color:var(--muted);font-size:13px">' +
        (t.empty || "") + '</div>';
    }

    var computeShares = window.s4ComputeDisplayShares || function (list) {
      var total = sumBytes(list);
      return list.map(function (f) { return pct(f.bytes, total); });
    };
    var computeGroupShares = window.s4ComputeGroupShares || function (groups) {
      var totals = groups.map(sumBytes);
      var total = totals.reduce(function (s, b) { return s + b; }, 0);
      return totals.map(function (b) { return pct(b, total); });
    };

    if (layout === "s2") {
      var gridIndices = [];
      var colIndices = [];
      var colFiles = [];
      files.forEach(function (f, i) {
        if (f.count && f.type !== "archive") {
          gridIndices.push(i);
        } else {
          colIndices.push(i);
          colFiles.push(f);
        }
      });
      var layoutGroups = [];
      if (gridIndices.length) {
        layoutGroups.push(gridIndices.map(function (i) { return files[i]; }));
      }
      if (colFiles.length) {
        layoutGroups.push(colFiles);
      }
      var groupShares = computeGroupShares(layoutGroups);
      var cols = 7;
      var rows = Math.ceil(gridIndices.length / cols);
      var colShares = computeShares(colFiles);
      var shareIdx = 0;
      var html = '<div style="display:flex;flex-direction:row;width:100%;height:100%">';
      if (gridIndices.length) {
        html +=
          '<div class="fss-grid-group" style="flex:0 0 ' + groupShares[shareIdx++].toFixed(4) + '%;height:100%;display:grid;' +
            "grid-template-columns:repeat(" + cols + ",1fr);" +
            "grid-template-rows:repeat(" + rows + ",1fr);" +
            'gap:1px;padding:1px;background:rgba(0,0,0,0.08)">' +
            gridIndices.map(function (idx) {
              var f = files[idx];
              return renderLeaf(f, "", "width:100%;height:100%;min-width:12px;min-height:12px", idx, selectedSet && selectedSet.has(idx));
            }).join("") +
          '</div>';
      }
      if (colFiles.length) {
        html +=
          '<div style="display:flex;flex-direction:column;flex:0 0 ' + groupShares[shareIdx].toFixed(4) + '%;height:100%">' +
            colIndices.map(function (idx, i) {
              var f = files[idx];
              return renderLeaf(f, flexPctStyle(colShares[i], "col"), "", idx, selectedSet && selectedSet.has(idx));
            }).join("") +
          '</div>';
      }
      html += '</div>';
      return html;
    }

    if (layout === "s1") {
      var videos = [];
      var texts = [];
      var vIdx = [];
      var tIdx = [];
      files.forEach(function (f, i) {
        if (f.type === "video") { videos.push(f); vIdx.push(i); }
        else { texts.push(f); tIdx.push(i); }
      });
      var s1GroupShares = computeGroupShares([videos, texts]);
      var videoShares = computeShares(videos);
      var textShares = computeShares(texts);
      return (
        '<div style="display:flex;flex-direction:row;width:100%;height:100%">' +
          '<div style="display:flex;flex-direction:column;flex:0 0 ' + s1GroupShares[0].toFixed(4) + '%;height:100%">' +
            vIdx.map(function (idx, i) {
              var f = files[idx];
              return renderLeaf(f, flexPctStyle(videoShares[i], "col"), "", idx, selectedSet && selectedSet.has(idx));
            }).join("") +
          '</div>' +
          '<div style="display:flex;flex-direction:column;flex:0 0 ' + s1GroupShares[1].toFixed(4) + '%;height:100%">' +
            tIdx.map(function (idx, i) {
              var f = files[idx];
              return renderLeaf(f, flexPctStyle(textShares[i], "col"), "", idx, selectedSet && selectedSet.has(idx));
            }).join("") +
          '</div>' +
        '</div>'
      );
    }

    if (layout === "s3") {
      var docs = [];
      var media = [];
      var encrypted = [];
      var archives = [];
      var dIdx = [];
      var mIdx = [];
      var eIdx = [];
      var aIdx = [];
      files.forEach(function (f, i) {
        if (f.type === "archive") { archives.push(f); aIdx.push(i); }
        else if (f.type === "encrypted") { encrypted.push(f); eIdx.push(i); }
        else if (f.type === "image" || f.type === "video") { media.push(f); mIdx.push(i); }
        else { docs.push(f); dIdx.push(i); }
      });
      var groupDefs = [
        { items: docs, idx: dIdx },
        { items: media, idx: mIdx },
        { items: encrypted, idx: eIdx },
        { items: archives, idx: aIdx }
      ].filter(function (g) { return g.items.length; });
      var s3GroupShares = computeGroupShares(groupDefs.map(function (g) { return g.items; }));
      return (
        '<div style="display:flex;flex-direction:row;width:100%;height:100%">' +
          groupDefs.map(function (g, gi) {
            var shares = computeShares(g.items);
            return '<div style="display:flex;flex-direction:column;flex:0 0 ' + s3GroupShares[gi].toFixed(4) + '%;height:100%">' +
              g.idx.map(function (idx, i) {
                var f = files[idx];
                return renderLeaf(f, flexPctStyle(shares[i], "col"), "", idx, selectedSet && selectedSet.has(idx));
              }).join("") +
            '</div>';
          }).join("") +
        '</div>'
      );
    }

    var totalShares = computeShares(files);
    return (
      '<div style="display:flex;flex-direction:column;width:100%;height:100%">' +
        files.map(function (f, i) {
          return renderLeaf(f, flexPctStyle(totalShares[i], "col"), "", i, selectedSet && selectedSet.has(i));
        }).join("") +
      '</div>'
    );
  }

  var states = {};

  function createState(id, layout) {
    return {
      id: id,
      layout: layout,
      files: getInitialFiles(id),
      steps: 0,
      tool: null,
      archiveSelection: [],
      archiveCounter: 1,
      archiveError: null,
      won: false,
      tutorial: {
        hovered: false,
        toolChosen: false,
        fileClicked: false,
        statusChecked: false
      }
    };
  }

  function hasProblematicMetadata(files) {
    if (typeof s4HasProblematicMeta === "function") {
      return files.some(function (f) { return s4HasProblematicMeta(f); });
    }
    return files.some(function (f) { return f.metadata && f.metadata.length > 0; });
  }

  function fileHasProblematicMetadata(file) {
    if (typeof s4HasProblematicMeta === "function") {
      return s4HasProblematicMeta(file);
    }
    return !!(file && file.metadata && file.metadata.length > 0);
  }

  function fitsCard(files) {
    return sumBytes(files) <= CARD_BYTES;
  }

  function isScenarioComplete(state) {
    return state.steps > 0 &&
      fitsCard(state.files) &&
      !hasProblematicMetadata(state.files);
  }

  function setPendingDelta(scenarioId, beforeBytes, afterBytes, clearedProblemMeta) {
    var saved = beforeBytes - afterBytes;
    var msg;
    var kind;
    if (saved > 0) {
      msg = formatLang(t.deltaSaved || "−{size}", { size: formatSize(saved) });
      kind = "saved";
    } else if (clearedProblemMeta) {
      msg = t.deltaMetaOnly || "Problematische Metadaten entfernt";
      kind = "meta";
    } else if (saved === 0) {
      msg = t.deltaUnchanged || "Keine Größenänderung";
      kind = "neutral";
    } else {
      msg = "+" + formatSize(-saved);
      kind = "grow";
    }
    pendingDelta[scenarioId] = { msg: msg, kind: kind };
  }

  function scheduleDeltaFade(scenarioId) {
    if (deltaTimers[scenarioId]) {
      clearTimeout(deltaTimers[scenarioId]);
    }
    deltaTimers[scenarioId] = setTimeout(function () {
      var el = document.querySelector('.fss-panel[data-scenario="' + scenarioId + '"] .fss-delta');
      if (el) {
        el.classList.add("fss-delta--fade");
      }
      pendingDelta[scenarioId] = null;
      deltaTimers[scenarioId] = null;
    }, 2800);
  }

  function tutorialHtml(state) {
    if (!state || state.id !== "s0") { return ""; }
    var steps = t.tutorialSteps || {};
    var tut = state.tutorial || {};
    function item(done, label) {
      return (
        '<li class="fss-tutorial-step' + (done ? " fss-tutorial-step--done" : "") + '">' +
          '<span class="fss-tutorial-check" aria-hidden="true">' + (done ? "✓" : "") + "</span>" +
          escHtml(label || "") +
        "</li>"
      );
    }
    return (
      '<div class="fss-tutorial" role="region" aria-label="' + escHtml(t.tutorialTitle || "Tutorial") + '">' +
        "<strong>" + escHtml(t.tutorialTitle || "Tutorial") + "</strong>" +
        "<p>" + escHtml(t.tutorialIntro || "") + "</p>" +
        "<ol>" +
          item(tut.hovered, steps.hover) +
          item(tut.toolChosen, steps.tool) +
          item(tut.fileClicked, steps.click) +
          item(tut.statusChecked, steps.status) +
        "</ol>" +
      "</div>"
    );
  }

  function escHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function markTutorialStatus(state) {
    if (!state || !state.tutorial) { return; }
    if (state.tutorial.fileClicked || state.steps > 0) {
      state.tutorial.statusChecked = true;
    }
  }

  function updateTutorialChecklist(host, state) {
    if (!host || !state || !state.tutorial || state.id !== "s0") { return; }
    var steps = host.querySelectorAll(".fss-tutorial-step");
    var flags = [
      state.tutorial.hovered,
      state.tutorial.toolChosen,
      state.tutorial.fileClicked,
      state.tutorial.statusChecked
    ];
    steps.forEach(function (li, i) {
      var done = !!flags[i];
      li.classList.toggle("fss-tutorial-step--done", done);
      var check = li.querySelector(".fss-tutorial-check");
      if (check) {
        check.textContent = done ? "✓" : "";
      }
    });
  }

  function toolButtonsHtml(state) {
    var tools = ["convert", "lossless", "lossy", "archive"];
    return tools.map(function (tool) {
      var active = state.tool === tool;
      return (
        '<button type="button" class="fss-tool" data-tool="' + tool + '" aria-pressed="' + (active ? "true" : "false") + '"' +
          ' style="border:2px solid ' + TOOL_COLORS[tool] + ";background:" + (active ? TOOL_COLORS[tool] : "#fff") + ";" +
          "color:" + (active ? "#fff" : TOOL_COLORS[tool]) + ";" +
          "padding:0.45rem 0.7rem;border-radius:4px;cursor:pointer;font-size:12px;font-weight:600;" +
          'font-family:\'Source Sans 3\',\'Gill Sans\',sans-serif">' +
          (t.tools && t.tools[tool] ? t.tools[tool].label : tool) +
        '</button>'
      );
    }).join("");
  }

  function renderPanel(scenarioDef) {
    var state = states[scenarioDef.id];
    var used = sumBytes(state.files);
    var diff = used - CARD_BYTES;
    var overCapacity = diff > 0;
    var hasProblemMeta = hasProblematicMetadata(state.files);
    var statsClass = "fss-stats-row" +
      (overCapacity ? " fss-stats-row--over" : " fss-stats-row--ok") +
      (hasProblemMeta ? " fss-stats-row--meta-warn" : "");

    var capBadge = overCapacity
      ? '<span class="fss-stat-badge fss-stat-badge--over">' + formatLang(t.statsOver, { size: formatGb(diff) }) + "</span>"
      : '<span class="fss-stat-badge fss-stat-badge--ok">' + formatLang(t.statsFree, { size: formatGb(-diff) }) + "</span>";

    var metaBadge = hasProblemMeta
      ? '<span class="fss-stat-badge fss-stat-badge--meta-warn">' + t.metadataLabel + " " + t.metadataYes + "</span>"
      : '<span class="fss-stat-badge fss-stat-badge--meta-ok">' + t.metadataLabel + " " + t.metadataNo + "</span>";

    var selection = new Set(state.archiveSelection);
    var archiveBar = "";
    if (state.tool === "archive") {
      archiveBar =
        '<div class="fss-archive-bar">' +
          '<span>' + formatLang(t.archiveSelected, { count: state.archiveSelection.length }) + "</span>" +
          '<button type="button" class="fss-archive-btn"' + (state.archiveSelection.length < 2 ? " disabled" : "") + ">" +
            t.archiveCreate +
          "</button>" +
        "</div>";
    }

    var resultHtml = "";
    state.won = isScenarioComplete(state);
    var succeeded = state.won;
    if (succeeded) {
      resultHtml =
        '<div class="fss-result fss-result--success" role="status">' +
          formatLang(t.success, { steps: state.steps }) +
        "</div>";
    }

    var archiveErrorHtml = "";
    if (state.archiveError) {
      archiveErrorHtml =
        '<div class="fss-result fss-result--error fss-result--below-treemap" role="alert">' +
          state.archiveError +
        "</div>";
    }

    var delta = pendingDelta[scenarioDef.id];
    var deltaHtml = "";
    if (delta && delta.msg) {
      deltaHtml =
        '<div class="fss-delta fss-delta--' + delta.kind + '" role="status">' +
          escHtml(delta.msg) +
        "</div>";
    }

    return (
      '<div class="fss-panel' + (succeeded ? " fss-panel--success" : "") + '" data-scenario="' + scenarioDef.id + '">' +
        tutorialHtml(state) +
        '<div class="fss-toolbar">' +
          '<div class="fss-tools">' + toolButtonsHtml(state) + "</div>" +
          '<p class="fss-tool-hint">' + (
            state.tool && t.tools && t.tools[state.tool] && t.tools[state.tool].hint
              ? t.tools[state.tool].hint
              : (t.noToolHint || "")
          ) + "</p>" +
        "</div>" +
        archiveBar +
        '<div class="' + statsClass + '">' +
          '<span class="fss-stat-badge">' + t.statsUsed + " <strong>" + formatGb(used) + "</strong> (" +
            countFiles(state.files).toLocaleString("de-DE") + " " + t.statsFiles + ")</span>" +
          capBadge +
          '<span class="fss-stat-badge">' + formatLang(t.steps, { count: state.steps }) + "</span>" +
          metaBadge +
        "</div>" +
        deltaHtml +
        resultHtml +
        '<div class="fss-treemap" id="fss-treemap-' + scenarioDef.id + '">' +
          renderTreemap(state.files, state.layout, selection) +
        "</div>" +
        archiveErrorHtml +
        '<div class="fss-actions">' +
          '<button type="button" class="fss-reset">' + t.reset + "</button>" +
        "</div>" +
      "</div>"
    );
  }

  function findScenarioContainer(def) {
    return document.querySelector('.fss-scenario[data-scenario="' + def.id + '"]');
  }

  function updateSuccessChrome(host, def) {
    var state = states[def.id];
    var succeeded = !!state.won;
    host.classList.toggle("fss-sim--success", succeeded);
    var scenario = findScenarioContainer(def);
    if (scenario) {
      scenario.classList.toggle("fss-scenario--success", succeeded);
    }
  }

  function mountAll() {
    ensureTooltip();
    SCENARIO_DEFS.forEach(function (def) {
      var host = document.getElementById("widget-s4-sim-" + def.id);
      if (!host) { return; }
      if (!states[def.id]) {
        states[def.id] = createState(def.id, def.layout);
      }
      host.classList.remove("widget-placeholder");
      host.innerHTML = renderPanel(def);
      updateSuccessChrome(host, def);
      bindHost(host, def);
    });
  }

  function refreshPanel(def) {
    hideFssTooltip();
    var host = document.getElementById("widget-s4-sim-" + def.id);
    if (!host) { return; }
    host.innerHTML = renderPanel(def);
    updateSuccessChrome(host, def);
    if (pendingDelta[def.id]) {
      scheduleDeltaFade(def.id);
    }
    requestAnimationFrame(function () {
      refreshTooltipUnderPointer(host, def.id);
    });
  }

  function applyArchive(state, indices) {
    var selected = indices.map(function (i) { return state.files[i]; }).filter(Boolean);
    if (selected.length < 2) { return false; }
    if (selected.some(fileHasProblematicMetadata)) {
      state.archiveError = t.archiveMetadataError;
      return false;
    }
    var beforeBytes = sumBytes(state.files);
    var sorted = indices.slice().sort(function (a, b) { return b - a; });
    var archived = {
      name: buildArchiveName(selected),
      bytes: archiveBytes(selected),
      type: "archive",
      count: selected.reduce(function (n, f) { return n + (f.count || 1); }, 0),
      metadata: [],
      applied: cloneApplied()
    };
    sorted.forEach(function (idx) {
      state.files.splice(idx, 1);
    });
    state.files.push(archived);
    state.steps += 1;
    state.archiveSelection = [];
    state.archiveError = null;
    markTutorialStatus(state);
    setPendingDelta(
      state.id,
      beforeBytes,
      sumBytes(state.files),
      false
    );
    return true;
  }

  function bindHost(host, def) {
    if (host.dataset.fssBound === "1") { return; }
    host.dataset.fssBound = "1";

    var activeSeg = null;

    host.addEventListener("click", function (e) {
      var state = states[def.id];
      if (!state) { return; }

      var toolBtn = e.target.closest(".fss-tool");
      if (toolBtn && host.contains(toolBtn)) {
        state.tool = toolBtn.getAttribute("data-tool");
        state.archiveSelection = [];
        state.archiveError = null;
        if (state.tutorial) {
          state.tutorial.toolChosen = true;
        }
        refreshPanel(def);
        return;
      }

      var archiveBtn = e.target.closest(".fss-archive-btn");
      if (archiveBtn && host.contains(archiveBtn)) {
        if (state.archiveSelection.length < 2) { return; }
        hideFssTooltip();
        applyArchive(state, state.archiveSelection.slice());
        refreshPanel(def);
        return;
      }

      var resetBtn = e.target.closest(".fss-reset");
      if (resetBtn && host.contains(resetBtn)) {
        hideFssTooltip();
        pendingDelta[def.id] = null;
        if (deltaTimers[def.id]) {
          clearTimeout(deltaTimers[def.id]);
          deltaTimers[def.id] = null;
        }
        states[def.id] = createState(def.id, def.layout);
        refreshPanel(def);
        return;
      }

      var seg = e.target.closest(".fss-segment");
      if (!seg || !host.contains(seg)) { return; }

      var idx = parseInt(seg.getAttribute("data-index"), 10);
      var file = state.files[idx];
      if (!file) { return; }

      if (!state.tool) { return; }

      if (state.tool === "archive") {
        var pos = state.archiveSelection.indexOf(idx);
        if (pos >= 0) {
          state.archiveSelection.splice(pos, 1);
          state.archiveError = null;
        } else if (fileHasProblematicMetadata(file)) {
          state.archiveError = t.archiveMetadataError;
        } else {
          state.archiveSelection.push(idx);
          state.archiveError = null;
        }
        if (state.tutorial) {
          state.tutorial.fileClicked = true;
          markTutorialStatus(state);
        }
        refreshPanel(def);
        return;
      }

      hideFssTooltip();
      var beforeBytes = sumBytes(state.files);
      var fileHadProblem = fileHasProblematicMetadata(file);
      state.steps += 1;
      var updated = null;
      if (state.tool === "convert") {
        if (canApplyTool(file, "convert")) {
          updated = applySingleOp(file, state.tool);
        }
      } else if (canApplyTool(file, state.tool)) {
        updated = applySingleOp(file, state.tool);
      }
      if (updated) {
        state.files[idx] = updated;
        var clearedProblem = fileHadProblem && !fileHasProblematicMetadata(updated);
        setPendingDelta(
          state.id,
          beforeBytes,
          sumBytes(state.files),
          clearedProblem
        );
      } else {
        pendingDelta[state.id] = null;
      }
      if (state.tutorial) {
        state.tutorial.fileClicked = true;
        markTutorialStatus(state);
      }
      refreshPanel(def);
    });

    host.addEventListener("mouseover", function (e) {
      var seg = e.target.closest(".fss-segment");
      if (!seg || !host.contains(seg)) {
        if (activeSeg) {
          activeSeg.style.filter = "";
          activeSeg = null;
          hideFssTooltip();
        }
        return;
      }
      if (seg === activeSeg) {
        positionFssTooltip(e);
        return;
      }
      if (activeSeg) {
        activeSeg.style.filter = "";
      }
      activeSeg = seg;
      var file = fileForSegment(seg, def.id);
      if (!file) { return; }
      seg.style.filter = "brightness(1.12)";
      showFssTooltip(e, file);
      var state = states[def.id];
      if (state && state.tutorial && !state.tutorial.hovered) {
        state.tutorial.hovered = true;
        updateTutorialChecklist(host, state);
      }
    });

    host.addEventListener("mouseleave", function (e) {
      if (!e.relatedTarget || !host.contains(e.relatedTarget)) {
        if (activeSeg) {
          activeSeg.style.filter = "";
          activeSeg = null;
        }
        hideFssTooltip();
      }
    });

    host.addEventListener("mousemove", function (e) {
      var seg = e.target.closest(".fss-segment");
      if (!seg || !host.contains(seg)) {
        if (activeSeg) {
          activeSeg.style.filter = "";
          activeSeg = null;
          hideFssTooltip();
        }
        return;
      }
      if (seg !== activeSeg) {
        return;
      }
      positionFssTooltip(e);
    });

    host.addEventListener("focusin", function (e) {
      var seg = e.target.closest(".fss-segment");
      if (!seg || !host.contains(seg)) { return; }
      var file = fileForSegment(seg, def.id);
      if (!file) { return; }
      showFssTooltip({ clientX: pointer.x, clientY: pointer.y }, file);
      var state = states[def.id];
      if (state && state.tutorial && !state.tutorial.hovered) {
        state.tutorial.hovered = true;
        updateTutorialChecklist(host, state);
      }
    });

    host.addEventListener("focusout", function (e) {
      var seg = e.target.closest(".fss-segment");
      if (!seg || !host.contains(seg)) { return; }
      hideFssTooltip();
    });
  }

  SCENARIO_DEFS.forEach(function (def) {
    states[def.id] = createState(def.id, def.layout);
  });
  mountAll();
}
