function setupFilesystemWidget(source) {
  const t = source || getWidgetLang("filesystem");
  var container = document.getElementById("widget-s4-filesystem");
  if (!container) { return; }

  var CARD_BYTES = 16 * 1024 * 1024 * 1024;
  var GB = 1024 * 1024 * 1024;
  var MB = 1024 * 1024;

  var FILE_COLORS = {
    video: "#c0392b",
    text: "#2980b9",
    spreadsheet: "#27ae60",
    image: "#8e44ad",
    encrypted: "#2c3e50"
  };

  var TYPE_LABELS = t.typeLabels || {};

  var SCENARIOS = (window.S4_SCENARIOS || []).filter(function (def) {
    return !def.tutorial;
  }).map(function (def) {
    return {
      id: def.id,
      label: (t.scenarios && t.scenarios[def.id]) || def.id,
      layout: def.layout,
      files: def.files
    };
  });

  function formatSize(bytes) {
    if (bytes >= 1048576) {
      return (bytes / 1048576).toLocaleString("de-DE", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
      }) + " MB";
    }
    if (bytes >= 1024) {
      return (bytes / 1024).toLocaleString("de-DE", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
      }) + " KB";
    }
    return bytes.toLocaleString("de-DE") + " Bytes";
  }

  function formatGb(bytes) {
    return (bytes / (1024 * 1024 * 1024)).toLocaleString("de-DE", {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1
    }) + " GB";
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

  function filterTypes(files, types) {
    return files.filter(function (f) { return types.indexOf(f.type) >= 0; });
  }

  function filterType(files, type) {
    return files.filter(function (f) { return f.type === type; });
  }

  function segmentStyle(color) {
    return (
      "background:" + color + ";" +
      "border:1px solid rgba(0,0,0,0.12);" +
      "box-sizing:border-box;cursor:pointer;" +
      "min-width:12px;min-height:12px;overflow:hidden;" +
      "transition:filter 120ms ease"
    );
  }

  function flexPctStyle(share, direction) {
    var pct = share.toFixed(4) + "%";
    if (direction === "col") {
      return "flex:0 0 " + pct + ";height:" + pct + ";width:100%;min-height:12px;";
    }
    return "flex:0 0 " + pct + ";width:" + pct + ";height:100%;min-width:12px;";
  }

  function renderLeaf(file, flexStyle, extraStyle) {
    var countAttr = file.count ? ' data-count="' + file.count + '"' : "";
    var metaAttr = "";
    if (file.metadata && file.metadata.length) {
      metaAttr = ' data-metadata="' + encodeURIComponent(JSON.stringify(file.metadata)) + '"';
    }
    return (
      '<div class="fs-segment" tabindex="0"' +
        ' data-name="' + file.name + '"' +
        ' data-type="' + file.type + '"' +
        ' data-bytes="' + file.bytes + '"' +
        countAttr + metaAttr +
        ' style="' + segmentStyle(FILE_COLORS[file.type]) + flexStyle + (extraStyle || "") + '"' +
        ' aria-label="' + file.name + ", " + TYPE_LABELS[file.type] + ", " + formatSize(file.bytes) + '"' +
      '></div>'
    );
  }

  function renderFlexGroup(files, direction, outerFlex) {
    if (!files.length) { return ""; }
    var flexDir = direction === "col" ? "column" : "row";
    var computeShares = window.s4ComputeDisplayShares || function (list) {
      var total = sumBytes(list);
      return list.map(function (f) { return pct(f.bytes, total); });
    };
    var shares = computeShares(files);
    var inner = files.map(function (f, i) {
      return renderLeaf(f, flexPctStyle(shares[i], direction));
    }).join("");
    return (
      '<div style="display:flex;flex-direction:' + flexDir + ";" + outerFlex + '">' +
        inner +
      '</div>'
    );
  }

  function renderScenario1(files) {
    var videos = filterType(files, "video");
    var texts = filterType(files, "text");
    var sheets = filterType(files, "spreadsheet");
    var computeGroupShares = window.s4ComputeGroupShares || function (groups) {
      var totals = groups.map(sumBytes);
      var total = totals.reduce(function (s, b) { return s + b; }, 0);
      return totals.map(function (b) { return pct(b, total); });
    };
    var textGroup = texts.concat(sheets);
    var groupShares = computeGroupShares([videos, textGroup]);

    return (
      '<div style="display:flex;flex-direction:row;width:100%;height:100%">' +
        renderFlexGroup(videos, "col", "flex:0 0 " + groupShares[0].toFixed(4) + "%;height:100%") +
        renderFlexGroup(textGroup, "col", "flex:0 0 " + groupShares[1].toFixed(4) + "%;height:100%") +
      '</div>'
    );
  }

  function renderScenario2(files) {
    var texts = filterType(files, "text");
    var others = files.filter(function (f) { return f.type !== "text"; });
    var computeGroupShares = window.s4ComputeGroupShares || function (groups) {
      var totals = groups.map(sumBytes);
      var total = totals.reduce(function (s, b) { return s + b; }, 0);
      return totals.map(function (b) { return pct(b, total); });
    };
    var groupShares = computeGroupShares([texts, others]);
    var cols = 7;
    var rows = Math.ceil(texts.length / cols);
    var gridCells = texts.map(function (f) {
      return renderLeaf(f, "", "width:100%;height:100%;min-width:12px;min-height:12px");
    }).join("");

    return (
      '<div style="display:flex;flex-direction:row;width:100%;height:100%">' +
        '<div style="flex:0 0 ' + groupShares[0].toFixed(4) + '%;height:100%;display:grid;' +
          "grid-template-columns:repeat(" + cols + ",1fr);" +
          "grid-template-rows:repeat(" + rows + ",1fr);" +
          'gap:1px;padding:1px;background:rgba(0,0,0,0.08)">' +
          gridCells +
        '</div>' +
        renderFlexGroup(others, "col", "flex:0 0 " + groupShares[1].toFixed(4) + "%;height:100%") +
      '</div>'
    );
  }

  function renderScenario3(files) {
    var docs = filterTypes(files, ["text", "spreadsheet"]);
    var media = filterTypes(files, ["image", "video"]);
    var encrypted = filterType(files, "encrypted");
    var groupDefs = [
      { items: docs },
      { items: media },
      { items: encrypted }
    ].filter(function (g) { return g.items.length; });
    var computeGroupShares = window.s4ComputeGroupShares || function (groups) {
      var totals = groups.map(sumBytes);
      var total = totals.reduce(function (s, b) { return s + b; }, 0);
      return totals.map(function (b) { return pct(b, total); });
    };
    var groupShares = computeGroupShares(groupDefs.map(function (g) { return g.items; }));

    return (
      '<div style="display:flex;flex-direction:row;width:100%;height:100%">' +
        groupDefs.map(function (g, i) {
          return renderFlexGroup(g.items, "col", "flex:0 0 " + groupShares[i].toFixed(4) + "%;height:100%");
        }).join("") +
      '</div>'
    );
  }

  function renderTreemap(scenario) {
    if (scenario.layout === "s1") { return renderScenario1(scenario.files); }
    if (scenario.layout === "s2") { return renderScenario2(scenario.files); }
    return renderScenario3(scenario.files);
  }

  function legendHtml() {
    var types = ["video", "text", "image", "encrypted"];
    return types.map(function (t) {
      return (
        '<div style="display:flex;align-items:center;gap:6px;font-size:12px;font-family:sans-serif">' +
          '<span style="width:14px;height:14px;border-radius:2px;background:' + FILE_COLORS[t] + ';border:1px solid rgba(0,0,0,0.12);flex-shrink:0"></span>' +
          '<span>' + TYPE_LABELS[t] + '</span>' +
        '</div>'
      );
    }).join("");
  }

  function fileTypesOverviewHtml() {
    var overview = t.fileTypesOverview;
    if (!overview || !overview.items || !overview.items.length) { return ""; }
    var rows = overview.items.map(function (item) {
      return (
        '<li style="margin:0 0 0.55rem 0">' +
          '<span style="font-family:monospace;font-weight:600">' + item.ext + '</span>' +
          ' <span style="font-weight:600">(' + item.label + '):</span> ' +
          item.text +
        '</li>'
      );
    }).join("");
    return (
      '<details class="article" style="margin-top:12px;margin-bottom:0">' +
        '<summary>' + overview.title + '</summary>' +
        '<div class="article-body">' +
          '<ul style="margin:0;padding-left:1.2rem;font-size:13px;line-height:1.55">' + rows + '</ul>' +
        '</div>' +
      '</details>'
    );
  }

  container.classList.remove("widget-placeholder");
  container.style.cssText = "border:1px solid var(--border);background:#fffdf8;padding:0.8rem 1rem 1rem;border-radius:3px;margin:0.75rem 0";

  var tabHtml = SCENARIOS.map(function (s, i) {
    return (
      '<button type="button" class="fs-tab" data-scenario="' + s.id + '"' +
        ' aria-selected="' + (i === 0 ? "true" : "false") + '"' +
        ' style="' +
          "border:1px solid var(--border);background:" + (i === 0 ? "var(--accent)" : "#fff") + ";" +
          "color:" + (i === 0 ? "#fff" : "var(--ink)") + ";" +
          "padding:0.4rem 0.75rem;border-radius:3px;cursor:pointer;" +
          "font-family:'Source Sans 3','Gill Sans',sans-serif;font-size:13px;font-weight:600;" +
          "flex:1 1 0;min-width:0" +
        '">' + s.label + '</button>'
    );
  }).join("");

  container.innerHTML =
    '<div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px">' +
      tabHtml +
    '</div>' +
    '<div style="display:flex;justify-content:space-between;align-items:baseline;gap:12px;margin-bottom:8px;flex-wrap:wrap">' +
      '<div style="font-size:12px;color:var(--muted);font-family:sans-serif">' +
        t.memoryCard + ' <strong style="color:var(--ink)">' + formatGb(CARD_BYTES) + '</strong>' +
      '</div>' +
      '<div id="fs-stats" style="font-size:12px;color:var(--muted);font-family:sans-serif;text-align:right"></div>' +
    '</div>' +
    '<div id="fs-treemap" style="position:relative;width:100%;height:min(360px,50vh);border:1px solid var(--border);border-radius:4px;overflow:hidden;background:#fff"></div>' +
    '<div style="display:flex;flex-wrap:wrap;gap:14px 20px;margin-top:12px;padding-top:10px;border-top:1px solid var(--border)">' +
      legendHtml() +
    '</div>' +
    fileTypesOverviewHtml() +
    '<div id="fs-tooltip" role="tooltip" hidden style="' +
      "position:fixed;z-index:9999;pointer-events:none;" +
      "background:#122;color:#fff;padding:8px 10px;border-radius:4px;" +
      "font-size:12px;line-height:1.5;font-family:sans-serif;" +
      "box-shadow:0 4px 12px rgba(0,0,0,0.2);max-width:min(320px,90vw)" +
    '"></div>';

  var treemapEl = document.getElementById("fs-treemap");
  var statsEl = document.getElementById("fs-stats");
  var tooltip = document.getElementById("fs-tooltip");
  var activeId = SCENARIOS[0].id;

  function getScenario(id) {
    for (var i = 0; i < SCENARIOS.length; i++) {
      if (SCENARIOS[i].id === id) { return SCENARIOS[i]; }
    }
    return SCENARIOS[0];
  }

  function updateStats(scenario) {
    var used = sumBytes(scenario.files);
    var totalFiles = countFiles(scenario.files);
    var diff = used - CARD_BYTES;
    var diffHtml;
    if (diff > 0) {
      diffHtml = '<span style="color:#c0392b">' + formatLang(t.statsOver, { size: formatGb(diff) }) + '</span>';
    } else if (diff < 0) {
      diffHtml = '<span style="color:#3f8f4b">' + formatLang(t.statsFree, { size: formatGb(-diff) }) + '</span>';
    } else {
      diffHtml = '<span style="color:var(--ink)">' + t.statsFull + '</span>';
    }
    statsEl.innerHTML =
      t.statsUsed + ' <strong style="color:var(--ink)">' + formatGb(used) + '</strong> (' +
      totalFiles.toLocaleString("de-DE") + ' ' + t.statsFiles + ') \u00b7 ' + diffHtml;
  }

  function setActiveTab(id) {
    activeId = id;
    container.querySelectorAll(".fs-tab").forEach(function (btn) {
      var on = btn.getAttribute("data-scenario") === id;
      btn.setAttribute("aria-selected", on ? "true" : "false");
      btn.style.background = on ? "var(--accent)" : "#fff";
      btn.style.color = on ? "#fff" : "var(--ink)";
    });
    var scenario = getScenario(id);
    updateStats(scenario);
    treemapEl.innerHTML = renderTreemap(scenario);
    bindSegments();
  }

  function positionTooltip(e) {
    tooltip.hidden = false;
    var offset = 14;
    var x = e.clientX + offset;
    var y = e.clientY + offset;
    tooltip.style.left = x + "px";
    tooltip.style.top = y + "px";

    var rect = tooltip.getBoundingClientRect();
    if (rect.right > window.innerWidth - 8) {
      x = e.clientX - rect.width - offset;
      tooltip.style.left = Math.max(8, x) + "px";
    }
    if (rect.bottom > window.innerHeight - 8) {
      y = e.clientY - rect.height - offset;
      tooltip.style.top = Math.max(8, y) + "px";
    }
    rect = tooltip.getBoundingClientRect();
    if (rect.left < 8) {
      tooltip.style.left = "8px";
    }
    if (rect.top < 8) {
      tooltip.style.top = "8px";
    }
  }

  function showTooltip(e, seg) {
    var name = seg.getAttribute("data-name");
    var type = seg.getAttribute("data-type");
    var bytes = parseInt(seg.getAttribute("data-bytes"), 10);
    var count = seg.getAttribute("data-count");
    var metadata = [];
    var rawMeta = seg.getAttribute("data-metadata");
    if (rawMeta) {
      try { metadata = JSON.parse(decodeURIComponent(rawMeta)); } catch (err) { metadata = []; }
    }
    var html =
      "<strong style=\"display:block;margin-bottom:2px;font-family:monospace;font-size:11px\">" + name + "</strong>" +
      TYPE_LABELS[type];
    if (count) {
      html += "<br>" + formatLang(t.tooltipFiles, { count: parseInt(count, 10).toLocaleString("de-DE") });
    }
    html += "<br>" + formatSize(bytes);
    var metaHtml = typeof getS4MetadataTooltip === "function"
      ? getS4MetadataTooltip({ name: name, type: type, metadata: metadata }, t)
      : "";
    if (metaHtml) {
      html += "<br><span style=\"opacity:0.92\">" + metaHtml + "</span>";
    }
    tooltip.innerHTML = html;
    seg.style.filter = "brightness(1.12)";
    positionTooltip(e);
  }

  function hideTooltip(seg) {
    tooltip.hidden = true;
    seg.style.filter = "";
  }

  function bindSegments() {
    container.querySelectorAll(".fs-segment").forEach(function (seg) {
      seg.addEventListener("mouseenter", function (e) { showTooltip(e, seg); });
      seg.addEventListener("mousemove", function (e) { positionTooltip(e); });
      seg.addEventListener("mouseleave", function () { hideTooltip(seg); });
      seg.addEventListener("focus", function () {
        showTooltip({ clientX: window.innerWidth / 2, clientY: 120 }, seg);
      });
      seg.addEventListener("blur", function () { hideTooltip(seg); });
    });
  }

  container.querySelectorAll(".fs-tab").forEach(function (btn) {
    btn.addEventListener("click", function () {
      setActiveTab(btn.getAttribute("data-scenario"));
    });
  });

  setActiveTab(activeId);
}
