import {EditorState, StateEffect, StateField} from "https://esm.sh/@codemirror/state@6.5.2";
import {
  EditorView,
  Decoration,
  keymap,
  drawSelection,
  highlightActiveLine,
  lineNumbers,
  highlightActiveLineGutter,
  ViewPlugin
} from "https://esm.sh/@codemirror/view@6.38.6?deps=@codemirror/state@6.5.2";
import {defaultKeymap, history, historyKeymap, indentLess, indentMore} from "https://esm.sh/@codemirror/commands@6.8.1?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";
import {
  bracketMatching,
  foldGutter,
  foldKeymap,
  indentUnit,
  indentOnInput,
  syntaxHighlighting,
  defaultHighlightStyle,
  syntaxTree
} from "https://esm.sh/@codemirror/language@6.11.3?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6";
import {highlightSelectionMatches, searchKeymap} from "https://esm.sh/@codemirror/search@6.5.11?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6";
import {python} from "https://esm.sh/@codemirror/lang-python@6.2.1?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3,@codemirror/autocomplete@6.18.4";
import {cpp} from "https://esm.sh/@codemirror/lang-cpp@6.0.2?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";
import {oneDark} from "https://esm.sh/@codemirror/theme-one-dark@6.1.3?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";
import {indentationMarkers} from "https://esm.sh/@replit/codemirror-indentation-markers@6.5.3?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";

const INDENT_SPACES = "    ";

const replaceTabsWithSpaces = (text) => text.replace(/\t/g, INDENT_SPACES);

const setDiagnosticsEffect = StateEffect.define();

const diagnosticTheme = EditorView.theme({
  "&": {
    "--edusquirrel-diag-warning": "255, 190, 88",
    "--edusquirrel-diag-error": "255, 96, 96",
    "--edusquirrel-diag-soft": "98, 184, 255",
    "--edusquirrel-diag-line-alpha": "0.18",
    "--edusquirrel-diag-line-fade": "0.055",
    "--edusquirrel-diag-rail-alpha": "0.92",
    "--edusquirrel-diag-mark-alpha": "0.13"
  },
  ".cm-edusquirrel-diagnostic": {
    position: "relative",
    backgroundImage: [
      "linear-gradient(90deg, rgba(var(--edusquirrel-diag-warning), var(--edusquirrel-diag-line-alpha)), rgba(var(--edusquirrel-diag-warning), var(--edusquirrel-diag-line-fade)) 42%, transparent 82%)",
      "linear-gradient(180deg, rgba(255,255,255,0.045), transparent 58%)"
    ].join(", "),
    boxShadow: [
      "inset 3px 0 0 rgba(var(--edusquirrel-diag-warning), var(--edusquirrel-diag-rail-alpha))",
      "inset 0 1px 0 rgba(var(--edusquirrel-diag-warning), 0.10)",
      "inset 0 -1px 0 rgba(var(--edusquirrel-diag-warning), 0.06)"
    ].join(", "),
    transition: "background-color 0.16s ease, box-shadow 0.16s ease"
  },
  ".cm-edusquirrel-diagnostic-error": {
    backgroundImage: [
      "linear-gradient(90deg, rgba(var(--edusquirrel-diag-error), calc(var(--edusquirrel-diag-line-alpha) + 0.06)), rgba(var(--edusquirrel-diag-error), calc(var(--edusquirrel-diag-line-fade) + 0.025)) 42%, transparent 82%)",
      "linear-gradient(180deg, rgba(255,255,255,0.04), transparent 58%)"
    ].join(", "),
    boxShadow: [
      "inset 3px 0 0 rgba(var(--edusquirrel-diag-error), var(--edusquirrel-diag-rail-alpha))",
      "inset 0 1px 0 rgba(var(--edusquirrel-diag-error), 0.13)",
      "inset 0 -1px 0 rgba(var(--edusquirrel-diag-error), 0.08)"
    ].join(", ")
  },
  ".cm-edusquirrel-diagnostic-soft": {
    backgroundImage: [
      "linear-gradient(90deg, rgba(var(--edusquirrel-diag-soft), calc(var(--edusquirrel-diag-line-alpha) - 0.04)), rgba(var(--edusquirrel-diag-soft), var(--edusquirrel-diag-line-fade)) 42%, transparent 82%)",
      "linear-gradient(180deg, rgba(255,255,255,0.035), transparent 58%)"
    ].join(", "),
    boxShadow: [
      "inset 3px 0 0 rgba(var(--edusquirrel-diag-soft), 0.76)",
      "inset 0 1px 0 rgba(var(--edusquirrel-diag-soft), 0.09)",
      "inset 0 -1px 0 rgba(var(--edusquirrel-diag-soft), 0.05)"
    ].join(", ")
  },
  ".cm-edusquirrel-diagnostic::before": {
    content: "\"\"",
    position: "absolute",
    left: "0",
    top: "4px",
    bottom: "4px",
    width: "3px",
    borderRadius: "0 3px 3px 0",
    backgroundColor: "rgba(var(--edusquirrel-diag-warning), 0.95)",
    filter: "drop-shadow(0 0 5px rgba(var(--edusquirrel-diag-warning), 0.34))",
    pointerEvents: "none"
  },
  ".cm-edusquirrel-diagnostic-error::before": {
    backgroundColor: "rgba(var(--edusquirrel-diag-error), 0.98)",
    filter: "drop-shadow(0 0 6px rgba(var(--edusquirrel-diag-error), 0.38))"
  },
  ".cm-edusquirrel-diagnostic-soft::before": {
    backgroundColor: "rgba(var(--edusquirrel-diag-soft), 0.86)",
    filter: "drop-shadow(0 0 5px rgba(var(--edusquirrel-diag-soft), 0.26))"
  },
  ".cm-edusquirrel-diagnostic-mark": {
    borderRadius: "3px",
    backgroundColor: "rgba(var(--edusquirrel-diag-warning), var(--edusquirrel-diag-mark-alpha))",
    boxShadow: "0 0 0 1px rgba(var(--edusquirrel-diag-warning), 0.10)",
    textDecorationLine: "underline",
    textDecorationStyle: "wavy",
    textDecorationThickness: "1px",
    textUnderlineOffset: "3px",
    textDecorationColor: "rgba(var(--edusquirrel-diag-warning), 0.96)"
  },
  ".cm-edusquirrel-diagnostic-mark-error": {
    backgroundColor: "rgba(var(--edusquirrel-diag-error), calc(var(--edusquirrel-diag-mark-alpha) + 0.03))",
    boxShadow: "0 0 0 1px rgba(var(--edusquirrel-diag-error), 0.12)",
    textDecorationColor: "rgba(var(--edusquirrel-diag-error), 0.96)"
  },
  ".cm-edusquirrel-diagnostic-mark-soft": {
    backgroundColor: "rgba(var(--edusquirrel-diag-soft), calc(var(--edusquirrel-diag-mark-alpha) - 0.02))",
    boxShadow: "0 0 0 1px rgba(var(--edusquirrel-diag-soft), 0.10)",
    textDecorationColor: "rgba(var(--edusquirrel-diag-soft), 0.86)"
  }
});

const clamp = (value, min, max) => Math.max(min, Math.min(max, value));

const normalizeDiagnostics = (diagnostics, doc) => {
  if (!Array.isArray(diagnostics) || doc.lines < 1) {
    return [];
  }

  return diagnostics
    .map((item) => {
      const line = Number(item?.line);
      if (!Number.isFinite(line)) {
        return null;
      }
      const startLine = clamp(Math.floor(line), 1, doc.lines);
      const rawEndLine = Number(item?.endLine);
      const endLine = Number.isFinite(rawEndLine)
        ? clamp(Math.floor(rawEndLine), startLine, doc.lines)
        : startLine;
      const severity = String(item?.severity ?? "warning").toLowerCase();
      const safeSeverity =
        severity === "error" ? "error" :
        severity === "soft" || severity === "info" ? "soft" :
        "warning";

      return {
        line: startLine,
        endLine,
        fromCh: Number.isFinite(Number(item?.fromCh)) ? Math.max(0, Math.floor(Number(item.fromCh))) : null,
        toCh: Number.isFinite(Number(item?.toCh)) ? Math.max(0, Math.floor(Number(item.toCh))) : null,
        severity: safeSeverity,
        message: String(item?.message ?? "")
      };
    })
    .filter(Boolean);
};

const buildDiagnosticDecorations = (state, diagnostics) => {
  const ranges = [];

  for (const diagnostic of normalizeDiagnostics(diagnostics, state.doc)) {
    for (let lineNr = diagnostic.line; lineNr <= diagnostic.endLine; lineNr += 1) {
      const line = state.doc.line(lineNr);
      const severityClass =
        diagnostic.severity === "error" ? " cm-edusquirrel-diagnostic-error" :
        diagnostic.severity === "soft" ? " cm-edusquirrel-diagnostic-soft" :
        "";
      ranges.push(Decoration.line({
        class: `cm-edusquirrel-diagnostic${severityClass}`,
        attributes: {
          ...(diagnostic.message ? {title: diagnostic.message} : {}),
          "data-diagnostic-severity": diagnostic.severity
        }
      }).range(line.from));
    }

    if (diagnostic.fromCh !== null && diagnostic.toCh !== null && diagnostic.endLine === diagnostic.line) {
      const line = state.doc.line(diagnostic.line);
      const from = clamp(line.from + diagnostic.fromCh, line.from, line.to);
      const to = clamp(line.from + diagnostic.toCh, from, line.to);
      if (to > from) {
        const severityClass =
          diagnostic.severity === "error" ? " cm-edusquirrel-diagnostic-mark-error" :
          diagnostic.severity === "soft" ? " cm-edusquirrel-diagnostic-mark-soft" :
          "";
        ranges.push(Decoration.mark({
          class: `cm-edusquirrel-diagnostic-mark${severityClass}`,
          attributes: {
            ...(diagnostic.message ? {title: diagnostic.message} : {}),
            "data-diagnostic-severity": diagnostic.severity
          }
        }).range(from, to));
      }
    }
  }

  return Decoration.set(ranges, true);
};

const diagnosticField = StateField.define({
  create() {
    return Decoration.none;
  },
  update(decorations, transaction) {
    let next = decorations.map(transaction.changes);
    for (const effect of transaction.effects) {
      if (effect.is(setDiagnosticsEffect)) {
        next = buildDiagnosticDecorations(transaction.state, effect.value);
      }
    }
    return next;
  },
  provide: (field) => EditorView.decorations.from(field)
});

const indentWithSpaces = ({state, dispatch}) => {
  const {from, to, empty} = state.selection.main;
  if (!empty) {
    return indentMore({state, dispatch});
  }
  dispatch(state.update({
    changes: {from, to, insert: INDENT_SPACES},
    selection: {anchor: from + INDENT_SPACES.length}
  }));
  return true;
};

const editorTheme = EditorView.theme({
  ".cm-scroller": {
    fontFamily: "var(--code-font-family, var(--font-mono, 'Fira Code', 'JetBrains Mono', monospace))",
    fontSize: "var(--code-font-size, 14px)",
    lineHeight: "1.5"
  },
  ".cm-indent-markers": {
    "--indent-marker-bg-color":     "var(--cm-indent-color,        rgba(255,255,255,0.10))",
    "--indent-marker-active-bg-color": "var(--cm-indent-active-color, rgba(255,255,255,0.28))"
  },
  ".cm-todo-token": {
    color: "var(--color-accent-error) !important",
    fontWeight: "700"
  },
  ".cm-todo-token *": {
    color: "var(--color-accent-error) !important",
    fontWeight: "700"
  },
  /* Identifier/variable names and brackets without TODO */
  ".cm-plain-name": {
    color: "var(--color-text-inverse) !important"
  },
  ".cm-plain-name *": {
    color: "var(--color-text-inverse) !important"
  },
  /* Keep comments grey (oneDark stone) even if other overrides compete */
  ".cm-comment": {
    color: "var(--color-gray-7) !important",
    fontStyle: "italic"
  },
  ".cm-comment *": {
    color: "var(--color-gray-7) !important"
  },
  /* Calls / member access / Arduino constants — light blue instead of oneDark coral red */
  ".cm-accent-name": {
    color: "var(--color-blue-1) !important"
  },
  ".cm-accent-name *": {
    color: "var(--color-blue-1) !important"
  },
  /* Hat / green-flag call: start of a new Snap script. */
  ".cm-receive-go": {
    color: "var(--color-green-2) !important",
    fontWeight: "700"
  },
  ".cm-receive-go *": {
    color: "var(--color-green-2) !important",
    fontWeight: "700"
  }
});

/** Keywords that must keep their oneDark keyword colors. */
const RESERVED_IDENTIFIERS = new Set([
  "if", "else", "elif", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
  "int", "void", "char", "float", "double", "long", "short", "bool", "boolean", "byte", "word", "string",
  "const", "static", "unsigned", "signed", "struct", "class", "public", "private", "protected",
  "true", "false", "True", "False", "NULL", "nullptr", "None", "sizeof", "typedef", "enum", "volatile",
  "def", "import", "from", "as", "pass", "and", "or", "not", "in", "is", "with", "try", "except",
  "finally", "raise", "yield", "lambda", "global", "nonlocal", "assert", "async", "await",
  "self", "cls", "new", "delete", "this", "using", "namespace", "template", "typename", "virtual",
  "override", "inline", "extern", "auto", "include", "define", "ifdef", "ifndef", "endif"
]);

/** Arduino / API constants that should read as purple accents, not coral red. */
const ACCENT_IDENTIFIERS = new Set([
  "HIGH", "LOW", "INPUT", "OUTPUT", "INPUT_PULLUP", "LED_BUILTIN"
]);

const IDENTIFIER_PATTERN = /\b[A-Za-z_][A-Za-z0-9_]*\b/g;
const BRACKET_PATTERN = /[{}[\]()]/g;

const nextNonSpaceChar = (doc, pos) => {
  const slice = doc.sliceString(pos, Math.min(pos + 32, doc.length));
  const match = /^\s*(.)/.exec(slice);
  return match ? match[1] : "";
};

const isInCommentOrString = (state, pos) => {
  let node = syntaxTree(state).resolveInner(pos, -1);
  for (let cur = node; cur; cur = cur.parent) {
    const name = cur.name;
    if (
      name === "LineComment" ||
      name === "BlockComment" ||
      name === "Comment" ||
      name === "String" ||
      name === "CharLiteral" ||
      name.includes("Comment") ||
      name.includes("String")
    ) {
      return true;
    }
  }
  return false;
};

const buildIdentifierDecorations = (view) => {
  const ranges = [];
  const doc = view.state.doc;
  const state = view.state;
  for (const {from, to} of view.visibleRanges) {
    const text = doc.sliceString(from, to);

    IDENTIFIER_PATTERN.lastIndex = 0;
    let match;
    while ((match = IDENTIFIER_PATTERN.exec(text)) !== null) {
      const word = match[0];
      if (RESERVED_IDENTIFIERS.has(word)) {
        continue;
      }
      const start = from + match.index;
      const end = start + word.length;
      if (isInCommentOrString(state, start)) {
        continue;
      }
      const isTodo = word.includes("TODO");
      const nextChar = nextNonSpaceChar(doc, end);
      const isCallOrMember = nextChar === "(" || nextChar === ".";
      const isAccentConstant = ACCENT_IDENTIFIERS.has(word);

      if (isTodo) {
        ranges.push(Decoration.mark({class: "cm-todo-token"}).range(start, end));
        continue;
      }

      if (word === "receive_go" && nextChar === "(") {
        ranges.push(Decoration.mark({class: "cm-receive-go"}).range(start, end));
        continue;
      }

      if (isCallOrMember || isAccentConstant) {
        ranges.push(Decoration.mark({class: "cm-accent-name"}).range(start, end));
        continue;
      }

      ranges.push(Decoration.mark({class: "cm-plain-name"}).range(start, end));
    }

    BRACKET_PATTERN.lastIndex = 0;
    while ((match = BRACKET_PATTERN.exec(text)) !== null) {
      const start = from + match.index;
      if (isInCommentOrString(state, start)) {
        continue;
      }
      ranges.push(Decoration.mark({class: "cm-plain-name"}).range(start, start + 1));
    }
  }
  return Decoration.set(ranges, true);
};

const identifierHighlightPlugin = ViewPlugin.fromClass(class {
  constructor(view) {
    this.decorations = buildIdentifierDecorations(view);
  }

  update(update) {
    if (update.docChanged || update.viewportChanged) {
      this.decorations = buildIdentifierDecorations(update.view);
    }
  }
}, {
  decorations: (value) => value.decorations
});

const languageExtension = (language) => {
  const normalized = String(language ?? "python").toLowerCase();
  if (normalized === "cpp" || normalized === "c" || normalized === "c++") {
    return cpp();
  }
  return python();
};

const sharedExtensions = [
  EditorState.tabSize.of(4),
  indentUnit.of(INDENT_SPACES),
  lineNumbers(),
  highlightActiveLineGutter(),
  history(),
  drawSelection(),
  foldGutter(),
  indentOnInput(),
  bracketMatching(),
  highlightActiveLine(),
  highlightSelectionMatches(),
  indentationMarkers({
    thickness: 1,
    highlightActiveBlock: true,
    hideFirstIndent: false
  }),
  syntaxHighlighting(defaultHighlightStyle, {fallback: true}),
  oneDark,
  keymap.of([
    {key: "Tab", run: indentWithSpaces, shift: indentLess},
    ...defaultKeymap,
    ...historyKeymap,
    ...foldKeymap,
    ...searchKeymap
  ]),
  diagnosticField,
  diagnosticTheme,
  editorTheme,
  identifierHighlightPlugin
];

const codeMirrorFacade = {
  createEditor: ({parent, doc = "", onDocChange, language = "python"}) => {
    let isProgrammaticUpdate = false;

    const state = EditorState.create({
      doc: replaceTabsWithSpaces(doc),
      extensions: [
        ...sharedExtensions,
        languageExtension(language),
        EditorView.updateListener.of((update) => {
          if (update.docChanged && !isProgrammaticUpdate && typeof onDocChange === "function") {
            onDocChange(update.state.doc.toString());
          }
        })
      ]
    });

    const view = new EditorView({state, parent});

    return {
      setDoc(newDoc) {
        const nextDoc = replaceTabsWithSpaces(newDoc ?? "");
        if (view.state.doc.toString() === nextDoc) {
          return;
        }
        isProgrammaticUpdate = true;
        view.dispatch({
          changes: {
            from: 0,
            to: view.state.doc.length,
            insert: nextDoc
          },
          effects: setDiagnosticsEffect.of([])
        });
        isProgrammaticUpdate = false;
      },
      getDoc() {
        return view.state.doc.toString();
      },
      setDiagnostics(diagnostics) {
        view.dispatch({
          effects: setDiagnosticsEffect.of(diagnostics ?? [])
        });
      },
      focus() {
        view.focus();
      },
      destroy() {
        view.destroy();
      }
    };
  }
};

globalThis.EduSquirrelCodeMirror = codeMirrorFacade;
globalThis.EduSquirrelCodeMirrorReady = Promise.resolve(codeMirrorFacade);
