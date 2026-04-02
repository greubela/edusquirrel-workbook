// turtle-worker.js
// Dedicated worker focused on two minimal capabilities:
// 1) Snapshot of scripts beneath green flag (SVG data URL with PNG-embedded script images)
// 2) Preview image after one green-flag execution pass (PNG data URL)
//
// Notes:
// - This file intentionally omits editor UI, keyboard input, DOM integration, and language switching.
// - It provides a small DOM shim sufficient for Morphic/Snap loading in worker scope.
// - Deterministic policy for green-flag snapshots:
//   * no timeout-based completion logic
//   * no fallback image sources
//   * either produce the canonical result or fail with an error

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
        const attrName = String(name);
        const attrValue = String(value);
        this.attributes.set(attrName, attrValue);
        if (attrName === "id") this.id = attrValue;

        // Keep common DOM attributes in sync with element properties.
        // Snap/TurtleStitch often relies on setAttribute() rather than direct property assignment.
        if (attrName === "src" && "src" in this) this.src = attrValue;
        else if (attrName === "href" && "href" in this) this.href = attrValue;
        else if (attrName === "width" && "width" in this) this.width = Number(attrValue) || 0;
        else if (attrName === "height" && "height" in this) this.height = Number(attrValue) || 0;
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
            warn("drawImage failed in worker canvas context", err);
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

    async settleWorldCycles(cycles = 6) {
      for (let i = 0; i < cycles; i++) {
        this.stepWorld(1);
        await Promise.resolve();
      }
    }

    safeCall(label, action) {
      try {
        return action();
      } catch (err) {
        warn(`${label} failed`, err);
        return undefined;
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


    normalizeRequestedLanguage(language) {
      if (typeof language !== "string") return "en";
      const normalized = language.trim().toLowerCase();
      if (!normalized) return "en";
      return normalized.split(/[_-]/)[0] || "en";
    }

    languageExists(lang) {
      return Boolean(globalThis.SnapTranslator?.dict && lang in globalThis.SnapTranslator.dict);
    }

    async setLanguage(language) {
      if (!this.ide) return false;

      const requestedLanguage = this.normalizeRequestedLanguage(language);
      if (requestedLanguage !== "en" && !this.languageExists(requestedLanguage)) {
        importScriptOnce(BASE_PROG_DIR + "adjusted/lang-" + requestedLanguage + ".js");
      }
      const safeLang = this.languageExists(requestedLanguage) ? requestedLanguage : "en";

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
      this.safeCall("ide.flushBlocksCache", () => ide.flushBlocksCache?.());
      this.safeCall("SpriteMorph.initBlocks", () => globalThis.SpriteMorph?.prototype?.initBlocks?.());
      this.safeCall("ide.spriteBar.tabTo(scripts)", () => ide.spriteBar?.tabBar?.tabTo?.("scripts"));
      this.safeCall("ide.createCategories", () => ide.createCategories?.());
      this.safeCall("ide.categories.refreshEmpty", () => ide.categories?.refreshEmpty?.());
      this.safeCall("ide.createCorralBar", () => ide.createCorralBar?.());
      this.safeCall("ide.refreshCustomizedPalette", () => ide.refreshCustomizedPalette?.());
      this.safeCall("ide.fixLayout", () => ide.fixLayout?.());
      this.forceLayout();
      this.stepWorld(4);
      return true;
    }

    async loadProjectXmlCanonical(xml) {
      await this.boot();
      if (typeof xml !== "string") throw new Error("xml must be a string");

      // TurtleStitch XML parsing depends on English block specs.
      // Keep the editor in English while loading project XML.
      await this.setLanguage("en");
      this.ide.loadProjectXML(xml);
      await this.settleWorldCycles(12);

      this.safeCall("ide.selectSprite(currentSprite)", () => this.ide.selectSprite?.(this.ide.currentSprite));
      this.forceLayout();
      this.stepWorld(4);
    }

    greenFlagProgramPictures() {
      const topBlocks = this.greenFlagTopBlocks();
      if (!topBlocks.length) {
        throw new Error("No green-flag scripts found for program snapshot.");
      }

      const pics = [];
      for (const block of topBlocks) {
        const top = typeof block?.topBlock === "function" ? block.topBlock() : block;
        if (!top) {
          throw new Error("Encountered invalid green-flag top block while building program snapshot.");
        }

        let pic = null;
        if (typeof top.scriptPic === "function") {
          pic = top.scriptPic();
        } else if (typeof top.fullImage === "function") {
          pic = top.fullImage();
        }

        if (!pic) {
          throw new Error("Could not render a green-flag script picture.");
        }
        pics.push(pic);
      }
      return pics;
    }

    async snapshotProgramsPngDataUrl(pics) {
      this.forceLayout();
      this.stepWorld(2);

      const padding = 20;
      if (!pics.length) throw new Error("No scripts picture could be generated.");

      let width = 0;
      let height = 0;
      pics.forEach((p, i) => {
        width = Math.max(width, p.width);
        height += p.height;
        if (i < pics.length - 1) height += padding;
      });

      let y = 0;
      const composite = document.createElement("canvas");
      composite.width = Math.max(1, width);
      composite.height = Math.max(1, height);
      const ctx = composite.getContext("2d");
      if (!ctx) throw new Error("Could not get 2D context for program snapshot composition.");

      for (const canvas of pics) {
        try {
          ctx.drawImage(canvas, 0, y);
        } catch (err) {
          warn("Failed to draw program snapshot layer", err);
          throw new Error("Could not compose program snapshot PNG from script images.");
        }
        y += canvas.height + padding;
      }

      return canvasToPngDataUrl(composite);
    }

    async snapshotGreenFlagProgramsPngDataUrl() {
      return this.snapshotProgramsPngDataUrl(this.greenFlagProgramPictures());
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
      // Deterministic, fail-fast policy:
      // - no wall-clock timeouts
      // - no fallback completion heuristics
      // - complete only when processes become idle and trails stabilize
      this.forceLayout();
      this.safeCall("ide.stopAllScripts(before run)", () => this.ide.stopAllScripts?.());
      this.safeCall("ide.stage.clearPenTrails", () => this.ide.stage.clearPenTrails?.());
      this.runGreenFlag();

      let lastTrailCount = -1;
      let stableTrailCycles = 0;
      let idleProcessCycles = 0;
      while (true) {
        this.stepWorld(2);

        const trailCount = this.ide?.stage?.trailsLog?.length ?? 0;
        const processCount = this.activeProcessCount();

        if (trailCount === lastTrailCount) stableTrailCycles += 1;
        else stableTrailCycles = 0;
        lastTrailCount = trailCount;

        if (processCount === 0) idleProcessCycles += 1;
        else idleProcessCycles = 0;

        const trailsDone = stableTrailCycles >= 3;
        const processesDone = idleProcessCycles >= 3;

        if (trailsDone && processesDone) break;
        await Promise.resolve();
      }

      this.safeCall("ide.stopAllScripts(after run)", () => this.ide.stopAllScripts?.());
      this.stepWorld(3);
    }

    async snapshotStagePngDataUrl() {
      // No fallback behavior here: stage.fullImage() is the canonical output source.
      this.forceLayout();
      this.stepWorld(2);

      const stageImage = this.ide?.stage?.fullImage?.();
      if (stageImage) return await canvasToPngDataUrl(stageImage);

      throw new Error("Could not generate stage PNG snapshot.");
    }

    async calcProgramPng(xml, language = "en") {
      await this.loadProjectXmlCanonical(xml);
      await this.setLanguage(language);
      this.forceLayout();
      this.stepWorld(2);
      return this.snapshotGreenFlagProgramsPngDataUrl();
    }

    async simulateGreenFlag(xml, language = "en") {
      await this.loadProjectXmlCanonical(xml);
      await this.runGreenFlagOnce();
      await this.setLanguage(language);
      return this.snapshotStagePngDataUrl();
    }

    async getGreenFlagAsLispCode(xml, language = "en") {
      await this.loadProjectXmlCanonical(xml);
      await this.setLanguage(language);
      return this.greenFlagLispCode();
    }

    destroy() {
      if (this.destroyed) return;
      this.destroyed = true;

      this.safeCall("ide.stopAllScripts(destroy)", () => this.ide?.stopAllScripts?.());
      this.safeCall("ide.destroy", () => this.ide?.destroy?.());
      this.safeCall("world.destroy", () => this.world?.destroy?.());

      this.safeCall("canvas.cleanup", () => {
        if (this.canvas) {
          this.canvas.width = 0;
          this.canvas.height = 0;
          this.canvas.remove?.();
        }
      });

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
      case "calcProgramPng": {
        const engine = await ensureSingletonEngine();
        const result = await engine.calcProgramPng(payload?.xml_content, payload?.language || "en");
        return { id, ok: true, result };
      }
      case "simulateGreenFlag": {
        const engine = await ensureSingletonEngine();
        const result = await engine.simulateGreenFlag(payload?.xml_content, payload?.language || "en");
        return { id, ok: true, result };
      }
      case "getGreenFlagAsLispCode": {
        const engine = await ensureSingletonEngine();
        const result = await engine.getGreenFlagAsLispCode(payload?.xml_content, payload?.language || "en");
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
