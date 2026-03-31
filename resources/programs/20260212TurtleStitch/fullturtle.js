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
  window.TurtleStitchPoCReady = new Promise((res, rej) => {
    resolveReady = res;
    rejectReady = rej;
  });

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
    createEditor: async (options = {}) => {
      await window.TurtleStitchPoCReady;
      return api._impl.createEditor(options);
    },
    destroyHiddenPreview: async () => {
      await window.TurtleStitchPoCReady;
      return api._impl.destroyHiddenPreview();
    },
    _impl: null
  };

  window.TurtleStitchPoC = api;

  const BASE_PROG_DIR = "../resources/programs/20260212TurtleStitch/";
  // keep for backward compatibility with scripts that read this global directly
  window.base_prog_dir = BASE_PROG_DIR;

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
    "turtlestitchsrc/embroider.js"
  ];

  let scriptsLoaded = false;
  let singletonPreview = null;
  let singletonBootPromise = null;

  function injectScript(src) {
    return new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = BASE_PROG_DIR + src;
      s.async = false;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error("Failed to load " + src));
      document.head.appendChild(s);
    });
  }

  async function ensureScriptsLoaded() {
    if (scriptsLoaded) return;
    for (const p of SNAP_SCRIPT_ORDER) await injectScript(p);
    scriptsLoaded = true;
  }

  let nextEditorInstanceId = 1;

  class TurtleStitchEditorInstance {
    constructor(options = {}) {
      this.options = options;
      this.world = null;
      this.ide = null;
      this.wrap = null;
      this.canvas = null;
      this._rafId = null;
      this._destroyed = false;
      this._loopBound = this._loop.bind(this);
      this._languageScriptElement = null;
      this._instanceId = nextEditorInstanceId++;
      this._onProjectChange = null;
      this._projectXmlDebounce = null;
      this._projectXmlPushInFlight = false;
    }

    static sleep(ms) {
      return new Promise(r => setTimeout(r, ms));
    }

    async boot() {
      if (this._destroyed) throw new Error("Editor was destroyed");
      await ensureScriptsLoaded();
      if (!window.WorldMorph || !window.IDE_Morph) throw new Error("WorldMorph/IDE_Morph missing after load.");
      if (this.world && this.ide) return;

      const hidden = this.options.hidden !== false;
      const wrap = document.createElement("div");
      wrap.style.position = hidden ? "fixed" : "relative";
      wrap.style.left = hidden ? "-20000px" : "0";
      wrap.style.top = "0";
      wrap.style.width = `${this.options.width || 1400}px`;
      wrap.style.height = `${this.options.height || 1000}px`;

      const canvas = document.createElement("canvas");
      canvas.width = this.options.width || 1400;
      canvas.height = this.options.height || 1000;
      canvas.tabIndex = 1;
      wrap.appendChild(canvas);

      const parentNode = this.options.parentNode || document.body;
      parentNode.appendChild(wrap);

      const world = new WorldMorph(canvas);
      world.worldCanvas = canvas;

      const ide = new IDE_Morph({ noAutoFill: true, noCloud: true });
      ide.openIn(world);
      this._instrumentProjectChanges(ide);

      this.wrap = wrap;
      this.canvas = canvas;
      this.world = world;
      this.ide = ide;

      this.forceLayout();
      this._scheduleLoop();
    }

    _instrumentProjectChanges(ide) {
      const originalRecordUnsavedChanges = typeof ide.recordUnsavedChanges === "function"
        ? ide.recordUnsavedChanges.bind(ide)
        : null;

      if (originalRecordUnsavedChanges) {
        ide.recordUnsavedChanges = (...args) => {
          const result = originalRecordUnsavedChanges(...args);
          this._scheduleProjectXmlPush();
          return result;
        };
      }

      const originalOpenProjectString = typeof ide.openProjectString === "function"
        ? ide.openProjectString.bind(ide)
        : null;

      if (originalOpenProjectString) {
        ide.openProjectString = (...args) => {
          const result = originalOpenProjectString(...args);
          this._scheduleProjectXmlPush();
          return result;
        };
      }
    }

    _scheduleProjectXmlPush() {
      if (!this._onProjectChange) return;
      if (this._projectXmlDebounce) clearTimeout(this._projectXmlDebounce);
      this._projectXmlDebounce = setTimeout(async () => {
        if (this._projectXmlPushInFlight || !this._onProjectChange) return;
        this._projectXmlPushInFlight = true;
        try {
          const xml = await this.getProjectXml();
          this._onProjectChange(xml);
        } catch (e) {
          warn("project xml callback failed:", e);
        } finally {
          this._projectXmlPushInFlight = false;
          this._projectXmlDebounce = null;
        }
      }, 120);
    }

    _scheduleLoop() {
      if (this._destroyed) return;
      this._rafId = requestAnimationFrame(this._loopBound);
    }

    _loop() {
      if (this._destroyed || !this.world) return;
      try {
        this.world.doOneCycle();
      } catch (_) {}
      this._scheduleLoop();
    }

    stepWorld(n = 2) {
      if (!this.world) return;
      for (let i = 0; i < n; i++) {
        try {
          this.world.doOneCycle();
        } catch (_) {}
      }
    }

    forceLayout() {
      if (!this.world || !this.ide) return;
      try {
        const c = this.world.worldCanvas;
        const w = new Point(c.width, c.height);
        this.world.setExtent(w);
        if (this.ide.setExtent) this.ide.setExtent(w);
        if (this.ide.fixLayout) this.ide.fixLayout();
      } catch (e) {
        warn("forceLayout exception:", e);
      }
      this.stepWorld(3);
    }

    setLanguageAsync(lang) {
      return new Promise((resolve) => {
        if (!this.ide || typeof this.ide.setLanguage !== "function") return resolve(false);
        this.ide.setLanguage(lang, () => resolve(true), true);
      });
    }

    normalizeSnapLanguage(lang) {
      if (!lang || typeof lang !== "string") return "en";
      if (window.SnapTranslator?.dict && (lang in window.SnapTranslator.dict)) return lang;
      if (lang.includes("_")) {
        const base = lang.split("_")[0];
        if (window.SnapTranslator?.dict && (base in window.SnapTranslator.dict)) return base;
      }
      return "en";
    }

    loadLanguageScriptAsync(lang) {
      return new Promise((resolve) => {
        if (this._languageScriptElement?.parentNode) {
          this._languageScriptElement.parentNode.removeChild(this._languageScriptElement);
        }
        this._languageScriptElement = null;
        if (lang === "en") return resolve();

        const script = document.createElement("script");
        script.id = `language-editor-${this._instanceId}`;
        script.onload = () => resolve();
        script.onerror = () => resolve();
        script.src = BASE_PROG_DIR + "adjusted/lang-" + lang + ".js";
        this._languageScriptElement = script;
        document.head.appendChild(script);
      });
    }

    async setLanguageWithoutProjectReloadAsync(lang) {
      if (!this.ide) return false;
      const safeLang = this.normalizeSnapLanguage(lang);

      try { window.SnapTranslator?.unload?.(); } catch (_) {}
      await this.loadLanguageScriptAsync(safeLang);
      if (window.SnapTranslator) {
        window.SnapTranslator.language = safeLang;
      }

      const ide = this.ide;
      try { ide.flushBlocksCache?.(); } catch (_) {}
      try { window.SpriteMorph?.prototype?.initBlocks?.(); } catch (_) {}
      try { ide.spriteBar?.tabBar?.tabTo?.("scripts"); } catch (_) {}
      try { ide.createCategories?.(); } catch (_) {}
      try { ide.categories?.refreshEmpty?.(); } catch (_) {}
      try { ide.createCorralBar?.(); } catch (_) {}
      try { ide.refreshCustomizedPalette?.(); } catch (_) {}
      try { ide.fixLayout?.(); } catch (_) {}
      this.forceLayout();
      this.stepWorld(4);
      return true;
    }

    async loadProjectXmlCanonical(xml) {
      await this.boot();
      await this.setLanguageWithoutProjectReloadAsync("en");
      this.ide.loadProjectXML(xml);
      await TurtleStitchEditorInstance.sleep(350);
      try { this.ide.selectSprite?.(this.ide.currentSprite); } catch (_) {}
      this.forceLayout();
      this.stepWorld(3);
    }

    async setProjectXml(xml_content) {
      if (typeof xml_content !== "string") throw new Error("xml_content must be a string");
      await this.loadProjectXmlCanonical(xml_content);
      this._scheduleProjectXmlPush();
    }

    async getProjectXml() {
      await this.boot();
      if (!this.ide?.serializer || !this.ide?.scenes || !this.ide?.scene) {
        throw new Error("IDE is not ready for XML serialization.");
      }
      return this.ide.serializer.serialize(new Project(this.ide.scenes, this.ide.scene));
    }

    setProjectChangeListener(callback) {
      this._onProjectChange = typeof callback === "function" ? callback : null;
      this._scheduleProjectXmlPush();
    }

    clearProjectChangeListener() {
      this._onProjectChange = null;
    }

    allProgramPictures() {
      const pics = [];
      const ide = this.ide;
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

    svgDataUrlFromString(svgMarkup) {
      const encoded = btoa(unescape(encodeURIComponent(svgMarkup)));
      return `data:image/svg+xml;base64,${encoded}`;
    }

    snapshotAllProgramsSvgDataUrl() {
      this.forceLayout();
      this.stepWorld(2);

      const padding = 20;
      const pics = this.allProgramPictures();
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
        const node = `<image x=\"0\" y=\"${y}\" width=\"${canvas.width}\" height=\"${canvas.height}\" href=\"${href}\" />`;
        y += canvas.height + padding;
        return node;
      }).join("\n");

      const svg = [
        `<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${width}\" height=\"${height}\" viewBox=\"0 0 ${width} ${height}\">`,
        images,
        "</svg>"
      ].join("\n");

      return this.svgDataUrlFromString(svg);
    }

    snapshotStagePngDataUrl() {
      this.forceLayout();
      this.stepWorld(2);

      try {
        const stageImage = this.ide?.stage?.fullImage?.();
        if (stageImage && typeof stageImage.toDataURL === "function") {
          return stageImage.toDataURL("image/png");
        }
      } catch (_) {}

      if (this.world?.worldCanvas && typeof this.world.worldCanvas.toDataURL === "function") {
        return this.world.worldCanvas.toDataURL("image/png");
      }

      throw new Error("Could not generate stage PNG snapshot.");
    }

    activeProcessCount() {
      const ide = this.ide;
      const world = this.world;
      const processBuckets = [
        ide?.stage?.threads?.processes,
        ide?.threads?.processes,
        ide?.currentSprite?.threads?.processes,
        world?.hand?.threads?.processes
      ];

      return processBuckets.reduce((acc, bucket) => {
        if (Array.isArray(bucket)) return acc + bucket.length;
        return acc;
      }, 0);
    }

    runGreenFlag() {
      if (typeof this.ide?.pressStart === "function") {
        this.ide.pressStart();
        return;
      }
      this.ide.runScripts();
    }

    async runGreenFlagOnce() {
      this.forceLayout();
      try { this.ide.stopAllScripts?.(); } catch (_) {}
      try { this.ide.stage.clearPenTrails?.(); } catch (_) {}
      this.runGreenFlag();

      const started = Date.now();
      const minRuntimeMs = 700;
      const maxRuntimeMs = 3500;
      let lastTrailCount = -1;
      let stableTrailCycles = 0;
      let idleProcessCycles = 0;
      while (Date.now() - started < maxRuntimeMs) {
        this.stepWorld(2);

        const trailCount = this.ide?.stage?.trailsLog?.length ?? 0;
        const processCount = this.activeProcessCount();

        if (trailCount === lastTrailCount) stableTrailCycles += 1;
        else stableTrailCycles = 0;
        lastTrailCount = trailCount;

        if (processCount === 0) idleProcessCycles += 1;
        else idleProcessCycles = 0;

        const runtimeMs = Date.now() - started;
        const minRuntimeReached = runtimeMs >= minRuntimeMs;
        const trailsDone = trailCount > 0 && stableTrailCycles >= 3;
        const processesDone = idleProcessCycles >= 3;
        if (minRuntimeReached && (trailsDone || processesDone)) break;

        await TurtleStitchEditorInstance.sleep(120);
      }

      try { this.ide.stopAllScripts?.(); } catch (_) {}
      this.stepWorld(3);
    }

    async calcProgramSvg(xml_content, language) {
      if (typeof xml_content !== "string") throw new Error("xml_content must be a string");
      await this.loadProjectXmlCanonical(xml_content);
      if (language && language !== "en") {
        await this.setLanguageAsync(language);
        this.forceLayout();
        this.stepWorld(2);
      }
      return this.snapshotAllProgramsSvgDataUrl();
    }

    async simulateGreenFlag(xml_content) {
      if (typeof xml_content !== "string") throw new Error("xml_content must be a string");
      await this.loadProjectXmlCanonical(xml_content);
      await this.runGreenFlagOnce();
      return this.snapshotStagePngDataUrl();
    }

    async downloadDst(xml_content) {
      if (typeof xml_content !== "string") throw new Error("xml_content must be a string");
      await this.loadProjectXmlCanonical(xml_content);
      await this.runGreenFlagOnce();

      if (typeof window.exportEmbroidery !== "function") {
        throw new Error("exportEmbroidery not found (src/embroider.js missing?)");
      }
      const stage = this.ide?.stage;
      const trailsLog = stage?.trailsLog || stage?.turtle?.trailsLog || null;
      if (!trailsLog) throw new Error("No trailsLog after running; cannot export DST.");
      window.exportEmbroidery(trailsLog, "turtlestitch-export", "dst");
    }

    destroy() {
      if (this._destroyed) return;
      this._destroyed = true;

      if (this._rafId !== null) {
        cancelAnimationFrame(this._rafId);
      }

      if (this._projectXmlDebounce) {
        clearTimeout(this._projectXmlDebounce);
      }

      try { this.ide?.stopAllScripts?.(); } catch (_) {}
      try { this.ide?.destroy?.(); } catch (_) {}
      try { this.world?.destroy?.(); } catch (_) {}

      try {
        if (this.canvas) {
          this.canvas.width = 0;
          this.canvas.height = 0;
        }
      } catch (_) {}

      try { this.wrap?.remove?.(); } catch (_) {}

      if (window.world === this.world) window.world = null;
      if (window.ide === this.ide) window.ide = null;

      if (this._languageScriptElement?.parentNode) {
        this._languageScriptElement.parentNode.removeChild(this._languageScriptElement);
      }

      this._rafId = null;
      this._languageScriptElement = null;
      this._onProjectChange = null;
      this._projectXmlDebounce = null;
      this.world = null;
      this.ide = null;
      this.canvas = null;
      this.wrap = null;
      this.options = null;
      this._loopBound = null;
    }
  }

  async function ensureSingletonPreview() {
    await ensureScriptsLoaded();
    if (singletonPreview && !singletonPreview._destroyed) return singletonPreview;
    if (singletonBootPromise) return singletonBootPromise;

    singletonBootPromise = (async () => {
      const instance = new TurtleStitchEditorInstance({ hidden: true, width: 1400, height: 1000 });
      await instance.boot();
      singletonPreview = instance;

      // Keep these globals only for legacy debugging compatibility and only for singleton preview.
      window.world = instance.world;
      window.ide = instance.ide;

      log("[INFO] TurtleStitch hidden singleton preview booted.");
      return instance;
    })();

    try {
      return await singletonBootPromise;
    } finally {
      singletonBootPromise = null;
    }
  }

  async function createEditorInstance(options = {}) {
    const instance = new TurtleStitchEditorInstance(options);
    await instance.boot();
    return instance;
  }

  async function destroyHiddenPreview() {
    if (!singletonPreview) return;
    singletonPreview.destroy();
    singletonPreview = null;
  }

  const impl = {
    calcProgramPng: async (xml_content) => {
      const preview = await ensureSingletonPreview();
      return preview.simulateGreenFlag(xml_content);
    },
    calcProgramSvg: async (xml_content, language) => {
      const preview = await ensureSingletonPreview();
      return preview.calcProgramSvg(xml_content, language);
    },
    simulateGreenFlag: async (xml_content) => {
      const preview = await ensureSingletonPreview();
      return preview.simulateGreenFlag(xml_content);
    },
    downloadDst: async (xml_content) => {
      const preview = await ensureSingletonPreview();
      return preview.downloadDst(xml_content);
    },
    createEditor: async (options = {}) => createEditorInstance(options),
    destroyHiddenPreview
  };

  // Boot and mark ready
  (async () => {
    try {
      await ensureSingletonPreview();
      api._impl = impl;
      resolveReady();
      log("[INFO] API ready.");
    } catch (e) {
      window.TurtleStitchPoCError = e;
      rejectReady(e);
      err("[ERROR] API boot failed:", e);
    }
  })();
})();
