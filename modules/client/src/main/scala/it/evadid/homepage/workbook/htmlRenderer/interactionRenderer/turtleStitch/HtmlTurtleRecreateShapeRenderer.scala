package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.workbook.interaction.sync.UpdateImportance

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorConfig
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.DomElementSingle
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.workbookEditors.{EditorWithWorkbookPanel, WorkbookEvaEditor}
import it.evadid.workbook.abstractions.RoleInWorkbook.EXERCISE_DESCRIPTION
import it.evadid.workbook.abstractions.TypeOfTextDisplay.PLAINTEXT
import it.evadid.workbook.abstractions.{LangMapContentIdType, TypeOfTextDisplay}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction

case object HtmlTurtleRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleRecreateShapeInteraction] {

  /** Editor side module replaying the drawing of the bound answer step by step. */
  private[homepage] def turtleStepsCard(boundVar: Var[it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState]): ElementCard =
    ElementCard(LanguageMapContentId("basic/turtleStepsTitle"),
      new it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.steps.TurtleStepPanel(boundVar,
        key => laminarHelper.plaintextStringSignal(LanguageMapContentId(s"basic/$key"))).getDomElement())

  override protected def createRendering(workbookElement: TurtleRecreateShapeInteraction): AtomarLineRendering = {
    val boundVar = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar

    val gradingReporter = fullInfo.signals.reportersFor(workbookElement).grading
    val shapeGrader = workbookElement.gradingElements.collectFirst {
      case grader: it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.RecreateShapeGrader => grader
    }.getOrElse(throw IllegalArgumentException("A shape exercise needs its shape grader."))
    val shapeGradingPreview = CompareShapePreview(boundVar, shapeGrader.desiredResult, shapeGrader.comparisonPolicy)

    val associatedInstruction = WorkbookEvaEditor.instruction(workbookElement.elementId,
      workbookElement.instructionText.getOrElse(LanguageMapContentId("turtlestitch/instructionRecreateShape")))

    def createShapeGradingCard: ElementCard = {
      ElementCard(LanguageMapContentId("basic/turtleGradingPanel"), shapeGradingPreview.getDomElement())
    }

    def createCodePreview(): HtmlAppElement = {
      val defaultConfig: SnapCodeEditorConfig = SnapCodeEditorConfig()
      SnapPreviewEditor(boundVar, SnapCodeEditorConfig(), fullInfo.signals.currentLanguage)
    }

    def createEditor(): HtmlAppElement = {


      val useConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.forPalette(workbookElement.exerciseConfig.availablePalette)
      val evaEditor = WorkbookEvaEditor(fullInfo, boundVar,
        EvaEditorConfig(enabledLanguages = workbookElement.exerciseConfig.allowedEditorLanguages, snapConfig = useConfig))
      val editor = EditorWithWorkbookPanel(workbookElement, Some(associatedInstruction), gradingReporter,
        List(createShapeGradingCard, turtleStepsCard(boundVar)), evaEditor)

      editor
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
