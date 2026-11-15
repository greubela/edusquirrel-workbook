package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleTaskEditorExtension
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorLabels}
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.DomElementSingle
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.workbookEditors.EditorWithWorkbookPanel
import it.evadid.workbook.abstractions.RoleInWorkbook.EXERCISE_DESCRIPTION
import it.evadid.workbook.abstractions.TypeOfTextDisplay.PLAINTEXT
import it.evadid.workbook.abstractions.{LangMapContentIdType, TypeOfTextDisplay}
import it.evadid.workbook.elements.displayElements.DisplayLangMapContent
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case object HtmlTurtleRecreateShapeRenderer extends LineBasedRenderingFactory[TurtleRecreateShapeInteraction] {

  override protected def createRendering(workbookElement: TurtleRecreateShapeInteraction): AtomarLineRendering = {
    val boundVar = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar

    val gradingReporter = InteractionVariableGradingReporter(workbookElement)
    val shapeGradingPreview = CompareShapePreview(boundVar, workbookElement.desiredResult)

    val associatedInstruction = DisplayLangMapContent("tmp-001", LanguageMapContentId("turtle/instructionRecreateShape"), LangMapContentIdType(EXERCISE_DESCRIPTION, PLAINTEXT))

    def createShapeGradingCard: ElementCard = {
      ElementCard(LanguageMapContentId("basic/turtleGradingPanel"), shapeGradingPreview.getDomElement())
    }

    def createCodePreview(): HtmlAppElement = {
      val defaultConfig: SnapCodeEditorConfig = SnapCodeEditorConfig()
      SnapPreviewEditor(boundVar, SnapCodeEditorConfig(), fullInfo.signals.currentLanguage)
    }

    def createEditor(): HtmlAppElement = {


      val useConfig: SnapCodeEditorConfig = SnapCodeEditorConfig.forPalette(workbookElement.exerciseConfig.availablePalette)
      val draw = new TurtleTaskEditorExtension(boundVar, None)
      val evaEditor = EvaEditor(
        fullInfo.loggerSystemInfo.uiAndDomLogger,
        boundVar,
        List(draw),
        Var(EvaEditorConfig(enabledLanguages = workbookElement.exerciseConfig.allowedEditorLanguages, snapConfig = useConfig)),
        humanLanguage = fullInfo.signals.currentLanguage,
        labels = EvaEditorLabels.fromContent(laminarHelper.plaintextStringSignal))
      val editor = EditorWithWorkbookPanel(workbookElement, Some(associatedInstruction), gradingReporter, List(createShapeGradingCard), evaEditor)

      editor
    }

    def openEditorButtonPressed(): Unit = {
      fullInfo.displayControl.setFullscreen(createEditor())
    }

    def createCards(): List[ElementCard] = {
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
