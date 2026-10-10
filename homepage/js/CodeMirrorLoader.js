import {Compartment, EditorState, MapMode, StateEffect, StateField, Text, Transaction} from "https://esm.sh/@codemirror/state@6.5.2";
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
import {defaultKeymap, history, historyKeymap, indentLess, indentMore, invertedEffects} from "https://esm.sh/@codemirror/commands@6.8.1?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";
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
import {sql, MySQL} from "https://esm.sh/@codemirror/lang-sql@6.9.0?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3,@codemirror/autocomplete@6.18.4";
import {cpp} from "https://esm.sh/@codemirror/lang-cpp@6.0.2?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";
import {oneDark} from "https://esm.sh/@codemirror/theme-one-dark@6.1.3?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";
import {indentationMarkers} from "https://esm.sh/@replit/codemirror-indentation-markers@6.5.3?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3";

let javaModulePromise;
const loadJavaModule = () => {
  if (!javaModulePromise) {
    javaModulePromise = import("https://esm.sh/@codemirror/lang-java@6.0.2?deps=@codemirror/state@6.5.2,@codemirror/view@6.38.6,@codemirror/language@6.11.3")
      .catch(error => {
        javaModulePromise = undefined;
        throw error;
      });
  }
  return javaModulePromise;
};

globalThis.EduSquirrelCodeMirrorLibraries = Object.freeze({
  Compartment, EditorState, MapMode, StateEffect, StateField, Text, Transaction,
  EditorView, Decoration, keymap, drawSelection, highlightActiveLine, lineNumbers,
  highlightActiveLineGutter, ViewPlugin, defaultKeymap, history, historyKeymap,
  indentLess, indentMore, invertedEffects, bracketMatching, foldGutter, foldKeymap,
  indentUnit, indentOnInput, syntaxHighlighting, defaultHighlightStyle, syntaxTree,
  highlightSelectionMatches, searchKeymap, python, sql, MySQL, cpp, oneDark,
  indentationMarkers, loadJavaModule
});

globalThis.EduSquirrelCodeMirror = globalThis.EduSquirrelCodeMirrorScala;
globalThis.EduSquirrelCodeMirrorReady = Promise.resolve(globalThis.EduSquirrelCodeMirror);
