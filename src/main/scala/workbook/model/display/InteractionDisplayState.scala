package workbook.model.display

import workbook.model.display.InteractionComponent.*
import workbook.model.display.InteractionComponent.InteractionContentRole.*
import workbook.workbookHtmlElements.*
import workbook.workbookHtmlElements.HtmlInteractionButtonComponent.*

sealed trait InteractionDisplayState {

  def allKnownComponents: List[InteractionComponentForRole]

  def visibleComponentRolesInOrder: List[InteractionContentRole]

  def disabledComponentRoles: Seq[InteractionContentRole] = Seq()

  def layoutCssForExercise: Seq[String]

}

object InteractionDisplayState {
  case class DefaultEditorDisplayState(allKnownComponents: List[InteractionComponentForRole]) extends InteractionDisplayState {
    val layoutCssForExercise: List[String] = List("style-vbox", "display-default-editor")
    val visibleComponentRolesInOrder: List[InteractionContentRole] = List(ButtonShowScaffolder, Editor, ButtonStartGrading)
  }

  case class DefaultScaffoldingStatusState(allKnownComponents: List[InteractionComponentForRole]) extends InteractionDisplayState {
    val layoutCssForExercise = List("style-vbox", "display-default-scaffolding-status")

    val visibleComponentRolesInOrder = List(ButtonStartScaffolding, ScaffoldingStateEditor, ButtonShowEditor, Editor)

    override def disabledComponentRoles = Seq(Editor)
  }

  case class DefaultScaffoldingResultState(allKnownComponents: List[InteractionComponentForRole]) extends InteractionDisplayState {
    val layoutCssForExercise = List("style-vbox", "display-default-scaffolding-result")

    val visibleComponentRolesInOrder = List(ScaffoldingResult, ButtonShowScaffolder, ScaffoldingStateEditor, ButtonShowEditor, Editor)

    override def disabledComponentRoles = Seq(ScaffoldingResult, ScaffoldingStateEditor, Editor)

  }

  case class DefaultGradingResultState(allKnownComponents: List[InteractionComponentForRole]) extends InteractionDisplayState {
    val layoutCssForExercise = List("style-vbox", "display-default-grading-result")

    val visibleComponentRolesInOrder = List(Editor, ButtonShowEditor, GradingResult)

    override def disabledComponentRoles = Seq(Editor)
  }

  case class CustomDisplayState(
      allKnownComponents: List[InteractionComponentForRole],
      visibleComponentRolesInOrder: List[InteractionContentRole],
      layoutCssForExercise: Seq[String]
  ) extends InteractionDisplayState

}