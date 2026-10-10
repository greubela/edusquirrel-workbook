package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.{JavaTurtleEditorExtension, TurtleTaskEditorExtension, TurtleExecutionPanel}
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorPlain, EvaEditorConfig}
import it.evadid.homepage.webElements.{HtmlAppElement, FullscreenLifecycle}
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.interaction.sync.{SyncControl, UpdateImportance}
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular.ProgrammingExerciseFullJava
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaString
import scala.concurrent.Future
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlProgrammingExerciseFullJavaRenderer extends LineBasedRenderingFactory[ProgrammingExerciseFullJava] {
  private[homepage] def editorState(element: ProgrammingExerciseFullJava, syncControl: SyncControl): Var[ProgrammingState] =
    element.interactionVariable.createBoundStateWithUpdateImportance(syncControl, UpdateImportance.MAJOR).toAirstreamVar

  private[homepage] def editorFor(workbookElement: ProgrammingExerciseFullJava,
      boundVar: Var[ProgrammingState]): HtmlAppElement & FullscreenLifecycle = workbookElement.turtleTask match {
      case None => new JavaFunctionBasedEditor(boundVar)
      case Some(task) =>
        val execution = new JavaTurtleEditorExtension(boundVar)
        val emptyTargets = task.cases.filter(_.expectedShape.toTurtleProgram.isEmpty).map(_.call(task.methodName))
        val prompt = s"Use the parameters of ${task.methodName} to draw the requested shape. " +
          s"Check task calls ${task.cases.map(_.call(task.methodName)).mkString(", ")}. " +
          (if emptyTargets.nonEmpty then s"${emptyTargets.mkString(", ")} should draw no lines." else "")
        val assessment = TurtleExecutionPanel.Assessment(
          task.cases.map(entry => TurtleExecutionPanel.Case(entry.call(task.methodName), entry.expectedShape)).toVector,
          () => boundVar.now() match {
            case java: ProgrammingStateJavaString => execution.checkTask(java, task)
            case _ => Future.failed(IllegalStateException("This task requires a Java program."))
          }, prompt)
        EvaEditorPlain(boundVar, EvaEditorConfig(enabledLanguages = List(AppLanguage.Java)),
          extensions = List(execution, new TurtleTaskEditorExtension(boundVar,
            task.cases.headOption.map(_.expectedShape), task.comparisonPolicy, Some(assessment))))
  }

  override protected def createRendering(workbookElement: ProgrammingExerciseFullJava): AtomarLineRendering = {
    val boundVar = editorState(workbookElement, fullInfo.syncControl)
    val editor = editorFor(workbookElement, boundVar)
    val openButton = HtmlButtonElement.withTextLabel(
      "basic/OpenEditor",
      _ => fullInfo.displayControl.setFullscreen(editor)
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
