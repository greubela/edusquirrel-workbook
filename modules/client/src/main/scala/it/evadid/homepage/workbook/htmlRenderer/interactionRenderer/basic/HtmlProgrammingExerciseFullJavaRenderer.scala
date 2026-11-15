package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.TurtleTaskEditorExtension
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorLabels}
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.DomElementSingle
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.workbookEditors.EditorWithWorkbookPanel
import it.evadid.vm.conversion.ProgrammingStateConversions.*
import it.evadid.workbook.abstractions.scaffolding.AdditionalInfoLlmScaffolding
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExerciseFullJava
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleDrawingPolicy
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.interaction.sync.{SyncControl, UpdateImportance}
import it.evadid.workbook.meta.InteractionVariableGradingReporter

case object HtmlProgrammingExerciseFullJavaRenderer extends LineBasedRenderingFactory[ProgrammingExerciseFullJava] {
  private[homepage] def editorState(element: ProgrammingExerciseFullJava, syncControl: SyncControl): Var[ProgrammingState] =
    element.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar

  private[homepage] def createNewEditor(
                                         element: ProgrammingExerciseFullJava,
                                         boundVar: Var[ProgrammingState],
                                         gradingReporter: InteractionVariableGradingReporter[ProgrammingState, AdditionalProgrammingTestInfo, AdditionalInfoLlmScaffolding]
                                       ):
  EditorWithWorkbookPanel[ProgrammingState, AdditionalProgrammingTestInfo, AdditionalInfoLlmScaffolding, EvaEditorConfig] = {

    val draw = new TurtleTaskEditorExtension(boundVar, None)
    val evaEditor = EvaEditor(
      fullInfo.loggerSystemInfo.uiAndDomLogger,
      boundVar,
      List(draw),
      Var(EvaEditorConfig(enabledLanguages = List(AppLanguage.Java))),
      humanLanguage = fullInfo.signals.currentLanguage,
      labels = EvaEditorLabels.fromContent(laminarHelper.plaintextStringSignal))
    EditorWithWorkbookPanel(element, gradingReporter, DomElementSingle(draw.getDomElement()), evaEditor)
  }

  override protected def createRendering(workbookElement: ProgrammingExerciseFullJava): AtomarLineRendering = {
    val boundVar = editorState(workbookElement, fullInfo.syncControl)
    val gradingReporter = InteractionVariableGradingReporter(workbookElement)

    val openButton = HtmlButtonElement.withTextLabel(
      "basic/OpenEditor", _ => fullInfo.displayControl.setFullscreen(createNewEditor(workbookElement, boundVar, gradingReporter))
    )
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), openButton.getDomElement())
    val editorCard = ElementCard(
      LanguageMapContentId("basic/staticPreviewProgram"),
      div(
        cls := "java-full-editor-preview",
        h3("Java program"),
        pre(code(child.text <-- boundVar.signal.map(_.toJava.code)))
      )
    )

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, editorCard))
  }
}
