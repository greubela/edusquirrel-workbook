package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.*
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorLabels}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle.{JavaTurtleEditorExtension, TurtleTaskEditorExtension}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.controlElements.InteractionInfoPanel
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.interaction.sync.{SyncControl, UpdateImportance}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExerciseFullJava
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleDrawingPolicy

case object HtmlProgrammingExerciseFullJavaRenderer extends LineBasedRenderingFactory[ProgrammingExerciseFullJava] {
  private[homepage] def editorState(element: ProgrammingExerciseFullJava, syncControl: SyncControl): Var[ProgrammingState] =
    element.interactionVariable.createBoundStateWithUpdateImportance(syncControl, UpdateImportance.MAJOR).toAirstreamVar

  private[homepage] def editorFor(workbookElement: ProgrammingExerciseFullJava,
      boundVar: Var[ProgrammingState]): HtmlAppElement  = {
    val draw = new TurtleTaskEditorExtension(boundVar, None, TurtleDrawingPolicy.Coverage)
    EvaEditor(fullInfo.loggerSystemInfo.uiAndDomLogger, boundVar,  List(draw), EvaEditorConfig(enabledLanguages = List(AppLanguage.Java)),
      labels = EvaEditorLabels.fromContent(laminarHelper.plaintextStringSignal(_)))
  }

  override protected def createRendering(workbookElement: ProgrammingExerciseFullJava): AtomarLineRendering = {
    val boundVar = editorState(workbookElement, fullInfo.syncControl)
    val openButton = HtmlButtonElement.withTextLabel(
      "basic/OpenEditor",
      _ => fullInfo.displayControl.setFullscreen(InteractionInfoPanel.withEditor(workbookElement, editorFor(workbookElement, boundVar)))
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
