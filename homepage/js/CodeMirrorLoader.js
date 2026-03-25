import {EditorState} from "https://cdn.jsdelivr.net/npm/@codemirror/state@6.5.2/+esm";
import {
  EditorView,
  keymap,
  drawSelection,
  highlightActiveLine,
  lineNumbers,
  highlightActiveLineGutter
} from "https://cdn.jsdelivr.net/npm/@codemirror/view@6.38.6/+esm";
import {defaultKeymap, history, historyKeymap, indentWithTab} from "https://cdn.jsdelivr.net/npm/@codemirror/commands@6.8.1/+esm";
import {
  bracketMatching,
  defaultHighlightStyle,
  foldGutter,
  foldKeymap,
  indentUnit,
  indentOnInput,
  syntaxHighlighting
} from "https://cdn.jsdelivr.net/npm/@codemirror/language@6.11.3/+esm";
import {highlightSelectionMatches, searchKeymap} from "https://cdn.jsdelivr.net/npm/@codemirror/search@6.5.11/+esm";
import {python} from "https://cdn.jsdelivr.net/npm/@codemirror/lang-python@6.2.1/+esm";

const editorTheme = EditorView.theme({
  "&": {
    height: "100%",
    minHeight: "16rem",
    border: "1px solid #d0d7de",
    borderRadius: "10px"
  },
  ".cm-scroller": {
    fontFamily: "var(--font-mono, 'Fira Code', 'JetBrains Mono', monospace)",
    lineHeight: "1.5"
  },
  ".cm-content": {
    padding: "12px 0",
    textAlign: "left"
  },
  ".cm-gutters": {
    backgroundColor: "#f6f8fa",
    color: "#57606a",
    border: "none",
    borderTopLeftRadius: "10px",
    borderBottomLeftRadius: "10px"
  },
  ".cm-line": {
    textAlign: "left"
  },
  ".cm-activeLine": {
    backgroundColor: "#f6f8fa"
  },
  ".cm-activeLineGutter": {
    backgroundColor: "#eaf2ff",
    color: "#0969da"
  },
  ".cm-selectionBackground, &.cm-focused .cm-selectionBackground, ::selection": {
    backgroundColor: "#cce0ff"
  },
  ".cm-cursor, .cm-dropCursor": {
    borderLeftColor: "#0969da"
  },
  ".cm-tooltip": {
    border: "1px solid #d0d7de",
    backgroundColor: "#ffffff"
  },
  ".cm-panels": {
    backgroundColor: "#ffffff",
    color: "#24292f"
  },
  ".cm-matchingBracket": {
    backgroundColor: "#ddf4ff",
    outline: "1px solid #54aeff"
  }
});

const baseExtensions = [
  EditorState.tabSize.of(4),
  indentUnit.of("    "),
  lineNumbers(),
  highlightActiveLineGutter(),
  history(),
  drawSelection(),
  foldGutter(),
  indentOnInput(),
  bracketMatching(),
  highlightActiveLine(),
  highlightSelectionMatches(),
  python(),
  syntaxHighlighting(defaultHighlightStyle, {fallback: true}),
  keymap.of([
    indentWithTab,
    ...defaultKeymap,
    ...historyKeymap,
    ...foldKeymap,
    ...searchKeymap
  ]),
  editorTheme
];

const codeMirrorFacade = {
  createEditor: ({parent, doc = "", onDocChange}) => {
    let isProgrammaticUpdate = false;

    const state = EditorState.create({
      doc,
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
        const nextDoc = newDoc ?? "";
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
