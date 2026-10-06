package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.{FullscreenLifecycle, HtmlAppElement}
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.{SnapCodeEditor, SnapCodeEditorConfig}
import it.evadid.workbook.elements.interactionElements.programming.*

import scala.concurrent.Future

/** Full-screen programming editor that owns one polymorphic state and presents
  * a suitable editor for every supported representation.
  */
final class EvaEditor(
    val state: Var[ProgrammingState],
    snapConfig: SnapCodeEditorConfig,
    onStateEdited: ProgrammingState => Unit = _ => ()
) extends HtmlAppElement with FullscreenLifecycle {

  private val activeTab = Var(EvaEditor.tabFor(state.now()))
  private val snapState = Var(state.now().toSnapXml)
  private val pythonState = Var(state.now().toPython.code)
  private val javaState = Var(state.now().toJava.code)

  private def publish(next: ProgrammingState): Unit =
    state.set(next)
    onStateEdited(next)

  private def receive(next: ProgrammingState): Unit =
    val nextTab =
      if activeTab.now() == EvaEditor.Tab.JavaFunctionEditor &&
          next.isInstanceOf[ProgrammingStateJavaString] then EvaEditor.Tab.JavaFunctionEditor
      else EvaEditor.tabFor(next)
    // Re-emitting the selected tab replaces Laminar's child node. Avoid doing
    // that for edits in the current CodeMirror instance, or it loses focus.
    if activeTab.now() != nextTab then activeTab.set(nextTab)
    nextTab match
      case EvaEditor.Tab.Snap => snapState.set(next.toSnapXml)
      case EvaEditor.Tab.Python => pythonState.set(next.toPython.code)
      case EvaEditor.Tab.Java => javaState.set(next.toJava.code)
      case EvaEditor.Tab.JavaFunctionEditor => javaState.set(next.toJava.code)

  private val snapEditor = SnapCodeEditor(
    snapState,
    snapConfig,
    onStateEdited = next => publish(next)
  )
  private val pythonEditor = CodeMirrorEditor(
    pythonState,
    code => publish(ProgrammingStatePythonString(code)),
    language = AppLanguage.Python
  )
  private val javaEditor = CodeMirrorEditor(
    javaState,
    code => publish(ProgrammingStateJavaString(code)),
    language = AppLanguage.Java
  )
  private var javaFunctionEditor = new JavaFunctionBasedEditor(state, onStateEdited = publish)

  /** Small preview retained by the workbook card. */
  val previewCanvas: Element = snapEditor.previewCanvas

  /** The representation currently owned by the editor. All derived behavior starts here. */
  def currentState(): ProgrammingState = state.now()

  def getCurrentTurtleCommands(): Future[List[TurtleCommand[Double]]] =
    snapEditor.getCurrentTurtleCommands()

  private def select(tab: EvaEditor.Tab): Unit =
    if activeTab.now() == tab then return
    val current = state.now()
    val leavingFunctionEditor =
      activeTab.now() == EvaEditor.Tab.JavaFunctionEditor &&
        tab != EvaEditor.Tab.JavaFunctionEditor
    val scriptState =
      if leavingFunctionEditor then
        ProgrammingStateJavaString(EvaEditor.scriptFromFunctionEditorSource(current.toJava.code))
      else current
    tab match
      case EvaEditor.Tab.Snap =>
        val next =
          if leavingFunctionEditor then scriptState.toSnapXml
          else current.toSnapXml
        snapState.set(next)
        if leavingFunctionEditor then publish(next)
      case EvaEditor.Tab.Python =>
        val next =
          if leavingFunctionEditor then scriptState.toPython
          else current.toPython
        pythonState.set(next.toPython.code)
        if leavingFunctionEditor then publish(next)
      case EvaEditor.Tab.Java =>
        val next = if leavingFunctionEditor then scriptState else current
        javaState.set(next.toJava.code)
        if leavingFunctionEditor then publish(next)
      case EvaEditor.Tab.JavaFunctionEditor =>
        val source = EvaEditor.functionEditorSource(current.toJava.code)
        val next = ProgrammingStateJavaString(source)
        publish(next)
        javaFunctionEditor = new JavaFunctionBasedEditor(state, onStateEdited = publish)
    activeTab.set(tab)

  override def getDomElement(): Element =
    div(
      cls := "eva-editor",
      onMountCallback { ctx =>
        state.signal.changes.foreach(receive)(using ctx.owner)
      },
      div(
        cls := "eva-editor__tabs",
        EvaEditor.Tab.values.map { tab =>
          button(
            typ := "button",
            cls <-- activeTab.signal.map(active =>
              if active == tab then "eva-editor__tab eva-editor__tab--active" else "eva-editor__tab"
            ),
            aria.selected <-- activeTab.signal.map(_ == tab),
            tab.label,
            onClick --> (_ => select(tab))
          )
        }
      ),
      div(
        cls := "eva-editor__content",
        child <-- activeTab.signal.map {
          case EvaEditor.Tab.Snap => snapEditor.getDomElement()
          case EvaEditor.Tab.Python => pythonEditor.getDomElement()
          case EvaEditor.Tab.Java => javaEditor.getDomElement()
          case EvaEditor.Tab.JavaFunctionEditor => javaFunctionEditor.getDomElement()
        }
      )
    )

  override def onFullscreenOpen(): Unit =
    if activeTab.now() == EvaEditor.Tab.Snap then snapEditor.onFullscreenOpen()

  override def onFullscreenClose(): Unit = snapEditor.onFullscreenClose()
  override def dismissOnOutsideClick: Boolean = false
}

object EvaEditor {
  private val JavaClassDeclaration =
    """(?m)^\s*(?:(?:public|protected|private|abstract|final)\s+)*class\s+[A-Za-z_$][A-Za-z0-9_$]*\b""".r

  enum Tab(val label: String) {
    case Snap extends Tab("Snap!")
    case Python extends Tab("Python")
    case Java extends Tab("Java")
    case JavaFunctionEditor extends Tab("Java (Function Editor)")
  }

  private[code] def tabFor(state: ProgrammingState): Tab = state match
    case _: ProgrammingStatePythonString => Tab.Python
    case _: ProgrammingStateJavaString => Tab.Java
    case _ => Tab.Snap

  private[code] def functionEditorSource(script: String): String =
    if JavaClassDeclaration.findFirstIn(script).nonEmpty then script
    else
      val indented = script.linesIterator.map(line => s"    $line").mkString("\n")
      s"""public class EvaProgram {
         |  public static void main(String[] args) {
         |$indented
         |  }
         |}
         |""".stripMargin

  private[code] def scriptFromFunctionEditorSource(source: String): String =
    val mainSignature =
      """\bstatic\s+void\s+main\s*\([^)]*\)\s*\{""".r
    mainSignature.findFirstMatchIn(source) match
      case None => source
      case Some(signature) =>
        val openBrace = signature.end - 1
        matchingBrace(source, openBrace) match
          case Some(closeBrace) =>
            source.substring(openBrace + 1, closeBrace)
              .linesIterator
              .map(_.replaceFirst("^\\s{0,4}", ""))
              .mkString("\n")
              .trim
          case None => source

  private def matchingBrace(source: String, openBrace: Int): Option[Int] =
    var depth = 0
    var i = openBrace
    var quote: Char = 0
    var escaped = false
    var lineComment = false
    var blockComment = false
    while i < source.length do
      val ch = source.charAt(i)
      val next = if i + 1 < source.length then source.charAt(i + 1) else 0
      if lineComment then
        if ch == '\n' then lineComment = false
      else if blockComment then
        if ch == '*' && next == '/' then
          blockComment = false
          i += 1
      else if quote != 0 then
        if escaped then escaped = false
        else if ch == '\\' then escaped = true
        else if ch == quote then quote = 0
      else if ch == '/' && next == '/' then
        lineComment = true
        i += 1
      else if ch == '/' && next == '*' then
        blockComment = true
        i += 1
      else if ch == '"' || ch == '\'' then quote = ch
      else if ch == '{' then depth += 1
      else if ch == '}' then
        depth -= 1
        if depth == 0 then return Some(i)
      i += 1
    None

}
