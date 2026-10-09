package it.evadid.homepage.webElements.editor.code

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.font.AppFont
import it.evadid.core.datastructures.language.AppLanguage.Python
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.homepage.webElements.editor.abstractions.SimpleWebEditor
import it.evadid.homepage.webElements.editor.config.CodeEditorConfig

import scala.scalajs.js

/** The regular Python editor with a line-aligned control-flow gutter before
  * the line numbers. Unsupported/incomplete programs remain editable.
  */
case class CodeMirrorEditorFlowchart(
    content: Var[String],
    onUserInput: String => Unit = _ => (),
    editorFont: Signal[AppFont] = Val(AppFont("JetBrains Mono", 14))
) extends SimpleWebEditor[String, CodeEditorConfig] {
  private val editor = CodeMirrorEditor(content, onUserInput, editorFont, Python,
    flowchart = Some(CodeMirrorEditorFlowchart.chartForDoc))

  def focus(): Unit = editor.focus()
  def currentDoc: Option[String] = editor.currentDoc
  def setDiagnostics(diagnostics: Seq[CodeMirrorEditor.Diagnostic]): Unit = editor.setDiagnostics(diagnostics)
  def clearDiagnostics(): Unit = editor.clearDiagnostics()
  override def getDomElement(): Element = editor.getDomElement()
  override def underlyingVar: Var[String] = content
  override def config: Val[CodeEditorConfig] = editor.config
}

object CodeMirrorEditorFlowchart {
  private[code] def chartForDoc(source: String): js.Object = {
    val chart = PythonFlowchartGutter.fromPython(source)
    js.Dynamic.literal(
      nodes = js.Array(chart.nodes.map(node => js.Dynamic.literal(
        line = node.line, kind = node.kind, depth = node.depth, label = node.label
      )) *),
      edges = js.Array(chart.edges.map(edge => js.Dynamic.literal(
        from = edge.from, to = edge.to, kind = edge.kind
      )) *),
      entries = js.Array(chart.entries *)
    )
  }
}
