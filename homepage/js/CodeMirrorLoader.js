import {EditorState, StateEffect, StateField} from "https://esm.sh/@codemirror/state@6.5.2";
import {
  EditorView,
  Decoration,
  keymap,
  drawSelection,
  highlightActiveLine,
  lineNumbers,
  highlightActiveLineGutter
} from "https://esm.sh/@codemirror/view@6.38.6?deps=@codemirror/state@6.5.2";
import {defaultKeymap, history, historyKeymap, indentLess, indentMore} from "https://esm.sh/@codemirror/commands@6.8.1?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";
import {
  bracketMatching,
  foldGutter,
  foldKeymap,
  indentUnit,
  indentOnInput,
  syntaxHighlighting,
  defaultHighlightStyle
} from "https://esm.sh/@codemirror/language@6.11.3?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6";
import {highlightSelectionMatches, searchKeymap} from "https://esm.sh/@codemirror/search@6.5.11?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6";
import {python} from "https://esm.sh/@codemirror/lang-python@6.2.1?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3,@codemirror/autocomplete@6.18.4";
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
  }
});

const baseExtensions = [
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
  python(),
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
  editorTheme
];

const codeMirrorFacade = {
  createEditor: ({parent, doc = "", onDocChange}) => {
    let isProgrammaticUpdate = false;

    const state = EditorState.create({
      doc: replaceTabsWithSpaces(doc),
      extensions: [
        ...baseExtensions,
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
