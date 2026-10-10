package it.evadid.homepage.webElements.editor.code.codemirror

import it.evadid.homepage.webElements.editor.code.CodeMirrorEditor.{CodeMirrorHandle, EditorConfig}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

private[code] object CodeMirrorRuntime {
  import CodeMirrorApi.*
  private val indent = "    "
  private lazy val diagnostics = new CodeMirrorDiagnostics

  @JSExportTopLevel("EduSquirrelCodeMirrorScala")
  val facade: js.Object = {
    val value = js.Dynamic.literal(createEditor =
      ((config: EditorConfig) => createEditor(config)): js.Function1[EditorConfig, CodeMirrorHandle])
    js.Dynamic.global.globalThis.updateDynamic("EduSquirrelCodeMirrorScala")(value)
    if CodeMirrorApi.available then {
      js.Dynamic.global.globalThis.updateDynamic("EduSquirrelCodeMirror")(value)
      js.Dynamic.global.globalThis.updateDynamic("EduSquirrelCodeMirrorReady")(js.Promise.resolve(value))
    }
    value
  }

  private def indentWithSpaces(target: js.Object): Boolean = {
    val view = target.asInstanceOf[View]
    val selected = view.state.selection.main
    if !selected.empty then Libraries.indentMore(target)
    else {
      view.dispatch(view.state.update(js.Dynamic.literal(
        changes = js.Dynamic.literal(from = selected.from, to = selected.to, insert = indent),
        selection = js.Dynamic.literal(anchor = selected.from + indent.length))))
      true
    }
  }

  private lazy val sharedExtensions: js.Array[Extension] = js.Array(
    Libraries.EditorState.tabSize.of(4), Libraries.indentUnit.of(indent),
    Libraries.lineNumbers(), Libraries.highlightActiveLineGutter(), Libraries.drawSelection(),
    Libraries.foldGutter(), Libraries.indentOnInput(), Libraries.bracketMatching(),
    Libraries.highlightActiveLine(), Libraries.highlightSelectionMatches(),
    Libraries.indentationMarkers(js.Dynamic.literal(thickness = 1, highlightActiveBlock = true, hideFirstIndent = false)),
    Libraries.syntaxHighlighting(Libraries.defaultHighlightStyle, js.Dynamic.literal(fallback = true)),
    Libraries.keymap.of(js.Array[js.Object](js.Dynamic.literal(
      key = "Tab", run = ((target: js.Object) => indentWithSpaces(target)): js.Function1[js.Object, Boolean],
      shift = ((target: js.Object) => Libraries.indentLess(target)): js.Function1[js.Object, Boolean]
    )).concat(Libraries.defaultKeymap, Libraries.historyKeymap, Libraries.foldKeymap, Libraries.searchKeymap)),
    diagnostics.field, CodeMirrorHighlighting.extension
  )

  private def languageExtension(language: String): Extension = language match {
    case "java" => js.Array[Extension]()
    case "sql" => Libraries.sql(js.Dynamic.literal(dialect = Libraries.MySQL))
    case "cpp" | "c" | "c++" => Libraries.cpp()
    case _ => Libraries.python()
  }

  private def default(value: String, fallback: String): String =
    if value == null || js.isUndefined(value) then fallback else value

  def createEditor(config: EditorConfig): CodeMirrorHandle = {
    if !CodeMirrorApi.available then throw IllegalStateException("CodeMirror libraries are not loaded.")
    new Handle(config).asInstanceOf[CodeMirrorHandle]
  }

  private class Handle(config: EditorConfig) extends js.Object {
    private val language = default(config.language, "python").toLowerCase
    private val isJava = language == "java"
    private val initial = default(config.doc, "")
    private var javaSource = initial
    private var programmatic = false
    private var destroyed = false
    private val javaHistory = new Compartment
    private val theme = new Compartment
    private val editorLanguage = new Compartment
    private val followsPageTheme = config.parent.closest(".fd-page") != null
    private def currentTheme(): Extension =
      if followsPageTheme && dom.document.documentElement.getAttribute("data-theme") == "light" then js.Array[Extension]()
      else Libraries.oneDark
    private def prepare(source: String): String = if isJava then source else source.replace("\t", indent)
    private def text(source: String): js.Any = if isJava then CodeMirrorJavaLineEndings.text(source) else prepare(source)

    private val state = Libraries.EditorState.create(js.Dynamic.literal(
      doc = text(initial),
      extensions = js.Array[Extension](sharedExtensions,
        if isJava then js.Array[Extension](
          CodeMirrorJavaLineEndings.field.init((_: State) => CodeMirrorJavaLineEndings.parse(initial)),
          CodeMirrorJavaLineEndings.historyEffects, javaHistory.of(Libraries.history()))
        else Libraries.history(),
        theme.of(currentTheme()), editorLanguage.of(languageExtension(language)),
        Libraries.EditorView.updateListener.of((update: ViewUpdate) => {
          val metadataChanged = isJava &&
            (update.startState.field(CodeMirrorJavaLineEndings.field) ne update.state.field(CodeMirrorJavaLineEndings.field))
          if (update.docChanged || metadataChanged) && !programmatic then {
            val value = if isJava then CodeMirrorJavaLineEndings.source(update.state) else update.state.doc.toString()
            val changed = !isJava || value != javaSource
            if isJava then javaSource = value
            if changed && js.typeOf(config.onDocChange) == "function" then config.onDocChange(value)
          }
        }))))
    private val view = new EditorView(js.Dynamic.literal(state = state, parent = config.parent))
    view.dom.classList.add("edusquirrel-code-mirror")

    if isJava then Libraries.loadJavaModule().`then`[Unit]((module: JavaModule) => {
      if !destroyed then view.dispatch(js.Dynamic.literal(effects = editorLanguage.reconfigure(module.java())))
    }).`catch`(((error: scala.Any) => {
      if !destroyed then dom.console.warn("Java syntax support is unavailable; text editing remains available.", error.asInstanceOf[js.Any])
      (): Unit
    }): js.Function1[scala.Any, Unit | js.Thenable[Unit]])

    private val themeObserver = if followsPageTheme then Some(new dom.MutationObserver((_, _) =>
      if !destroyed then view.dispatch(js.Dynamic.literal(effects = theme.reconfigure(currentTheme()))))) else None
    themeObserver.foreach(_.observe(dom.document.documentElement,
      new dom.MutationObserverInit { attributes = true; attributeFilter = js.Array("data-theme") }))

    def setDoc(value: String): Unit = {
      val next = prepare(default(value, ""))
      if getDoc() == next then return
      programmatic = true
      try {
        if isJava then {
          val withoutHistory = view.state.update(js.Dynamic.literal(effects = javaHistory.reconfigure(js.Array[Extension]())))
          val restored = withoutHistory.state.update(js.Dynamic.literal(
            changes = js.Dynamic.literal(from = 0, to = withoutHistory.state.doc.length, insert = CodeMirrorJavaLineEndings.text(next)),
            effects = js.Array[js.Object](CodeMirrorJavaLineEndings.reset.of(CodeMirrorJavaLineEndings.parse(next)),
              diagnostics.effect.of(js.Array[js.Object]()), javaHistory.reconfigure(Libraries.history())),
            annotations = Libraries.Transaction.addToHistory.of(false)))
          view.dispatch(js.Array(withoutHistory, restored))
          javaSource = next
        } else view.dispatch(js.Dynamic.literal(
          changes = js.Dynamic.literal(from = 0, to = view.state.doc.length, insert = next),
          effects = diagnostics.effect.of(js.Array[js.Object]())))
      } finally programmatic = false
    }

    def getDoc(): String = if isJava then javaSource else view.state.doc.toString()
    def setDiagnostics(value: js.Array[js.Object]): Unit =
      view.dispatch(js.Dynamic.literal(effects = diagnostics.effect.of(
        if value == null || js.isUndefined(value) then js.Array[js.Object]() else value)))
    def focus(): Unit = view.focus()
    def destroy(): Unit = if !destroyed then {
      destroyed = true
      themeObserver.foreach(_.disconnect())
      view.destroy()
    }
  }
}
