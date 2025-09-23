package workbook.workbookHtmlElements.container

import com.raquo.airstream.core.Observer
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.language.AppLanguage
import workbook.model.display.InteractionComponent.{InteractionComponentForRole, InteractionContentRole}
import workbook.model.display.InteractionDisplayState.{DefaultEditorDisplayState, DefaultGradingResultState, DefaultScaffoldingResultState, DefaultScaffoldingStatusState}
import workbook.model.display.{FullInteractionLabelModel, InteractionComponent, InteractionDisplayState}
import workbook.model.feedback.grading.GradingResult
import workbook.model.feedback.scaffolding.ScaffoldingResult
import workbook.model.interaction.full.{FullInteractionExerciseModel, HtmlFullInteractionModel}
import workbook.model.interaction.{Grader, Scaffolder}
import workbook.model.states.InteractionState
import workbook.workbookHtmlElements.HtmlInteractionButtonComponent
import workbook.workbookHtmlElements.abstractions.{HtmlFullInteractionContainer, HtmlFullInteractionExercise}

case class HtmlFullInteractionContainerDefault[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult[ScaffoldingState], GR <: GradingResult[GradingState],
  S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
](
   correspondingExercise: HtmlFullInteractionExercise[EditorState, ScaffoldingState, GradingState, SR, GR, S, G],
   interactionModel: HtmlFullInteractionModel[EditorState, ScaffoldingState, GradingState, SR, GR, S, G],
   labelModel: FullInteractionLabelModel
 ) extends HtmlFullInteractionContainer[EditorState, ScaffoldingState, GradingState, SR, GR, S, G] {

  private val reactiveModelToDraw: Var[FullInteractionExerciseModel[EditorState, ScaffoldingState, GradingState, SR, GR]] = Var(interactionModel.model)
  private val languageForLabelsToUse: Var[AppLanguage] = Var(AppLanguage.English)

  private def getAllKnownComponents(): List[InteractionComponentForRole] = allKnownComponents

  val allKnownComponents: List[InteractionComponentForRole] = List(
    HtmlInteractionButtonComponent.ShowEditorButton(_ => displayState.set(DefaultEditorDisplayState(getAllKnownComponents()))),
    HtmlInteractionButtonComponent.ShowScaffoldingButton(_ => displayState.set(DefaultScaffoldingStatusState(getAllKnownComponents()))),

    HtmlInteractionButtonComponent.StartScaffoldingButton(_ => {
      interactionModel.controller.scaffolder.generateFeedback(scaffolderResult => interactionModel.model.currentScaffoldingResultVar.set(Some(scaffolderResult)))
      displayState.set(DefaultScaffoldingResultState(getAllKnownComponents()))
    }),
    HtmlInteractionButtonComponent.StartGradingButton(_ => {
      interactionModel.controller.grader.gradeState(interactionModel.model.currentEditorStateVar.now(), gradingResult => interactionModel.model.currentGradingResultVar.set(Some(gradingResult)))
      displayState.set(DefaultGradingResultState(getAllKnownComponents()))
    }),

    interactionModel.visualizer.visualizeEditor(interactionModel.model.currentEditorStateVar),
    interactionModel.visualizer.visualizeScaffolderStateEditor(interactionModel.model.currentScaffoldingStateVar),
    interactionModel.visualizer.visualizeGraderStateEditor(interactionModel.model.currentGradingStateVar),

    interactionModel.visualizer.visualizeGradingResult(interactionModel.model.currentGradingResultVar),
    interactionModel.visualizer.visualizeScaffoldingResult(interactionModel.model.currentScaffoldingResultVar)
  )

  println("allKnownComponents: " + allKnownComponents)

  private def onNewDisplayState(newDisplayState: InteractionDisplayState): Unit = {
    println("set enabled + disabled (disable: " + newDisplayState.disabledComponentRoles + ")")
    allKnownComponents.foreach(curComponent =>
      curComponent.setDisabled(newDisplayState.disabledComponentRoles.contains(curComponent.forContentRole))
    )

  }

  override val displayState: Var[InteractionDisplayState] = Var(DefaultEditorDisplayState(allKnownComponents))
  displayState.signal.addObserver(Observer[InteractionDisplayState](newValue => onNewDisplayState(newValue)))(unsafeWindowOwner)


  private def combineWithLabel(domElement: Element, role: InteractionContentRole): Element = {
    div(
      cls := "container-interaction-element",
      div(cls := "container-label " + "label-" + role.cssString,
        child <-- languageForLabelsToUse.signal.map(curLang => labelModel.getLabelFor(curLang, role))
      ),
      div(cls := "container-interaction-element-content ",
        domElement
      ),
    )
  }

  private val fullInteractionContainer: Element = div(
    cls := "container-interaction",

    children <-- displayState.signal.map(curDisplayState => {
      curDisplayState.visibleComponentRolesInOrder.map(compRole => {
        val domElement = allKnownComponents.find(_.forContentRole == compRole).get.getDomElement()
        val res = if (labelModel.supportedInteractionRoles.contains(compRole))
          combineWithLabel(domElement, compRole)
        else domElement
        res
      }
      )
    })

  )

  /*
  private val fullInteractionContainer = {
    div(
      cls := "container-interaction",

      // SCAFFOLDING
      scaffoldingButton,

      div(cls := "container-interaction-element interaction-scaffolding",
        div(cls := "container-label label-scaffolding",
          child <-- languageForLabelsToUse.signal.map(curLang => labelModel.scaffoldingLabel.getInLanguage(curLang))),

        div(cls := "container-interaction-element-content content-scaffolding",
          div(
            cls := "scaffolding-status-editor",
            child <-- reactiveModelToDraw.signal.map(modelSignal => interactionModel.visualizer.visualizeScaffolderStateEditor(modelSignal.currentState.scaffoldingState))
          ),

          SvgFactory.createButtonArrowRight(event => {}, event => {}, event => {}),

          div(
            cls := "scaffolding-result",
            child <-- reactiveModelToDraw.signal.map(_.getScaffoldingResults().lastOption.map(curResult => interactionModel.visualizer.visualizeScaffoldingResult(curResult)).getOrElse(div()))
          )
        )
      ),

      // EDITOR
      editorButton,

      div(cls := "container-interaction-element interaction-editor",
        div(cls := "container-label label-editor",
          child <-- languageForLabelsToUse.signal.map(curLang => labelModel.editorLabel.getInLanguage(curLang))),
        div(cls := "container-interaction-element-content content-editor",
          child <-- reactiveModelToDraw.signal.map(modelSignal => {
            interactionModel.visualizer.visualizeEditor(modelSignal.currentState.editorState)
          })
        )
      ),

      // GRADING
      gradingButton,

      div(cls := "container-interaction-element interaction-grading",
        div(cls := "container-label label-grading",
          child <-- languageForLabelsToUse.signal.map(curLang => labelModel.gradingLabel.getInLanguage(curLang))),

        div(cls := "container-interaction-element-content content-grading",
          div(
            cls := "grading-status-editor",
            child <-- reactiveModelToDraw.signal.map(modelSignal => interactionModel.visualizer.visualizeGraderStateEditor(modelSignal.currentState.gradingState))
          ),
          SvgFactory.createButtonArrowRight(event => {}, event => {}, event => {}),
          div(
            cls := "grading-result",
            child <-- reactiveModelToDraw.signal.map(_.getGradingResults().lastOption.map(curResult => interactionModel.visualizer.visualizeGradingResult(curResult)).getOrElse(div()))
          )
        )

      )
    )
  }

   */

  override def getDomElement(): L.Element = fullInteractionContainer

}


object HtmlFullInteractionContainerDefault {

  def add[A <: String](asdf: Int): String = ""


}

