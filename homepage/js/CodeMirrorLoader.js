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
  ".cm-edusquirrel-diagnostic": {
    backgroundImage: "linear-gradient(90deg, rgba(255, 190, 88, 0.24), rgba(255, 190, 88, 0.07) 42%, transparent 88%)",
    boxShadow: "inset 3px 0 0 rgba(255, 190, 88, 0.86)"
  },
  ".cm-edusquirrel-diagnostic-error": {
    backgroundImage: "linear-gradient(90deg, rgba(255, 96, 96, 0.28), rgba(255, 96, 96, 0.08) 42%, transparent 88%)",
    boxShadow: "inset 3px 0 0 rgba(255, 96, 96, 0.9)"
  },
  ".cm-edusquirrel-diagnostic-soft": {
    backgroundImage: "linear-gradient(90deg, rgba(98, 184, 255, 0.18), rgba(98, 184, 255, 0.06) 42%, transparent 88%)",
    boxShadow: "inset 3px 0 0 rgba(98, 184, 255, 0.74)"
  },
  ".cm-edusquirrel-diagnostic-mark": {
    borderBottom: "1px solid rgba(255, 190, 88, 0.95)",
    backgroundColor: "rgba(255, 190, 88, 0.12)",
    borderRadius: "2px"
  },
  ".cm-edusquirrel-diagnostic-mark-error": {
    borderBottomColor: "rgba(255, 96, 96, 0.95)",
    backgroundColor: "rgba(255, 96, 96, 0.14)"
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
        attributes: diagnostic.message ? {title: diagnostic.message} : undefined
      }).range(line.from));
    }

    if (diagnostic.fromCh !== null && diagnostic.toCh !== null && diagnostic.endLine === diagnostic.line) {
      const line = state.doc.line(diagnostic.line);
      const from = clamp(line.from + diagnostic.fromCh, line.from, line.to);
      const to = clamp(line.from + diagnostic.toCh, from, line.to);
      if (to > from) {
        const severityClass = diagnostic.severity === "error" ? " cm-edusquirrel-diagnostic-mark-error" : "";
        ranges.push(Decoration.mark({
          class: `cm-edusquirrel-diagnostic-mark${severityClass}`,
          attributes: diagnostic.message ? {title: diagnostic.message} : undefined
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
          }
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
