const CM_BASE_CSS = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.css";
const CM_THEME_CSS = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/theme/material-darker.min.css";
const CM_BASE_JS = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/codemirror.min.js";
const CM_PYTHON_JS = "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.65.16/mode/python/python.min.js";

function ensureStylesheet(href) {
  const links = document.getElementsByTagName("link");
  for (let i = 0; i < links.length; i += 1) {
    const link = links[i];
    if (link.rel === "stylesheet" && link.href === href) {
      return;
    }
  }
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = href;
  document.head.appendChild(link);
}

function loadScript(src) {
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = src;
    script.async = false;
    script.addEventListener("load", () => resolve());
    script.addEventListener("error", () => reject(new Error("Failed to load " + src)));
    document.head.appendChild(script);
  });
}

let loadPromise = null;

function ensureCodeMirror() {
  if (loadPromise) return loadPromise;
  ensureStylesheet(CM_BASE_CSS);
  ensureStylesheet(CM_THEME_CSS);
  loadPromise = loadScript(CM_BASE_JS)
    .then(() => loadScript(CM_PYTHON_JS))
    .then(() => {
      if (!window.CodeMirror) {
        throw new Error("CodeMirror not available");
      }
      return window.CodeMirror;
    });
  return loadPromise;
}

globalThis.EduSquirrelCodeMirror = {
  createEditor: ({parent, doc, onDocChange}) => {
    let isProgrammaticUpdate = false;
    let cmInstance = null;
    let pendingDoc = doc;
    let refreshScheduled = false;

    const textarea = document.createElement("textarea");
    textarea.value = doc || "";
    parent.appendChild(textarea);

    ensureCodeMirror().then((CodeMirror) => {
      cmInstance = CodeMirror.fromTextArea(textarea, {
        lineNumbers: true,
        mode: "python",
        theme: "material-darker",
        indentUnit: 4,
        tabSize: 4,
        indentWithTabs: false,
        lineWrapping: true
      });

      function getIndentGuideColor() {
        return document.documentElement.getAttribute("data-theme") === "light"
          ? "rgba(70, 100, 180, 0.45)"
          : "rgba(255, 255, 255, 0.40)";
      }

      new MutationObserver(() => cmInstance.refresh())
        .observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

      function countIndentColumns(text, tabSize) {
        let count = 0;
        for (let i = 0; i < text.length; i += 1) {
          const ch = text.charAt(i);
          if (ch === " ") count += 1;
          else if (ch === "\t") count += tabSize;
          else break;
        }
        return count;
      }

      function applyIndentGuides(line, element) {
        const tabSize = cmInstance.getOption("tabSize") || 4;
        const indentUnit = cmInstance.getOption("indentUnit") || 4;
        let indentColumns = countIndentColumns(line.text || "", tabSize);
        const isBlankLine = !line.text || line.text.trim() === "";
        if (indentColumns === 0 && isBlankLine) {
          const lineNumber = cmInstance.getLineNumber(line);
          if (lineNumber != null && lineNumber > 0) {
            const prevText = cmInstance.getLine(lineNumber - 1) || "";
            indentColumns = countIndentColumns(prevText, tabSize);
          }
        }
        const levels = Math.floor(indentColumns / indentUnit);
        const existing = element.querySelector(".fd-indent-guides");
        if (levels > 0) {
          const widthCh = levels * indentUnit;
          const stepCh = indentUnit;
          const overlay = existing || document.createElement("span");
          if (!existing) {
            overlay.className = "fd-indent-guides";
            overlay.style.position = "absolute";
            overlay.style.top = "0";
            overlay.style.bottom = "0";
            overlay.style.left = "0";
            overlay.style.pointerEvents = "none";
            element.style.position = "relative";
            element.appendChild(overlay);
          }
          overlay.style.width = widthCh + "ch";
          const guideColor = getIndentGuideColor();
          overlay.style.backgroundImage =
            "repeating-linear-gradient(to right, " +
            guideColor + " 0, " +
            guideColor + " 1px, transparent 1px, transparent " +
            stepCh + "ch)";
          overlay.style.backgroundSize = stepCh + "ch 100%";
          overlay.style.backgroundRepeat = "repeat";
          overlay.style.backgroundPosition = "0 0";
        } else if (existing) {
          existing.remove();
        }
      }

      cmInstance.on("renderLine", (instance, line, element) => {
        applyIndentGuides(line, element);
      });

      if (pendingDoc != null) {
        cmInstance.setValue(pendingDoc);
      }

      cmInstance.on("change", () => {
        if (!isProgrammaticUpdate) {
          onDocChange(cmInstance.getValue());
        }
        if (!refreshScheduled) {
          refreshScheduled = true;
          requestAnimationFrame(() => {
            refreshScheduled = false;
            cmInstance.refresh();
          });
        }
      });
    });

    return {
      setDoc(newDoc) {
        if (cmInstance) {
          if (cmInstance.getValue() === newDoc) {
            return;
          }
          isProgrammaticUpdate = true;
          cmInstance.setValue(newDoc);
          isProgrammaticUpdate = false;
        } else {
          pendingDoc = newDoc;
          textarea.value = newDoc;
        }
      },
      getDoc() {
        return cmInstance ? cmInstance.getValue() : textarea.value;
      },
      focus() {
        if (cmInstance) cmInstance.focus();
      },
      destroy() {
        if (cmInstance) {
          cmInstance.toTextArea();
        }
        if (textarea.parentNode) {
          textarea.parentNode.removeChild(textarea);
        }
      }
    };
  }
};
