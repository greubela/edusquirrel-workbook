package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer
import it.evadid.homepage.webElements.editor.code.program.ProgramAnalyzer.Status
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

/** The drawing of the current program, from its static analysis: the shared BeExpression's turtle commands,
  * computed in the worker for every language. Nothing is executed dynamically, so no run is needed.
  */
final class StaticTurtleDrawing(source: Signal[ProgrammingState], environment: ProgrammingEnvironment,
    analyzer: ProgramAnalyzer = ProgramAnalyzer.shared)
    extends HtmlAppElement {
  override def getDomElement(): Element = {
    div(cls := "static-turtle-drawing",
      child <-- analyzer.statusOf(source, environment).map {
        case Status.Ready(analysis) => analysis.drawing.fold(
          reason => p(cls := "static-turtle-drawing__unavailable", role := "status", reason),
          commands => TurtleJsxGraphRenderer.render(TurtleDrawingGrading.drawingScene(commands), "Turtle drawing"))
        case Status.Failed(reason) => p(cls := "static-turtle-drawing__unavailable", role := "status", reason)
        case Status.Pending => div(cls := "static-turtle-drawing__pending")
      })
  }
}
