import {EditorView, keymap, highlightActiveLine, drawSelection} from "https://cdn.jsdelivr.net/npm/@codemirror/view@6.23.0/+esm";
import {EditorState} from "https://cdn.jsdelivr.net/npm/@codemirror/state@6.3.1/+esm";
import {history, historyKeymap, defaultKeymap, indentWithTab} from "https://cdn.jsdelivr.net/npm/@codemirror/commands@6.2.3/+esm";
import {lineNumbers, highlightActiveLineGutter} from "https://cdn.jsdelivr.net/npm/@codemirror/gutter@6.2.0/+esm";
import {defaultHighlightStyle} from "https://cdn.jsdelivr.net/npm/@codemirror/highlight@6.5.2/+esm";
import {syntaxHighlighting, indentOnInput} from "https://cdn.jsdelivr.net/npm/@codemirror/language@6.10.1/+esm";
import {highlightSelectionMatches} from "https://cdn.jsdelivr.net/npm/@codemirror/search@6.5.5/+esm";
import {python} from "https://cdn.jsdelivr.net/npm/@codemirror/lang-python@6.1.3/+esm";

const editorTheme = EditorView.theme({
    "&": {
        height: "100%",
        backgroundColor: "var(--color-interaction-background)",
        color: "var(--color-text-primary)"
    },
    ".cm-content": {
        fontFamily: "var(--font-mono, 'Fira Code', monospace)",
        fontSize: "var(--fontsize-code, 0.95rem)"
    },
    ".cm-lineNumbers": {
        backgroundColor: "var(--color-interaction-background)",
        color: "var(--color-text-tertiary)"
    }
});

const basicExtensions = [
    lineNumbers(),
    highlightActiveLineGutter(),
    history(),
    drawSelection(),
    EditorState.allowMultipleSelections.of(true),
    syntaxHighlighting(defaultHighlightStyle, {fallback: true}),
    highlightActiveLine(),
    highlightSelectionMatches(),
    indentOnInput(),
    keymap.of([
        ...defaultKeymap,
        ...historyKeymap,
        indentWithTab
    ]),
    python(),
    editorTheme
];

globalThis.EduSquirrelCodeMirror = {
    createEditor: ({parent, doc, onDocChange}) => {
        let isProgrammaticUpdate = false;

        const state = EditorState.create({
            doc,
            extensions: [
                ...basicExtensions,
                EditorView.updateListener.of(update => {
                    if (update.docChanged && !isProgrammaticUpdate) {
                        onDocChange(update.state.doc.toString());
                    }
                })
            ]
        });

        const view = new EditorView({
            state,
            parent
        });

        return {
            setDoc(newDoc) {
                if (view.state.doc.toString() === newDoc) {
                    return;
                }
                isProgrammaticUpdate = true;
                view.dispatch({
                    changes: {
                        from: 0,
                        to: view.state.doc.length,
                        insert: newDoc
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
