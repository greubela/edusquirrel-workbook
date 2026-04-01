// turtle-worker.js
// Dedicated worker focused on two minimal capabilities:
// 1) Snapshot of scripts beneath green flag (SVG data URL with PNG-embedded script images)
// 2) Preview image after one green-flag execution pass (PNG data URL)
//
// Notes:
// - This file intentionally omits editor UI, keyboard input, DOM integration, and language switching.
// - It provides a small DOM shim sufficient for Morphic/Snap loading in worker scope.

(() => {
  "use strict";

  const LOG_PREFIX = "[TurtleWorker]";
  const log = (...a) => console.log(LOG_PREFIX, ...a);
  const warn = (...a) => console.warn(LOG_PREFIX, ...a);
  const BASE_PROG_DIR = "./";

  const SNAP_SCRIPT_ORDER = [
    "adjusted/adjustedMorphic.js",
    "turtlestitchsrc/symbols.js",
    "turtlestitchsrc/widgets.js",
    "turtlestitchsrc/blocks.js",
    "turtlestitchsrc/threads.js",
    "adjusted/adjustedObjects.js",
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
  const importedScriptUrls = new Set();

  const WORKER_BASE_URL = (() => {
    try {
      return self.location && typeof self.location.href === "string"
        ? self.location.href
        : undefined;
    } catch (_) {
      return undefined;
    }
  })();

  function normalizeScriptUrl(src) {
    if (!src) return "";
    try {
      const base = WORKER_BASE_URL || BASE_PROG_DIR;
      return new URL(String(src), base).href;
    } catch (_) {
      return String(src);
    }
  }

  function importScriptOnce(src) {
    const normalized = normalizeScriptUrl(src);
    if (!normalized || importedScriptUrls.has(normalized)) return false;
    importScripts(normalized);
    importedScriptUrls.add(normalized);
    return true;
  }


  function installWorkerDomShim() {
    if (globalThis.document && globalThis.window) return;

    const timers = new Map();
    let rafId = 1;

    const styleProxy = {
      setProperty() {},
      removeProperty() {}
    };

    const tryLoadScriptNode = (node) => {
      if (!node || node.tagName !== "SCRIPT" || !node.src || node.__scriptLoaded) return;
      try {
        importScriptOnce(node.src);
        node.__scriptLoaded = true;
        if (typeof node.onload === "function") node.onload();
      } catch (e) {
        if (typeof node.onerror === "function") node.onerror(e);
      }
    };

    class MiniNode {
      constructor(tagName = "node") {
        this.tagName = String(tagName).toUpperCase();
        this.children = [];
        this.parentNode = null;
        this.style = Object.create(styleProxy);
        this.dataset = {};
        this.attributes = new Map();
        this.id = "";
        this.value = "";
        this.tabIndex = 0;
      }

      appendChild(child) {
        if (!child) return child;
        if (child.parentNode && child.parentNode !== this && typeof child.parentNode.removeChild === "function") {
          child.parentNode.removeChild(child);
        }
        child.parentNode = this;
        this.children.push(child);

        if (this === document.head && child.tagName === "SCRIPT") {
          tryLoadScriptNode(child);
        }
        return child;
      }

      removeChild(child) {
        const idx = this.children.indexOf(child);
        if (idx >= 0) this.children.splice(idx, 1);
        child.parentNode = null;
        return child;
      }

      remove() {
        if (this.parentNode && typeof this.parentNode.removeChild === "function") {
          this.parentNode.removeChild(this);
        }
      }

      setAttribute(name, value) {
        this.attributes.set(name, String(value));
        if (name === "id") this.id = String(value);
      }

      getAttribute(name) {
        return this.attributes.get(name) || null;
      }

      addEventListener() {}
      removeEventListener() {}
      focus() {}
      blur() {}
      getBoundingClientRect() {
        const width = Number(this.width || 0);
        const height = Number(this.height || 0);
        return {
          x: 0,
          y: 0,
          top: 0,
          left: 0,
          right: width,
          bottom: height,
          width,
          height
        };
      }
    }

    class CanvasNode extends MiniNode {
      constructor() {
        super("canvas");
        this._canvas = new OffscreenCanvas(300, 150);
        this.isRetinaEnabled = false;
      }

      get width() {
        return this._canvas.width;
      }
      set width(v) {
        this._canvas.width = Number(v) || 0;
      }

      get height() {
        return this._canvas.height;
      }
      set height(v) {
        this._canvas.height = Number(v) || 0;
      }

      getContext(type, opts) {
        return this._canvas.getContext(type, opts);
      }

      async toBlob(type = "image/png", quality) {
        return this._canvas.convertToBlob({ type, quality });
      }

      addEventListener() {}
      removeEventListener() {}
    }

    class TextAreaNode extends MiniNode {
      constructor() {
        super("textarea");
      }
    }

    class ImageNode extends MiniNode {
      constructor() {
        super("img");
        this.width = 0;
        this.height = 0;
        this.complete = false;
        this.onload = null;
        this.onerror = null;
        this.crossOrigin = "";
        this._src = "";
        this._bitmap = null;
      }

      get src() {
        return this._src;
      }

      set src(value) {
        this._src = String(value || "");
        this.complete = false;
        this._bitmap = null;

        const notifyError = (err) => {
          this.complete = false;
          if (typeof this.onerror === "function") this.onerror(err);
        };

        const notifyReady = () => {
          this.complete = true;
          if (typeof this.onload === "function") this.onload();
        };

        if (!this._src) {
          notifyReady();
          return;
        }

        const canDecodeBitmap = typeof fetch === "function" && typeof createImageBitmap === "function";
        if (!canDecodeBitmap) {
          if (typeof queueMicrotask === "function") queueMicrotask(notifyReady);
          else setTimeout(notifyReady, 0);
          return;
        }

        fetch(this._src)
          .then((response) => {
            if (!response.ok) throw new Error(`Image fetch failed (${response.status})`);
            return response.blob();
          })
          .then((blob) => createImageBitmap(blob))
          .then((bitmap) => {
            this._bitmap = bitmap;
            this.width = bitmap.width || this.width;
            this.height = bitmap.height || this.height;
            notifyReady();
          })
          .catch(notifyError);
      }
    }

    class ScriptNode extends MiniNode {
      constructor() {
        super("script");
        this.__src = "";
        this.__scriptLoaded = false;
        this.async = false;
        this.onload = null;
        this.onerror = null;
      }

      get src() {
        return this.__src;
      }

      set src(v) {
        this.__src = String(v || "");
        if (this.parentNode && this.parentNode.tagName === "HEAD") {
          tryLoadScriptNode(this);
        }
      }
    }

    const byId = new Map();

    const document = {
      head: new MiniNode("head"),
      body: new MiniNode("body"),
      documentElement: {
        clientHeight: 1000,
        clientWidth: 1400,
        scrollTop: 0,
        scrollLeft: 0
      },
      createElement(tag) {
        const t = String(tag).toLowerCase();
        if (t === "canvas") return new CanvasNode();
        if (t === "textarea") return new TextAreaNode();
        if (t === "img" || t === "image") return new ImageNode();
        if (t === "script") return new ScriptNode();
        return new MiniNode(t);
      },
      createElementNS(_namespace, tag) {
        return this.createElement(tag);
      },
      getElementById(id) {
        return byId.get(String(id)) || null;
      },
      addEventListener() {},
      removeEventListener() {},
      querySelector() { return null; }
    };

    const originalAppendChild = document.body.appendChild.bind(document.body);
    document.body.appendChild = (child) => {
      const appended = originalAppendChild(child);
      if (child?.id) byId.set(String(child.id), child);
      return appended;
    };

    const windowObj = globalThis;
    const defineAlias = (name, value) => {
      try {
        const descriptor = Object.getOwnPropertyDescriptor(windowObj, name);
        if (!descriptor) {
          Object.defineProperty(windowObj, name, {
            configurable: true,
            enumerable: true,
            writable: true,
            value
          });
          return;
        }
        if (descriptor.writable) {
          windowObj[name] = value;
        }
      } catch (_) {
        // Ignore read-only global aliases in strict mode workers.
      }
    };

    defineAlias("window", windowObj);
    defineAlias("self", windowObj);
    defineAlias("global", windowObj);
    const setGlobalIfWritable = (name, value) => {
      try {
        const descriptor = Object.getOwnPropertyDescriptor(windowObj, name);
        if (!descriptor) {
          Object.defineProperty(windowObj, name, {
            configurable: true,
            enumerable: true,
            writable: true,
            value
          });
          return;
        }
        if (descriptor.writable) {
          windowObj[name] = value;
          return;
        }
        if (descriptor.configurable) {
          Object.defineProperty(windowObj, name, {
            configurable: true,
            enumerable: true,
            writable: true,
            value
          });
        }
      } catch (_) {
        // Ignore read-only globals exposed by some WorkerGlobalScope implementations.
      }
    };

    setGlobalIfWritable("document", document);
    setGlobalIfWritable("navigator", windowObj.navigator || { language: "en-US", platform: "worker" });
    setGlobalIfWritable("innerHeight", 1000);
    setGlobalIfWritable("innerWidth", 1400);
    setGlobalIfWritable("pageXOffset", 0);
    setGlobalIfWritable("pageYOffset", 0);
    setGlobalIfWritable("devicePixelRatio", 1);
    setGlobalIfWritable("screen", windowObj.screen || { width: 1400, height: 1000 });
    setGlobalIfWritable("performance", windowObj.performance || { now: () => Date.now() });
    setGlobalIfWritable("location", windowObj.location || { href: "", hash: "", search: "" });
    const makeStorage = () => {
      const data = new Map();
      return {
        getItem(key) { return data.has(String(key)) ? data.get(String(key)) : null; },
        setItem(key, value) { data.set(String(key), String(value)); },
        removeItem(key) { data.delete(String(key)); },
        clear() { data.clear(); }
      };
    };
    setGlobalIfWritable("sessionStorage", windowObj.sessionStorage || makeStorage());
    setGlobalIfWritable("localStorage", windowObj.localStorage || makeStorage());
    if (!windowObj.Image && typeof OffscreenCanvas !== "undefined") {
      class WorkerImage extends ImageNode {
        constructor() {
          super();
        }
      }
      setGlobalIfWritable("Image", WorkerImage);
      setGlobalIfWritable("HTMLImageElement", WorkerImage);
    }

    windowObj.requestAnimationFrame = (cb) => {
      const id = rafId++;
      const handle = setTimeout(() => {
        timers.delete(id);
        cb(Date.now());
      }, 16);
      timers.set(id, handle);
      return id;
    };

    windowObj.cancelAnimationFrame = (id) => {
      const handle = timers.get(id);
      if (handle) {
        clearTimeout(handle);
        timers.delete(id);
      }
    };

    windowObj.addEventListener = windowObj.addEventListener || (() => {});
    windowObj.removeEventListener = windowObj.removeEventListener || (() => {});
    windowObj.open = windowObj.open || (() => ({ document: { write() {}, close() {} } }));
    windowObj.alert = windowObj.alert || (() => {});
    windowObj.confirm = windowObj.confirm || (() => false);
    windowObj.prompt = windowObj.prompt || (() => null);
    windowObj.matchMedia = windowObj.matchMedia || (() => ({ matches: false, addListener() {}, removeListener() {} }));
    windowObj.HTMLCanvasElement = windowObj.HTMLCanvasElement || CanvasNode;
    windowObj.OffscreenCanvas = windowObj.OffscreenCanvas || OffscreenCanvas;
    if (!windowObj.CanvasRenderingContext2D && typeof OffscreenCanvas !== "undefined") {
      try {
        const ctx = new OffscreenCanvas(1, 1).getContext("2d");
        if (ctx?.constructor) {
          setGlobalIfWritable("CanvasRenderingContext2D", ctx.constructor);
        }
      } catch (_) {
        // ignore if context constructor cannot be discovered in this runtime
      }
    }

    const contextProto = windowObj.CanvasRenderingContext2D?.prototype;
    if (contextProto && !contextProto.__turtleWorkerDrawImagePatched) {
      const originalDrawImage = contextProto.drawImage;
      if (typeof originalDrawImage === "function") {
        contextProto.drawImage = function patchedDrawImage(image, ...rest) {
          const normalizedImage =
            image && image._bitmap ? image._bitmap :
            image && image._canvas ? image._canvas :
            image;
          if (!normalizedImage) {
            return;
          }
          try {
            return originalDrawImage.call(this, normalizedImage, ...rest);
          } catch (err) {
            if (err instanceof TypeError) {
              return;
            }
            throw err;
          }
        };
        try {
          Object.defineProperty(contextProto, "__turtleWorkerDrawImagePatched", {
            value: true,
            enumerable: false,
            configurable: true
          });
        } catch (_) {
          contextProto.__turtleWorkerDrawImagePatched = true;
        }
      }
    }
  }

  function toBase64(bytes) {
    let binary = "";
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
  }

  async function canvasToPngDataUrl(canvasLike) {
    if (!canvasLike) throw new Error("canvasToPngDataUrl: missing canvas");

    if (typeof canvasLike.toDataURL === "function") {
      try {
        return canvasLike.toDataURL("image/png");
      } catch (_) {}
    }

    if (typeof canvasLike.convertToBlob === "function") {
      const blob = await canvasLike.convertToBlob({ type: "image/png" });
      const buffer = await blob.arrayBuffer();
      return `data:image/png;base64,${toBase64(new Uint8Array(buffer))}`;
    }

    if (typeof canvasLike.toBlob === "function") {
      const blob = await canvasLike.toBlob("image/png");
      const buffer = await blob.arrayBuffer();
      return `data:image/png;base64,${toBase64(new Uint8Array(buffer))}`;
    }

    if (canvasLike._canvas && typeof canvasLike._canvas.convertToBlob === "function") {
      const blob = await canvasLike._canvas.convertToBlob({ type: "image/png" });
      const buffer = await blob.arrayBuffer();
      return `data:image/png;base64,${toBase64(new Uint8Array(buffer))}`;
    }

    throw new Error("Cannot encode canvas to PNG data URL");
  }

  async function ensureScriptsLoaded() {
    if (scriptsLoaded) return;
    installWorkerDomShim();
    globalThis.base_prog_dir = BASE_PROG_DIR;

    const urls = SNAP_SCRIPT_ORDER.map((p) => BASE_PROG_DIR + p);
    urls.forEach((url) => importScriptOnce(url));

    if (!globalThis.WorldMorph || !globalThis.IDE_Morph) {
      throw new Error("WorldMorph/IDE_Morph missing after worker script load");
    }
    scriptsLoaded = true;
  }

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async function withTimeout(promise, timeoutMs, label) {
    let timer = null;
    try {
      return await Promise.race([
        promise,
        new Promise((_, reject) => {
          timer = setTimeout(() => reject(new Error(`Timeout: ${label}`)), timeoutMs);
        })
      ]);
    } finally {
      if (timer) clearTimeout(timer);
    }
  }

  class TurtleWorkerEngine {
    constructor(options = {}) {
      this.options = options;
      this.world = null;
      this.ide = null;
      this.canvas = null;
      this.destroyed = false;
    }

    async boot() {
      if (this.destroyed) throw new Error("Engine was destroyed");
      await ensureScriptsLoaded();
      if (this.world && this.ide) return;

      const width = this.options.width || 1400;
      const height = this.options.height || 1000;

      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      canvas.tabIndex = 1;

      document.body.appendChild(canvas);

      const world = new WorldMorph(canvas, false);
      world.worldCanvas = canvas;

      const ide = new IDE_Morph({ noAutoFill: true, noCloud: true });
      ide.openIn(world);

      this.canvas = canvas;
      this.world = world;
      this.ide = ide;

      this.forceLayout();
      this.stepWorld(4);
    }

    stepWorld(n = 2) {
      if (!this.world || this.destroyed) return;
      for (let i = 0; i < n; i++) {
        try {
          this.world.doOneCycle();
        } catch (e) {
          warn("world cycle failed", e);
        }
      }
    }

    forceLayout() {
      if (!this.world || !this.ide) return;
      try {
        const c = this.world.worldCanvas;
        const extent = new Point(c.width, c.height);
        this.world.setExtent(extent);
        if (this.ide.setExtent) this.ide.setExtent(extent);
        if (this.ide.fixLayout) this.ide.fixLayout();
      } catch (e) {
        warn("forceLayout exception", e);
      }
    }


    normalizeSnapLanguage(lang) {
      if (!lang || typeof lang !== "string") return "en";
      if (globalThis.SnapTranslator?.dict && (lang in globalThis.SnapTranslator.dict)) return lang;
      if (lang.includes("_") || lang.includes("-")) {
        const base = lang.split(/[_-]/)[0];
        if (globalThis.SnapTranslator?.dict && (base in globalThis.SnapTranslator.dict)) return base;
      }
      return "en";
    }

    requestedLanguageCode(lang) {
      if (!lang || typeof lang !== "string") return "en";
      const normalized = lang.trim().toLowerCase();
      if (!normalized) return "en";
      return normalized.split(/[_-]/)[0] || "en";
    }

    async setLanguageWithoutProjectReloadAsync(language) {
      if (!this.ide) return false;

      const requestedLanguage = this.requestedLanguageCode(language);
      let safeLang = this.normalizeSnapLanguage(requestedLanguage);

      if (safeLang !== "en") {
        // Language packs should only be imported once per worker lifetime.
        // Repeated importScripts() can be expensive and destabilize long-running workers.
        importScriptOnce(BASE_PROG_DIR + "adjusted/lang-" + safeLang + ".js");
        // Re-check after import so unsupported language requests gracefully fall back.
        safeLang = this.normalizeSnapLanguage(requestedLanguage);
      }

      if (globalThis.SnapTranslator) {
        globalThis.SnapTranslator.language = safeLang;
      }

      const ide = this.ide;
      if (typeof ide.setLanguage === "function") {
        await new Promise((resolve) => {
          try {
            ide.setLanguage(safeLang, () => resolve(), true);
          } catch (_) {
            resolve();
          }
        });
      }
      try { ide.flushBlocksCache?.(); } catch (_) {}
      try { globalThis.SpriteMorph?.prototype?.initBlocks?.(); } catch (_) {}
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
      if (typeof xml !== "string") throw new Error("xml must be a string");

      // TurtleStitch XML parsing depends on English block specs.
      // Keep the editor in English while loading project XML.
      await this.setLanguageWithoutProjectReloadAsync("en");
      this.ide.loadProjectXML(xml);
      await sleep(350);

      try { this.ide.selectSprite?.(this.ide.currentSprite); } catch (_) {}
      this.forceLayout();
      this.stepWorld(4);
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

    async snapshotAllProgramsSvgDataUrl() {
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
      const imageNodes = [];
      for (const canvas of pics) {
        try {
          const href = await withTimeout(canvasToPngDataUrl(canvas), 4000, "canvasToPngDataUrl");
          imageNodes.push(`<image x=\"0\" y=\"${y}\" width=\"${canvas.width}\" height=\"${canvas.height}\" href=\"${href}\" />`);
          y += canvas.height + padding;
        } catch (e) {
          warn("Skipping script image after PNG conversion failure", e);
        }
      }

      if (!imageNodes.length) {
        throw new Error("No scripts picture could be encoded as PNG.");
      }

      const svg = [
        `<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${width}\" height=\"${height}\" viewBox=\"0 0 ${width} ${height}\">`,
        ...imageNodes,
        "</svg>"
      ].join("\n");

      return this.svgDataUrlFromString(svg);
    }

    activeProcessCount() {
      const ide = this.ide;
      const world = this.world;
      const buckets = [
        ide?.stage?.threads?.processes,
        ide?.threads?.processes,
        ide?.currentSprite?.threads?.processes,
        world?.hand?.threads?.processes
      ];
      return buckets.reduce((acc, bucket) => acc + (Array.isArray(bucket) ? bucket.length : 0), 0);
    }

    runGreenFlag() {
      if (typeof this.ide?.pressStart === "function") {
        this.ide.pressStart();
        return;
      }
      this.ide.runScripts();
    }

    greenFlagTopBlocks() {
      const ide = this.ide;
      if (!ide) return [];

      const topBlocks = [];
      const addFromScripts = (scripts) => {
        scripts?.children?.forEach?.((block) => {
          if (block?.selector !== "receiveGo") return;
          if (typeof block.topBlock === "function" && block.topBlock() !== block) return;
          topBlocks.push(block);
        });
      };

      addFromScripts(ide.stage?.scripts);
      ide.sprites?.asArray?.().forEach((sprite) => addFromScripts(sprite?.scripts));
      return topBlocks;
    }

    greenFlagLispCode() {
      const snippets = this.greenFlagTopBlocks()
        .map((block) => {
          try {
            return typeof block?.toLisp === "function" ? block.toLisp(4) : "";
          } catch (_) {
            return "";
          }
        })
        .filter((text) => typeof text === "string" && text.trim().length > 0);

      if (!snippets.length) {
        throw new Error("No green-flag script found for Lisp export.");
      }
      return snippets.join("\n\n");
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

        await sleep(120);
      }

      try { this.ide.stopAllScripts?.(); } catch (_) {}
      this.stepWorld(3);
    }

    async snapshotStagePngDataUrl() {
      this.forceLayout();
      this.stepWorld(2);

      try {
        const stageImage = this.ide?.stage?.fullImage?.();
        if (stageImage) return await canvasToPngDataUrl(stageImage);
      } catch (_) {}

      if (this.world?.worldCanvas) {
        return canvasToPngDataUrl(this.world.worldCanvas);
      }

      throw new Error("Could not generate stage PNG snapshot.");
    }

    async calcProgramSvg(xml, language = "en") {
      await this.loadProjectXmlCanonical(xml);
      await this.setLanguageWithoutProjectReloadAsync(language);
      this.forceLayout();
      this.stepWorld(2);
      return this.snapshotAllProgramsSvgDataUrl();
    }

    async simulateGreenFlag(xml, language = "en") {
      await this.loadProjectXmlCanonical(xml);
      await this.runGreenFlagOnce();
      await this.setLanguageWithoutProjectReloadAsync(language);
      return this.snapshotStagePngDataUrl();
    }

    async getGreenFlagAsLispCode(xml, language = "en") {
      await this.loadProjectXmlCanonical(xml);
      await this.setLanguageWithoutProjectReloadAsync(language);
      return this.greenFlagLispCode();
    }

    destroy() {
      if (this.destroyed) return;
      this.destroyed = true;

      try { this.ide?.stopAllScripts?.(); } catch (_) {}
      try { this.ide?.destroy?.(); } catch (_) {}
      try { this.world?.destroy?.(); } catch (_) {}

      try {
        if (this.canvas) {
          this.canvas.width = 0;
          this.canvas.height = 0;
          this.canvas.remove?.();
        }
      } catch (_) {}

      this.world = null;
      this.ide = null;
      this.canvas = null;
    }
  }

  let singletonEngine = null;
  let bootPromise = null;

  async function ensureSingletonEngine() {
    if (singletonEngine && !singletonEngine.destroyed) return singletonEngine;
    if (bootPromise) return bootPromise;

    bootPromise = (async () => {
      const engine = new TurtleWorkerEngine({ width: 1400, height: 1000 });
      await engine.boot();
      singletonEngine = engine;
      return engine;
    })();

    try {
      return await bootPromise;
    } finally {
      bootPromise = null;
    }
  }

  const OPERATION_TIMEOUT_MS = 75000;

  async function handleMessage(data) {
    const { id, type, payload } = data || {};
    if (!id || typeof type !== "string") {
      throw new Error("Worker message must include { id, type, payload }");
    }

    switch (type) {
      case "init": {
        await ensureSingletonEngine();
        return { id, ok: true, result: { ready: true } };
      }
      case "calcProgramSvg": {
        const engine = await ensureSingletonEngine();
        const result = await withTimeout(
          engine.calcProgramSvg(payload?.xml_content, payload?.language || "en"),
          OPERATION_TIMEOUT_MS,
          "calcProgramSvg"
        );
        return { id, ok: true, result };
      }
      case "simulateGreenFlag": {
        const engine = await ensureSingletonEngine();
        const result = await withTimeout(
          engine.simulateGreenFlag(payload?.xml_content, payload?.language || "en"),
          OPERATION_TIMEOUT_MS,
          "simulateGreenFlag"
        );
        return { id, ok: true, result };
      }
      case "getGreenFlagAsLispCode": {
        const engine = await ensureSingletonEngine();
        const result = await withTimeout(
          engine.getGreenFlagAsLispCode(payload?.xml_content, payload?.language || "en"),
          OPERATION_TIMEOUT_MS,
          "getGreenFlagAsLispCode"
        );
        return { id, ok: true, result };
      }
      case "destroy": {
        if (singletonEngine) singletonEngine.destroy();
        singletonEngine = null;
        return { id, ok: true, result: { destroyed: true } };
      }
      default:
        throw new Error(`Unsupported worker operation: ${type}`);
    }
  }

  self.addEventListener("message", async (event) => {
    const data = event?.data || {};
    const id = data?.id;
    try {
      const response = await handleMessage(data);
      self.postMessage(response);
    } catch (e) {
      self.postMessage({
        id,
        ok: false,
        error: {
          message: e?.message || String(e),
          stack: e?.stack || null
        }
      });
    }
  });

  log("worker script loaded");
})();
