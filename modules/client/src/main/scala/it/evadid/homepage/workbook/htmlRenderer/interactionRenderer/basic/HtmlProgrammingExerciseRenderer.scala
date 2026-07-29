package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.ExecutionMethod
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingExercise, ProgrammingExerciseState}
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlProgrammingExerciseRenderer extends LineBasedRenderingFactory[ProgrammingExercise] {

  override protected def createRendering(workbookElement: ProgrammingExercise): AtomarLineRendering = {
    val interaction = workbookElement.interactionVariable
    // Fingerprint-based binding: BeProgram.equals is unreliable (function-typed AST fields).
    val boundVar: Var[ProgrammingExerciseState] = Var(interaction.currentValue)
    var lastFingerprint: String = ProgrammingExerciseState.fingerprint(interaction.currentValue)

    interaction.observableValue.addObserver(
      handleOnUpdate = { restored =>
        val fp = ProgrammingExerciseState.fingerprint(restored)
        if fp != lastFingerprint then
          lastFingerprint = fp
          boundVar.set(restored)
      },
      informObserverWith = ExecutionMethod.executeSync
    )

    def persistFromEditor(next: ProgrammingExerciseState): Unit = {
      val fp = ProgrammingExerciseState.fingerprint(next)
      if fp == lastFingerprint then return
      lastFingerprint = fp
      boundVar.set(next)
      interaction.setStateFromUserInteraction(fullInfo.syncControl, next, UpdateImportance.MAJOR)
    }

    val editor: SnapCodeEditor = SnapCodeEditor(boundVar, onStateEdited = persistFromEditor)

    def buttonPressed(): Unit =
      fullInfo.displayControl.setFullscreen(editor)

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())
    val canvasCard = ElementCard(LanguageMapContentId("basic/canvas"), editor.previewCanvas)

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, canvasCard))
  }
}
