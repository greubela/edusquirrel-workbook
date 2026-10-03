package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.ExecutionMethod
import it.evadid.core.datastructures.vectorShapes.renderer.{SvgLaminarRenderer, VmToSvg}
import it.evadid.core.datastructures.vectorShapes.svg.{BeExpressionToTurtleCommands, TurtlePathBuilder}
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.webElements.editor.code.SnapEditor.{SnapCodeEditor, SnapCodeEditorConfig, SnapProgramDerivation}
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.vm.BeProgram
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingEditorPalette, ProgrammingExercise, ProgrammingExerciseState}
import it.evadid.workbook.interaction.sync.UpdateImportance

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}

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
      case ProgrammingEditorPalette.BeginnerTurtle => SnapCodeEditorConfig.BeginnerTurtleTesting
      case ProgrammingEditorPalette.Embroidery => SnapCodeEditorConfig.EmbroideryTesting

    val editor: SnapCodeEditor = SnapCodeEditor(boundVar, editorConfig, onStateEdited = persistFromEditor)

    def buttonPressed(): Unit =
      fullInfo.displayControl.setFullscreen(editor)

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())
    val canvasCard = ElementCard(LanguageMapContentId("basic/canvas"), editor.previewCanvas)

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

    val referencePython = workbookElement.referencePython
    val targetBuilderOpt: Option[TurtlePathBuilder[Double]] =
      referencePython.flatMap { py =>
        Try(BeExpressionToTurtleCommands.toPathBuilder(BeProgram.fromPythonString(py).fullProgram)).toOption
      }

    val targetCards: List[ElementCard] = targetBuilderOpt.toList.map { target =>
        ElementCard(
          LanguageMapContentId("basic/targetDrawing"),
          SvgLaminarRenderer.renderExpectedTurtlePath(target)
        )
    }

    val stageSvgVar: Var[Option[Element]] = Var(None)
    val resultInFront: Var[Boolean] = Var(true)
    val overlayReady: Var[Boolean] = Var(false)
    var lastOverlay: Option[(TurtlePathBuilder[Double], TurtlePathBuilder[Double])] = None

    def paint(target: TurtlePathBuilder[Double], path: TurtlePathBuilder[Double], frontIsResult: Boolean): Unit =
      stageSvgVar.set(Some(SvgLaminarRenderer.render(
        shapeLogger,
        VmToSvg.renderOverlay(target, path, resultInFront = frontIsResult)
      )))

    def runProgram(): Unit = {
      editor.getCurrentTurtleCommands().onComplete {
        case Success(res) =>
          val path = TurtlePathBuilder[Double](
            Point(0.0, 0.0),
            res,
            BeExpressionToTurtleCommands.SnapHeadingDeg
          )
          targetBuilderOpt match
            case Some(target) =>
              lastOverlay = Some((target, path))
              overlayReady.set(true)
              paint(target, path, resultInFront.now())
            case None =>
              lastOverlay = None
              overlayReady.set(false)
              stageSvgVar.set(Some(SvgLaminarRenderer.render(shapeLogger, VmToSvg.renderTurtlePathBuilder(path))))
        case Failure(exception) =>
          lastOverlay = None
          overlayReady.set(false)
          stageSvgVar.set(None)
          println("Turtle run failed: " + exception)
      }
    }

    val runButton: HtmlButtonElement =
      HtmlButtonElement.withTextLabel("basic/runProgram", _ => runProgram())

    val stageOutput: Element = div(
      cls := "prog-ex-stage-output",
      child <-- stageSvgVar.signal.map {
        case None =>
          div(cls := "prog-ex-stage-output__placeholder")
        case Some(svgEl) =>
          div(cls := "preview-card", div(cls := "preview-content", svgEl))
      }
    )

    val targetMark = laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/turtleTargetMark"))
    val resultMark = laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/turtleResultMark"))
    val bringTargetFront = laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/turtleBringTargetFront"))
    val bringResultFront = laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/turtleBringResultFront"))

    val overlayLegend: Element = div(
      child <-- Signal.combine(overlayReady, targetMark, resultMark).map {
        case (false, _, _) =>
          emptyNode
        case (true, targetText, resultText) =>
          div(
            span(color := "#1e64dc", targetText),
            span(" · "),
            span(color := "#e00000", resultText)
          )
      }
    )

    val frontButtonLabel: Signal[Element] =
      Signal.combine(resultInFront, bringTargetFront, bringResultFront).map {
        case (true, targetText, _) => span(targetText)
        case (false, _, resultText) => span(resultText)
      }

    val frontButton = HtmlButtonElement(
      frontButtonLabel,
      "button-labeled",
      _ => {
        val next = !resultInFront.now()
        resultInFront.set(next)
        lastOverlay.foreach { (target, path) => paint(target, path, next) }
      },
      Signal.fromValue(HtmlButtonElement.stdConfig)
    )

    val frontButtonSlot: Element = div(
      child <-- overlayReady.signal.map {
        case true => frontButton.getDomElement()
        case false => emptyNode
      }
    )

    val runCardChildren =
      if referencePython.isDefined then List(runButton.getDomElement(), stageOutput, overlayLegend, frontButtonSlot)
      else List(runButton.getDomElement(), stageOutput)

    val runCard = ElementCard(
      LanguageMapContentId("basic/turtleResult"),
      runCardChildren
    )

    AtomarLineRendering.cardLine(
      workbookElement,
      List(buttonCard, canvasCard) ++ targetCards ++ List(staticRendering, runCard)
    )
  }
}
