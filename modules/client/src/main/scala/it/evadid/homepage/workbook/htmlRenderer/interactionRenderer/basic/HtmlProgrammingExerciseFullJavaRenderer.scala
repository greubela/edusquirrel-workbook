package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.ExecutionMethod
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.code.JavaFunctionBasedEditor
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditorPlain, EvaEditorConfig}
import it.evadid.homepage.webElements.{HtmlAppElement, FullscreenLifecycle}
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingExerciseFullJava, ProgrammingState}
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlProgrammingExerciseFullJavaRenderer extends LineBasedRenderingFactory[ProgrammingExerciseFullJava] {
  override protected def createRendering(workbookElement: ProgrammingExerciseFullJava): AtomarLineRendering = {
    val interaction = workbookElement.interactionVariable
    val boundVar = Var[ProgrammingState](interaction.currentValue)
    var lastFingerprint = ProgrammingState.fingerprint(interaction.currentValue)

    interaction.observableValue.addObserver(
      handleOnUpdate = { restored =>
        val fingerprint = ProgrammingState.fingerprint(restored)
        if fingerprint != lastFingerprint then {
          lastFingerprint = fingerprint
          boundVar.set(restored)
        }
      },
      informObserverWith = ExecutionMethod.executeSync
    )

    def persistFromEditor(next: ProgrammingState): Unit = {
      val fingerprint = ProgrammingState.fingerprint(next)
      if fingerprint != lastFingerprint then {
        lastFingerprint = fingerprint
        boundVar.set(next)
        interaction.setStateFromUserInteraction(fullInfo.syncControl, next, UpdateImportance.MAJOR)
      }
    }

    val editor: HtmlAppElement & FullscreenLifecycle = workbookElement.turtleTask match {
      case None => new JavaFunctionBasedEditor(boundVar, onStateEdited = persistFromEditor)
      case Some(task) => EvaEditorPlain(boundVar,
        EvaEditorConfig(enabledLanguages = List(AppLanguage.Java)), persistFromEditor, javaTask = Some(task))
    }
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
