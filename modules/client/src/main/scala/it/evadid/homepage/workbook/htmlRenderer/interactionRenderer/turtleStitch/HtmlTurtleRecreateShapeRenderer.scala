package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorLabels}
import it.evadid.homepage.webElements.editor.code.SnapEditor.{SnapMorphicSession, SnapPreviewEditor}
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.DomElementSingle
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps.TurtleStepPanel
import it.evadid.homepage.workbook.htmlRenderer.workbookEditors.EditorWithWorkbookPanel
import it.evadid.workbook.abstractions.RoleInWorkbook.EXERCISE_DESCRIPTION
import it.evadid.workbook.abstractions.{LangMapContentIdType, TypeOfTextDisplay}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleGraphic, TurtleRecreateShapeInteraction}
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

case object HtmlTurtleRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleRecreateShapeInteraction] {

  /** A run is an explicit request, so its drawings start right away instead of waiting for the source to rest. */
  private val immediately: FiniteDuration = Duration.Zero

  /** Editor side module replaying the drawing of `source` step by step; `frame` points always stay in view. */
  private[homepage] def turtleStepsCard(source: Signal[ProgrammingState], frame: Seq[Point[Double]] = Nil): ElementCard =
    ElementCard(LanguageMapContentId("basic/turtleStepsTitle"),
      new TurtleStepPanel(source, key => laminarHelper.plaintextStringSignal(LanguageMapContentId(s"basic/$key")), immediately, frame)
        .getDomElement())

  /** End points of the target's strokes, so the step replay keeps the target's view while the program is incomplete. */
  private def framePoints(target: TurtleGraphic): Seq[Point[Double]] =
    Try(target.renderMovements.flatMap(movement => List(movement.start, movement.end)).distinct).getOrElse(Nil)

  override protected def createRendering(workbookElement: TurtleRecreateShapeInteraction): AtomarLineRendering = {
    val boundVar = workbookElement.interactionVariable.createBoundStateForTyping(() => fullInfo.keyboardInput.lastKey.now(), fullInfo.workbookContextInformation).toAirstreamVar

    // Drawings and the shape grading follow the last run instead of every edit; typing alone never starts the program.
    // A requested grading joins a still running automatic one, which skips the shape grader; so it is requested after that.
    val programRun = new TurtleProgramRun(boundVar, () => {
      val control = workbookElement.interactionVariable.control
      val context = fullInfo.workbookContextInformation
      control.getOrCalculateGrading(context).flatMap(_ => control.getOrCalculateGrading(context, forceGrading = true))(using context.futureExecutionContext)
    })

    val gradingReporter = fullInfo.signals.reportersFor(workbookElement).grading
    val shapeGradingPreview = CompareShapePreview(programRun.executed, workbookElement.desiredResult, workbookElement.comparisonPolicy, immediately)

    val associatedInstruction = DisplayLangMapContent(workbookElement.elementId + "-sidebar-instruction", workbookElement.instructionText.getOrElse(LanguageMapContentId("turtlestitch/instructionRecreateShape")), LangMapContentIdType(EXERCISE_DESCRIPTION, TypeOfTextDisplay.MARKDOWN))

    def createShapeGradingCard: ElementCard = {
      ElementCard(LanguageMapContentId("basic/turtleGradingPanel"),
        div(programRun.element(id => laminarHelper.plaintextStringSignal(id)), shapeGradingPreview.getDomElement()))
    }

    def createCodePreview(): HtmlAppElement =
      SnapPreviewEditor(programRun.executed, SnapCodeEditorConfig(), SnapMorphicSession(), fullInfo.signals.currentLanguage)

    def createEditor(): HtmlAppElement = {
      val useConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.forPalette(workbookElement.exerciseConfig.availablePalette)
      // No editor panels: the side panel's comparison and step cards are the only drawing views.
      val evaEditor = EvaEditor(
        fullInfo.loggerSystemInfo.uiAndDomLogger,
        boundVar,
        Nil,
        Var(EvaEditorConfig(enabledLanguages = workbookElement.exerciseConfig.allowedEditorLanguages, snapConfig = useConfig)),
        humanLanguage = fullInfo.signals.currentLanguage,
        labels = EvaEditorLabels.fromContent(laminarHelper.plaintextStringSignal))
      EditorWithWorkbookPanel(workbookElement, Some(associatedInstruction), gradingReporter,
        List(createShapeGradingCard, turtleStepsCard(programRun.executed, framePoints(workbookElement.desiredResult))),
        evaEditor)
    }

    def openEditorButtonPressed(): Unit = {
      fullInfo.displayControl.setFullscreen(createEditor())
    }

    def createCards(): Seq[ElementCard] = {
      val buttonContent = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => openEditorButtonPressed())
      List(
        ElementCard(LanguageMapContentId("basic/openEditor"), buttonContent.getDomElement()),
        ElementCard(LanguageMapContentId("basic/canvas"), createCodePreview().getDomElement()),
        createShapeGradingCard
      )
    }

    AtomarLineRendering.cardLine(workbookElement, createCards())
  }

}
