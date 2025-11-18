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
import workbook.model.interaction.full.HtmlFullInteractionModel
import workbook.model.interaction.{Grader, Scaffolder}
import workbook.model.states.InteractionState
import workbook.workbookHtmlElements.HtmlInteractionButtonComponent
import workbook.workbookHtmlElements.abstractions.{HtmlFullInteractionContainer, HtmlFullInteractionExercise}
import workbook.workbookHtmlElements.container.HtmlFullInteractionContainerDefault.FullscreenInteractionConfig
import workbook.workbookHtmlElements.container.HtmlFullScreenElement

case class HtmlFullInteractionContainerDefault[
  EditorState <: InteractionState, ScaffoldingState <: InteractionState, GradingState <: InteractionState,
  SR <: ScaffoldingResult[ScaffoldingState], GR <: GradingResult[GradingState],
  S <: Scaffolder[ScaffoldingState, SR], G <: Grader[EditorState, GradingState, GR]
](
   correspondingExercise: HtmlFullInteractionExercise[EditorState, ScaffoldingState, GradingState, SR, GR, S, G],
   interactionModel: HtmlFullInteractionModel[EditorState, ScaffoldingState, GradingState, SR, GR, S, G],
   labelModel: FullInteractionLabelModel,
   useFullScreen: Boolean = false,
   fullscreenConfig: Option[FullscreenInteractionConfig] = None
 ) extends HtmlFullInteractionContainer[EditorState, ScaffoldingState, GradingState, SR, GR, S, G] {

  override val displayState: Var[InteractionDisplayState] = Var(DefaultEditorDisplayState(Nil))

  private val languageForLabelsToUse: Var[AppLanguage] = Var(AppLanguage.English)

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

  private lazy val inlineComponents: List[InteractionComponentForRole] = List(
    HtmlInteractionButtonComponent.ShowEditorButton(_ => displayState.set(DefaultEditorDisplayState(inlineComponents))),
    HtmlInteractionButtonComponent.ShowScaffoldingButton(_ => displayState.set(DefaultScaffoldingStatusState(inlineComponents))),

    HtmlInteractionButtonComponent.StartScaffoldingButton(_ => {
      interactionModel.controller.scaffolder.generateFeedback(scaffolderResult => interactionModel.model.currentScaffoldingResultVar.set(Some(scaffolderResult)))
      displayState.set(DefaultScaffoldingResultState(inlineComponents))
    }),
    HtmlInteractionButtonComponent.StartGradingButton(_ => {
      interactionModel.controller.grader.gradeState(interactionModel.model.currentEditorStateVar.now(), gradingResult => interactionModel.model.currentGradingResultVar.set(Some(gradingResult)))
      displayState.set(DefaultGradingResultState(inlineComponents))
    }),

    interactionModel.visualizer.visualizeEditor(interactionModel.model.currentEditorStateVar),
    interactionModel.visualizer.visualizeScaffolderStateEditor(interactionModel.model.currentScaffoldingStateVar),
    interactionModel.visualizer.visualizeGraderStateEditor(interactionModel.model.currentGradingStateVar),

    interactionModel.visualizer.visualizeGradingResult(interactionModel.model.currentGradingResultVar),
    interactionModel.visualizer.visualizeScaffoldingResult(interactionModel.model.currentScaffoldingResultVar)
  )

  private lazy val inlineInteractionContainer: Element = div(
    cls := "container-interaction",

    children <-- displayState.signal.map(curDisplayState => {
      curDisplayState.visibleComponentRolesInOrder.map(compRole => {
        val domElement = inlineComponents.find(_.forContentRole == compRole).get.getDomElement()
        val res = if (labelModel.supportedInteractionRoles.contains(compRole))
          combineWithLabel(domElement, compRole)
        else domElement
        res
      }
      )
    })

  )

  private def labelledComponent(component: InteractionComponentForRole): Element = {
    val domElement = component.getDomElement()
    if (labelModel.supportedInteractionRoles.contains(component.forContentRole)) then
      combineWithLabel(domElement, component.forContentRole)
    else domElement
  }

  private lazy val fullscreenComponents: List[InteractionComponentForRole] = {
    val fullscreenTarget = fullscreenConfig.map(_.fullscreenElement).getOrElse(
      throw new IllegalArgumentException("Fullscreen interaction requires a fullscreen element")
    )

    val editorComponent = interactionModel.visualizer.visualizeEditor(interactionModel.model.currentEditorStateVar)
    val scaffoldingEditorComponent = interactionModel.visualizer.visualizeScaffolderStateEditor(interactionModel.model.currentScaffoldingStateVar)
    val gradingEditorComponent = interactionModel.visualizer.visualizeGraderStateEditor(interactionModel.model.currentGradingStateVar)
    val gradingResultComponent = interactionModel.visualizer.visualizeGradingResult(interactionModel.model.currentGradingResultVar)
    val scaffoldingResultComponent = interactionModel.visualizer.visualizeScaffoldingResult(interactionModel.model.currentScaffoldingResultVar)

    val scaffoldingFullscreenView = div(
      cls := "fullscreen-interaction-content fullscreen-scaffolding-content",
      labelledComponent(scaffoldingEditorComponent),
      labelledComponent(scaffoldingResultComponent)
    )

    val gradingFullscreenView = div(
      cls := "fullscreen-interaction-content fullscreen-grading-content",
      labelledComponent(gradingEditorComponent),
      labelledComponent(gradingResultComponent)
    )

    val startScaffoldingButton = HtmlInteractionButtonComponent.StartScaffoldingButton(_ => {
      interactionModel.controller.scaffolder.generateFeedback(scaffolderResult => interactionModel.model.currentScaffoldingResultVar.set(Some(scaffolderResult)))
      fullscreenTarget.setElementFullscreen(scaffoldingFullscreenView)
    })

    val startGradingButton = HtmlInteractionButtonComponent.StartGradingButton(_ => {
      interactionModel.controller.grader.gradeState(interactionModel.model.currentEditorStateVar.now(), gradingResult => interactionModel.model.currentGradingResultVar.set(Some(gradingResult)))
      fullscreenTarget.setElementFullscreen(gradingFullscreenView)
    })

    List(
      startScaffoldingButton,
      startGradingButton,
      editorComponent,
      scaffoldingEditorComponent,
      gradingEditorComponent,
      gradingResultComponent,
      scaffoldingResultComponent
    )
  }

  private lazy val fullscreenInteractionContainer: Element = {
    val editorComponent = fullscreenComponents.find(_.forContentRole == InteractionComponent.InteractionContentRole.Editor).get
    val startScaffoldingButton = fullscreenComponents.collectFirst { case btn if btn.forContentRole == InteractionComponent.InteractionContentRole.ButtonStartScaffolding => btn }.get
    val startGradingButton = fullscreenComponents.collectFirst { case btn if btn.forContentRole == InteractionComponent.InteractionContentRole.ButtonStartGrading => btn }.get

    div(
      cls := "container-interaction fullscreen-interaction",
      labelledComponent(editorComponent),
      div(
        cls := "fullscreen-interaction-actions",
        labelledComponent(startScaffoldingButton),
        labelledComponent(startGradingButton)
      )
    )
  }

  private val fullscreenEnabled = useFullScreen && fullscreenConfig.nonEmpty

  private val (allKnownComponents, selectedInteractionContainer) =
    if (fullscreenEnabled) then (fullscreenComponents, fullscreenInteractionContainer)
    else (inlineComponents, inlineInteractionContainer)

  println("allKnownComponents: " + allKnownComponents)

  private def onNewDisplayState(newDisplayState: InteractionDisplayState): Unit = {
    println("set enabled + disabled (disable: " + newDisplayState.disabledComponentRoles + ")")
    allKnownComponents.foreach(curComponent =>
      curComponent.setDisabled(newDisplayState.disabledComponentRoles.contains(curComponent.forContentRole))
    )

  }

  displayState.set(DefaultEditorDisplayState(allKnownComponents))
  displayState.signal.addObserver(Observer[InteractionDisplayState](newValue => onNewDisplayState(newValue)))(unsafeWindowOwner)

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

  override def getDomElement(): L.Element = selectedInteractionContainer

}


object HtmlFullInteractionContainerDefault {

  final case class FullscreenInteractionConfig(fullscreenElement: HtmlFullScreenElement)

  def add[A <: String](asdf: Int): String = ""


}

