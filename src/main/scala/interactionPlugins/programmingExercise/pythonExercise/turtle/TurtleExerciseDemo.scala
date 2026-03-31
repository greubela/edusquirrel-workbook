package interactionPlugins.programmingExercise.pythonExercise.turtle

import contentmanagement.webElements.HtmlAppElement
import contentmanagement.webElements.genericHtmlElements.canvas.{SvgCanvas, WebCanvas}
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment
import workbook.htmlElements.basic.HtmlButtonElement
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

case class TurtleExerciseDemo() extends HtmlAppElement{

  val pyodideEnvironment: PyodideEnvironment = ??? // create and add turtle module to pyodideEnvironment
  val TurtleBackend: TurtleBackend = ??? // todo: create an instance etc.

  val inputEditor = ??? // CodeMirrorEditor, to the left
  val startButton: HtmlButtonElement = ??? // run the python code between both Elements
  val outputCanvas: WebCanvas = WebCanvas(1000,1000) // output of any turtle drawings
  val outputStdOut: Element = div() // text from std-out appears here
  val outputStdErr: Element = div() // text from std-err appears here
  val globalVariables: Element = div() // global variables from pyodideEnvironment as json or table

  override def getDomElement(): L.Element = ??? // editor left, button middle,

}
