package scala.interactionPlugins.turtleEnvironment

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import workbook.model.display.InteractionComponent.InteractionComponentWithReactiveVars

class TurtleScaffoldingStateComponent(stateVar: Var[TurtleScaffoldingState]) extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      cls := "turtle-scaffolding-state",
      h3("Current program"),
      pre(child.text <-- stateVar.signal.map(_.currentProgram.getStateAsString() match {
        case "" => "[no commands yet]"
        case text => text
      })),
      h3("Sample solution"),
      pre(child.text <-- stateVar.signal.map(_.sampleProgram.getStateAsString() match {
        case "" => "[sample program is empty]"
        case text => text
      }))
    )

  override def getDomElement(): L.Element = domElement
}

class TurtleTargetPreviewComponent(targetSvg: String, description: Option[String]) extends InteractionComponentWithReactiveVars {

  private val descriptionNodes: List[Modifier[HtmlElement]] =
    description.toList.map(desc => p(cls := "turtle-target-description", desc))

  private val domElement =
    div(
      cls := "turtle-target-preview",
      descriptionNodes*,
      div(cls := "turtle-target-image", unsafeHtml := targetSvg)
    )

  override def getDomElement(): L.Element = domElement
}

class TurtleScaffoldingResultComponent(resultVar: Var[Option[TurtleScaffoldingFeedback]]) extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      cls := "turtle-scaffolding-result",
      child <-- resultVar.signal.map {
        case Some(result) =>
          val statusClass = if (result.diff.isPerfectMatch) "ok" else "diff"
          div(
            cls := s"turtle-scaffolding-result-message $statusClass",
            span(result.message)
          )
        case None =>
          div(cls := "turtle-scaffolding-result-message empty", "Start scaffolding to compare with the sample solution.")
      }
    )

  override def getDomElement(): L.Element = domElement
}

class TurtleGradingResultComponent(resultVar: Var[Option[TurtleGradingFeedback]]) extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      cls := "turtle-grading-result",
      child <-- resultVar.signal.map {
        case Some(result) =>
          div(
            h3(s"Result: ${result.grade}"),
            div(cls := "turtle-grading-svg", unsafeHtml := result.svg),
            div(
              cls := "turtle-grading-summary",
              p(s"Missing lines: ${result.missingLines.size}"),
              p(s"Unexpected lines: ${result.additionalLines.size}")
            )
          )
        case None =>
          div(cls := "turtle-grading-placeholder", "Run grading to compare with the target figure.")
      }
    )

  override def getDomElement(): L.Element = domElement
}
