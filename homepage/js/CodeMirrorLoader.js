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
  HighlightStyle,
  bracketMatching,
  foldGutter,
  foldKeymap,
  indentOnInput,
  syntaxHighlighting
} from "https://cdn.jsdelivr.net/npm/@codemirror/language@6.11.3/+esm";
import {highlightSelectionMatches, searchKeymap} from "https://cdn.jsdelivr.net/npm/@codemirror/search@6.5.11/+esm";
import {python} from "https://cdn.jsdelivr.net/npm/@codemirror/lang-python@6.2.1/+esm";
import {oneDark} from "https://cdn.jsdelivr.net/npm/@codemirror/theme-one-dark@6.1.3/+esm";
import {tags} from "https://cdn.jsdelivr.net/npm/@lezer/highlight@1.2.1/+esm";

const pythonHighlightStyle = HighlightStyle.define([
  {tag: tags.keyword, color: "#c678dd", fontWeight: "600"},
  {tag: [tags.name, tags.deleted, tags.character, tags.propertyName, tags.macroName], color: "#e06c75"},
  {tag: [tags.function(tags.variableName), tags.labelName], color: "#61afef"},
  {tag: [tags.color, tags.constant(tags.name), tags.standard(tags.name)], color: "#d19a66"},
  {tag: [tags.definition(tags.name), tags.separator], color: "#e5c07b"},
  {tag: tags.className, color: "#e5c07b", fontWeight: "600"},
  {tag: [tags.number, tags.changed, tags.annotation, tags.modifier, tags.self, tags.namespace], color: "#d19a66"},
  {tag: tags.typeName, color: "#56b6c2"},
  {tag: [tags.operator, tags.operatorKeyword], color: "#56b6c2"},
  {tag: tags.tagName, color: "#e06c75"},
  {tag: tags.attributeName, color: "#d19a66"},
  {tag: tags.regexp, color: "#56b6c2"},
  {tag: tags.string, color: "#98c379"},
  {tag: tags.special(tags.string), color: "#56b6c2"},
  {tag: [tags.meta, tags.comment], color: "#7f848e", fontStyle: "italic"},
  {tag: tags.link, color: "#7fbaff", textDecoration: "underline"},
  {tag: tags.heading, fontWeight: "700", color: "#e06c75"},
  {tag: [tags.atom, tags.bool, tags.special(tags.variableName)], color: "#d19a66"},
  {tag: tags.invalid, color: "#ffffff", backgroundColor: "#e05252"}
]);

const editorTheme = EditorView.theme({
  "&": {
    height: "100%",
    minHeight: "16rem",
    backgroundColor: "#282c34",
    color: "#abb2bf",
    border: "1px solid rgba(255, 255, 255, 0.08)",
    borderRadius: "10px"
  },
  ".cm-scroller": {
    fontFamily: "var(--font-mono, 'Fira Code', 'JetBrains Mono', monospace)",
    lineHeight: "1.5"
  },
  ".cm-content": {
    caretColor: "#61afef",
    padding: "12px 0"
  },
  ".cm-gutters": {
    backgroundColor: "#21252b",
    color: "#5c6370",
    border: "none",
    borderTopLeftRadius: "10px",
    borderBottomLeftRadius: "10px"
  },
  ".cm-activeLine": {
    backgroundColor: "rgba(255, 255, 255, 0.04)"
  },
  ".cm-activeLineGutter": {
    backgroundColor: "rgba(97, 175, 239, 0.12)",
    color: "#7fbaff"
  },
  ".cm-selectionBackground, &.cm-focused .cm-selectionBackground, ::selection": {
    backgroundColor: "rgba(97, 175, 239, 0.28)"
  },
  ".cm-cursor, .cm-dropCursor": {
    borderLeftColor: "#61afef"
  },
  ".cm-tooltip": {
    border: "1px solid rgba(255, 255, 255, 0.1)",
    backgroundColor: "#21252b"
  },
  ".cm-panels": {
    backgroundColor: "#21252b",
    color: "#abb2bf"
  },
  ".cm-matchingBracket": {
    backgroundColor: "rgba(86, 182, 194, 0.18)",
    outline: "1px solid rgba(86, 182, 194, 0.45)"
  }
});

const baseExtensions = [
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
  syntaxHighlighting(pythonHighlightStyle),
  keymap.of([
    indentWithTab,
    ...defaultKeymap,
    ...historyKeymap,
    ...foldKeymap,
    ...searchKeymap
  ]),
  oneDark,
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
