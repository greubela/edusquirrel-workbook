/**********************
 * 1️⃣ Load CodeMirror v6
 **********************/
import { EditorView, basicSetup }
  from "https://esm.sh/@codemirror/basic-setup";
import { EditorState }
  from "https://esm.sh/@codemirror/state";
import { javascript }
  from "https://esm.sh/@codemirror/lang-javascript";

window.CodeMirror = {
  EditorView,
  EditorState,
  basicSetup,
  javascript
};

/**********************
 * 2️⃣ Load Algebrite
 **********************/
await import("https://unpkg.com/algebrite@1.4.0/dist/algebrite.bundle-for-browser.js");

/**********************
 * 3️⃣ Load Scala.js
 **********************/
await import("../../target/scala-3.3.3/scalajs-bundler/main/workbookapp-fastopt.js");
