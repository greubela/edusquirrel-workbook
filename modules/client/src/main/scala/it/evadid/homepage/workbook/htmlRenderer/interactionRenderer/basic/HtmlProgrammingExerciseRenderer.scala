package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.ExecutionMethod
import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.datastructures.vectorShapes.renderer.{SvgLaminarRenderer, VmToSvg}
import it.evadid.homepage.webElements.basic.{HtmlButtonElement, HtmlImageElement}
import it.evadid.homepage.webElements.editor.code.SnapEditor.{SnapCodeEditor, SnapCodeEditorConfig, SnapProgramDerivation, SnapTurtleStage}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingEditorPalette, ProgrammingExercise, ProgrammingExerciseState}
import it.evadid.workbook.interaction.sync.UpdateImportance
import todomove.datastructures.web.file.FullImage

case object HtmlProgrammingExerciseRenderer extends LineBasedRenderingFactory[ProgrammingExercise] {

  override protected def createRendering(workbookElement: ProgrammingExercise): AtomarLineRendering = {
    val interaction = workbookElement.interactionVariable
    // Fingerprint-based binding on canonical Snap XML.
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

    val editorConfig: SnapCodeEditorConfig = workbookElement.editorPalette match
      case ProgrammingEditorPalette.Default => SnapCodeEditorConfig.Testing
      case ProgrammingEditorPalette.PythonCompatibleSnap => SnapCodeEditorConfig.PythonCompatibleTesting

    val editor: SnapCodeEditor = SnapCodeEditor(boundVar, editorConfig, onStateEdited = persistFromEditor)

    def buttonPressed(): Unit =
      fullInfo.displayControl.setFullscreen(editor)

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())
    val canvasCard = ElementCard(LanguageMapContentId("basic/canvas"), editor.previewCanvas)

    // static preview based on the custom display engine (not working yet, for test purposes)
    val shapeLogger = Logger.withNameAndPrefixes(
      Some("HtmlProgrammingExerciseRenderer::ShapeRenderingLogger"),
      PrintToStdLogger.printEverything
    )
    val staticRendering = ElementCard(
      LanguageMapContentId("basic/staticPreviewProgram"),
      SvgLaminarRenderer.render(
        shapeLogger,
        VmToSvg.renderBeExpression(
          shapeLogger,
          SnapProgramDerivation.fromState(boundVar.now()).program.fullProgram
        )
      )
    )

    // Run → TurtleStitchWorker.simulateGreenFlag → stage PNG
    val stageImageVar: Var[Option[AsyncData[Nothing, FullImage]]] = Var(None)

    def runProgram(): Unit =
      stageImageVar.set(Some(SnapTurtleStage.run(boundVar.now())))

    val runButton: HtmlButtonElement =
      HtmlButtonElement.withTextLabel("basic/runProgram", _ => runProgram())

    val stageOutput: Element = div(
      cls := "prog-ex-stage-output",
      child <-- stageImageVar.signal.map {
        case None =>
          div(cls := "prog-ex-stage-output__placeholder")
        case Some(asyncImg) =>
          div(
            cls := "preview-card",
            div(
              cls := "preview-content",
              HtmlImageElement(asyncImg).getDomElement()
            )
          )
      }
    )

    val runCard = ElementCard(
      LanguageMapContentId("basic/turtleOutput"),
      List(runButton.getDomElement(), stageOutput)
    )

    AtomarLineRendering.cardLine(workbookElement, List(buttonCard, staticRendering, canvasCard, runCard))
  }
}
