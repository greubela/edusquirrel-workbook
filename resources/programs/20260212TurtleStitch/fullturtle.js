// fullturtle.js (hardened)
// Guarantees: window.TurtleStitchPoC exists immediately.
// Adds: window.TurtleStitchPoCReady (Promise<void>) and window.TurtleStitchPoCError (Error|null)

(() => {
  "use strict";

  const LOG_PREFIX = "[TurtleStitchPoC]";
  const log = (...a) => console.log(LOG_PREFIX, ...a);
  const warn = (...a) => console.warn(LOG_PREFIX, ...a);
  const err = (...a) => console.error(LOG_PREFIX, ...a);

  // Public readiness / error signals
  window.TurtleStitchPoCError = null;
  let resolveReady, rejectReady;
  window.TurtleStitchPoCReady = new Promise((res, rej) => { resolveReady = res; rejectReady = rej; });

  // Internal ready flag
  let isReady = false;

  // Stub API exposed immediately (never undefined)
  // Calls will wait for TurtleStitchPoCReady and then dispatch to real implementations.
  const api = {
    calcProgramPng: async (xml_content, language) => {
      await window.TurtleStitchPoCReady;
      return api._impl.calcProgramPng(xml_content, language);
    },
    calcProgramSvg: async (xml_content, language) => {
      await window.TurtleStitchPoCReady;
      return api._impl.calcProgramSvg(xml_content, language);
    },
    simulateGreenFlag: async (xml_content) => {
      await window.TurtleStitchPoCReady;
      return api._impl.simulateGreenFlag(xml_content);
    },
    downloadDst: async (xml_content) => {
      await window.TurtleStitchPoCReady;
      return api._impl.downloadDst(xml_content);
    },
    _impl: null
  };

  window.TurtleStitchPoC = api;

  window.base_prog_dir = "../resources/programs/20260212TurtleStitch/";

  // ---- Script loader ----
  const SNAP_SCRIPT_ORDER = [
    "turtlestitchsrc/morphic.js",
    "turtlestitchsrc/symbols.js",
    "turtlestitchsrc/widgets.js",
    "turtlestitchsrc/blocks.js",
    "turtlestitchsrc/threads.js",
    //"turtlestitchsrc/objects.js",
    "adjusted/adjustedObjects.js", // adjusted
    "turtlestitchsrc/scenes.js",
    "turtlestitchsrc/gui.js",
    "turtlestitchsrc/paint.js",
    "turtlestitchsrc/lists.js",
    "turtlestitchsrc/byob.js",
    "turtlestitchsrc/tables.js",
    "turtlestitchsrc/sketch.js",
    "turtlestitchsrc/video.js",
    "turtlestitchsrc/maps.js",
    "turtlestitchsrc/extensions.js",
    "turtlestitchsrc/xml.js",
    "turtlestitchsrc/store.js",
    "turtlestitchsrc/locale.js",
    "turtlestitchsrc/cloud.js",
    "turtlestitchsrc/api.js",
    "turtlestitchsrc/embroider.js",

  ];


  let scriptsLoaded = false;
  let booted = false;
  let bootPromise = null;

  let world = null;
  let ide = null;
  function injectScript(src) {

    //log("injectScript", base_prog_dir + src);
    return new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = base_prog_dir + src;
      s.async = false;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error("Failed to load " + src));
      document.head.appendChild(s);
    });
  }

  async function ensureScriptsLoaded() {
    if (scriptsLoaded) return;
    //log("Loading TurtleStitch scripts…");
    for (const p of SNAP_SCRIPT_ORDER) await injectScript(p);
    scriptsLoaded = true;
    //log("Scripts loaded.");
  }

  function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
  function stepWorld(n = 2) { for (let i = 0; i < n; i++) { try { world.doOneCycle(); } catch (_) {} } }

  function forceLayout() {
    if (!world || !ide) return;
    try {
      const c = world.worldCanvas;
      const w = new Point(c.width, c.height);
      world.setExtent(w);
      if (ide.setExtent) ide.setExtent(w);
      if (ide.fixLayout) ide.fixLayout();
    } catch (e) { warn("forceLayout exception:", e); }
    stepWorld(3);
  }

  function setLanguageAsync(lang) {
    return new Promise((resolve) => {
      if (!ide || typeof ide.setLanguage !== "function") return resolve(false);
      ide.setLanguage(lang, () => resolve(true), true);
    });
  }

  function normalizeSnapLanguage(lang) {
    if (!lang || typeof lang !== "string") return "en";
    if (window.SnapTranslator?.dict && (lang in window.SnapTranslator.dict)) return lang;
    if (lang.includes("_")) {
      const base = lang.split("_")[0];
      if (window.SnapTranslator?.dict && (base in window.SnapTranslator.dict)) return base;
    }
    return "en";
  }

  function loadLanguageScriptAsync(lang) {
    return new Promise((resolve) => {
      const translation = document.getElementById("language");
      if (translation?.parentNode) translation.parentNode.removeChild(translation);
      if (lang === "en") return resolve();

      const script = document.createElement("script");
      script.id = "language";
      script.onload = () => resolve();
      script.onerror = () => resolve();
      //console.log("script: " + base_prog_dir + "adjusted/lang-" + lang + ".js");
      script.src = base_prog_dir + "adjusted/lang-" + lang + ".js";
      document.head.appendChild(script);
    });
  }

  // TurtleStitch/Snap language switching normally serializes + reloads the current project.
  // That reload path is locale-sensitive and can drop blocks for some locales (e.g. tr).
  // Here we only switch UI language state, never re-opening the loaded XML project.
  async function setLanguageWithoutProjectReloadAsync(lang) {
    if (!ide) return false;
    const safeLang = normalizeSnapLanguage(lang);

    // keep Snap's language dictionary state in sync with what setLanguage() expects,
    // but avoid reflectLanguage() because it reloads the whole project.
    try { window.SnapTranslator?.unload?.(); } catch (_) {}
    await loadLanguageScriptAsync(safeLang);
    if (window.SnapTranslator) {
      window.SnapTranslator.language = safeLang;
    }

    try { ide.flushBlocksCache?.(); } catch (_) {}
    try { window.SpriteMorph?.prototype?.initBlocks?.(); } catch (_) {}
    try { ide.spriteBar?.tabBar?.tabTo?.("scripts"); } catch (_) {}
    try { ide.createCategories?.(); } catch (_) {}
    try { ide.categories?.refreshEmpty?.(); } catch (_) {}
    try { ide.createCorralBar?.(); } catch (_) {}
    try { ide.refreshCustomizedPalette?.(); } catch (_) {}
    try { ide.fixLayout?.(); } catch (_) {}
    forceLayout();
    stepWorld(4);
    return true;
  }

  async function boot() {
    await ensureScriptsLoaded();
    if (booted) return;
    if (bootPromise) return bootPromise;

    bootPromise = (async () => {
      if (!window.WorldMorph || !window.IDE_Morph) throw new Error("WorldMorph/IDE_Morph missing after load.");

      const wrap = document.createElement("div");
      wrap.style.position = "fixed";
      wrap.style.left = "-20000px";
      wrap.style.top = "0";

      const canvas = document.createElement("canvas");
      canvas.width = 1400;
      canvas.height = 1000;
      canvas.tabIndex = 1;
      wrap.appendChild(canvas);
      document.body.appendChild(wrap);

      world = new WorldMorph(canvas);
      world.worldCanvas = canvas;

      ide = new IDE_Morph({ noAutoFill: true, noCloud: true });
      ide.openIn(world);

      window.world = world;
      window.ide = ide;

      forceLayout();

      (function loop() {
        try { world.doOneCycle(); } catch (_) {}
        requestAnimationFrame(loop);
      })();

      booted = true;
      log("[INFO] TurtleStitch IDE successfully booted.");
    })();

    return bootPromise;
  }

  async function loadProjectXmlCanonical(xml) {
    await boot();
    await setLanguageWithoutProjectReloadAsync("en");
    ide.loadProjectXML(xml);
    await sleep(350);
    try { ide.selectSprite?.(ide.currentSprite); } catch (_) {}
    forceLayout();
    stepWorld(3);
  }

  function ctorName(o) { try { return o?.constructor?.name || typeof o; } catch (_) { return "?"; } }
  function isBlockish(m) {
    try {
      if (!m) return false;
      if (m.selector || m.blockSpec || m.isBlock) return true;
      return ctorName(m).includes("Block");
    } catch (_) { return false; }
  }
  function scoreMorph(m) {
    try {
      if (!m || !m.children) return 0;
      let blocks = 0;
      for (const ch of m.children) if (isBlockish(ch)) blocks++;
      return blocks * 1000 + m.children.length;
    } catch (_) { return 0; }
  }
  function walkMorphTree(root, maxNodes = 8000) {
    const out = [];
    const stack = [root];
    const seen = new Set();
    let n = 0;

    while (stack.length && n < maxNodes) {
      const m = stack.pop();
      if (!m || typeof m !== "object" || seen.has(m)) continue;
      seen.add(m); n++;

      const kids = (m.children && Array.isArray(m.children)) ? m.children.length : 0;
      if (typeof m.fullImage === "function" && kids > 0) {
        out.push({ m, ctor: ctorName(m), kids, score: scoreMorph(m) });
      }
      try {
        if (m.children && Array.isArray(m.children)) {
          for (let i = m.children.length - 1; i >= 0; i--) stack.push(m.children[i]);
        }
      } catch (_) {}
    }
    out.sort((a,b) => b.score - a.score);
    return out;
  }

  function findBestProgramMorph() {
    const roots = [];
    if (ide?.spriteEditor) roots.push(ide.spriteEditor);
    if (ide?.spriteEditor?.contents) roots.push(ide.spriteEditor.contents);
    if (ide?.currentSprite?.scripts) roots.push(ide.currentSprite.scripts);

    const all = [];
    for (const r of roots) all.push(...walkMorphTree(r));
    return all.length ? all[0].m : null;
  }

  function snapshotMorphToPngDataUrl(morph) {
    forceLayout();
    try { morph.fixLayout?.(); } catch (_) {}
    try { morph.changed?.(); } catch (_) {}
    stepWorld(2);
    return morph.fullImage().toDataURL("image/png");
  }

  function svgDataUrlFromString(svgMarkup) {
    const encoded = btoa(unescape(encodeURIComponent(svgMarkup)));
    return `data:image/svg+xml;base64,${encoded}`;
  }

  function allProgramPictures() {
    const pics = [];

    if (!ide) return pics;

    ide.sprites?.asArray?.().forEach((sprite) => {
      if (sprite?.scripts?.scriptsPicture) {
        const pic = sprite.scripts.scriptsPicture();
        if (pic) pics.push(pic);
      }
      sprite?.customBlocks?.forEach((def) => {
        const pic = def?.scriptsPicture?.();
        if (pic) pics.push(pic);
      });
    });

    if (ide.stage?.scripts?.scriptsPicture) {
      const stagePic = ide.stage.scripts.scriptsPicture();
      if (stagePic) pics.push(stagePic);
    }
    ide.stage?.customBlocks?.forEach((def) => {
      const pic = def?.scriptsPicture?.();
      if (pic) pics.push(pic);
    });
    ide.stage?.globalBlocks?.forEach((def) => {
      const pic = def?.scriptsPicture?.();
      if (pic) pics.push(pic);
    });

    return pics;
  }

  function snapshotAllProgramsSvgDataUrl() {
    forceLayout();
    stepWorld(2);

    const padding = 20;
    const pics = allProgramPictures();
    if (!pics.length) throw new Error("No scripts picture could be generated.");

    let width = 0;
    let height = 0;
    pics.forEach((p, i) => {
      width = Math.max(width, p.width);
      height += p.height;
      if (i < pics.length - 1) height += padding;
    });

    let y = 0;
    const images = pics.map((canvas) => {
      const href = canvas.toDataURL("image/png");
      const node = `<image x="0" y="${y}" width="${canvas.width}" height="${canvas.height}" href="${href}" />`;
      y += canvas.height + padding;
      return node;
    }).join("\n");

    const svg = [
      `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">`,
      images,
      "</svg>"
    ].join("\n");

    return svgDataUrlFromString(svg);
  }

  function snapshotStagePngDataUrl() {
    forceLayout();
    stepWorld(2);

    // Prefer the stage-only morph image when available.
    try {
      const stageImage = ide?.stage?.fullImage?.();
      if (stageImage && typeof stageImage.toDataURL === "function") {
        return stageImage.toDataURL("image/png");
      }
    } catch (_) {}

    // Fallback: use the world canvas if stage image cannot be produced.
    if (world?.worldCanvas && typeof world.worldCanvas.toDataURL === "function") {
      return world.worldCanvas.toDataURL("image/png");
    }

    throw new Error("Could not generate stage PNG snapshot.");
  }

  async function runGreenFlagOnce() {
    forceLayout();
    try { ide.stage.clearPenTrails?.(); } catch (_) {}
    ide.runScripts();
    await sleep(700);
    try { ide.stop?.(); } catch (_) {}
    stepWorld(3);
  }

  // Real implementations (wired into api._impl once boot finishes)
  const impl = {

    calcProgramSvg: async (xml_content, language) => {
      if (typeof xml_content !== "string") throw new Error("xml_content must be a string");
      await loadProjectXmlCanonical(xml_content);
      if (language && language !== "en") {
        await setLanguageAsync(language);
        forceLayout(); stepWorld(2);
      }
      return snapshotAllProgramsSvgDataUrl();
    },

    simulateGreenFlag: async (xml_content) => {
      if (typeof xml_content !== "string") throw new Error("xml_content must be a string");
      await loadProjectXmlCanonical(xml_content);
      await runGreenFlagOnce();
      return snapshotStagePngDataUrl();
    },

    downloadDst: async (xml_content) => {
      if (typeof xml_content !== "string") throw new Error("xml_content must be a string");
      await loadProjectXmlCanonical(xml_content);
      await runGreenFlagOnce();

      if (typeof window.exportEmbroidery !== "function") {
        throw new Error("exportEmbroidery not found (src/embroider.js missing?)");
      }
      const stage = ide?.stage;
      const trailsLog = stage?.trailsLog || stage?.turtle?.trailsLog || null;
      if (!trailsLog) throw new Error("No trailsLog after running; cannot export DST.");
      window.exportEmbroidery(trailsLog, "turtlestitch-export", "dst");
    }
  };

  // Boot and mark ready
  (async () => {
    try {
      await boot();
      api._impl = impl;
      isReady = true;
      resolveReady();
      log("[INFO] API ready.");
    } catch (e) {
      window.TurtleStitchPoCError = e;
      rejectReady(e);
      err("[ERROR] API boot failed:", e);
    }
  })();
})();
