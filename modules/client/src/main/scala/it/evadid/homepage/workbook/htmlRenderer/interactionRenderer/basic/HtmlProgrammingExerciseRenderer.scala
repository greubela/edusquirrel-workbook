package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.ExecutionMethod
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.core.datastructures.vectorShapes.renderer.{SvgLaminarRenderer, VmToSvg}
import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder
import it.evadid.homepage.webElements.basic.{HtmlButtonElement, HtmlImageElement}
import it.evadid.homepage.webElements.editor.code.EvaEditor.{EvaEditor, EvaEditorConfig, EvaEditorPlain}
import it.evadid.homepage.webElements.editor.code.JavaTurtleEditorExtension
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.{AtomarLineRendering, ElementCard}
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch.TurtleJsxGraphRenderer
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingEditorPalette, ProgrammingExercise, ProgrammingState, ProgrammingStateJavaString}
import it.evadid.workbook.elements.interactionElements.programming.TurtleGraphic
import it.evadid.workbook.interaction.sync.UpdateImportance
import todomove.datastructures.web.file.FullImage

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case object HtmlProgrammingExerciseRenderer extends LineBasedRenderingFactory[ProgrammingExercise] {

  override protected def createRendering(workbookElement: ProgrammingExercise): AtomarLineRendering = {

    val boundVar = workbookElement.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar

    val editorConfig: SnapCodeEditorConfig = workbookElement.editorPalette match
      case ProgrammingEditorPalette.Default => SnapCodeEditorConfig.Testing
      case ProgrammingEditorPalette.PythonCompatibleSnap => SnapCodeEditorConfig.PythonCompatibleTesting
      case ProgrammingEditorPalette.BeginnerTurtle => SnapCodeEditorConfig.BeginnerTurtleTesting
      case ProgrammingEditorPalette.Embroidery => SnapCodeEditorConfig.EmbroideryTesting

    val editor = EvaEditorPlain(boundVar, EvaEditorConfig(snapConfig = editorConfig),
      extensions = List(new JavaTurtleEditorExtension(boundVar)))

    def buttonPressed(): Unit = fullInfo.displayControl.setFullscreen(editor)

    val button: HtmlButtonElement = HtmlButtonElement.withTextLabel("basic/OpenEditor", event => buttonPressed())
    val buttonCard = ElementCard(LanguageMapContentId("basic/openEditor"), button.getDomElement())

    val previewEditor = SnapPreviewEditor(boundVar, editorConfig)
    val canvasCard = ElementCard(LanguageMapContentId("basic/canvas"), previewEditor.getDomElement())

    // static preview based on the custom display engine (not working yet, for test purposes)
    val shapeLogger = Logger.withNameAndPrefixes(
      Some("HtmlProgrammingExerciseRenderer::ShapeRenderingLogger"),
      PrintToStdLogger.printEverything
    )
    val staticRendering = ElementCard(
      LanguageMapContentId("basic/staticPreviewProgram"),
      Try {
        val current = editor.state.now()
        current match
          case java: ProgrammingStateJavaString if java.isClassProgram =>
            throw IllegalArgumentException("Full Java classes require the checked Java runner.")
          case _ => ()
        SvgLaminarRenderer.render(
          shapeLogger,
          VmToSvg.renderBeExpression(
            shapeLogger,
            current.toBeExpressionState.expression
          )
        )
      }.getOrElse(div("Preview unavailable for this draft."))
    )


    // Run → TurtleStitchWorker.simulateGreenFlag → stage PNG
    val stageImageVar: Var[Option[FullImage]] = Var(None)
    val runError = Var(Option.empty[String])
    var runId = 0

    def runProgram(): Unit = {
      runId += 1
      val requestedRun = runId
      stageImageVar.set(None)
      runError.set(None)
      val running = Try(editor.getCurrentTurtleCommands()).fold(Future.failed, identity)
      val source = ProgrammingState.fingerprint(editor.currentState())
      running.onComplete {
        case _ if requestedRun != runId || source != ProgrammingState.fingerprint(editor.currentState()) => ()
        case Success(commands) =>
          val path = TurtlePathBuilder[Double](Point(0, 0), commands, 90)
          stageImageVar.set(Some(FullImage(path.svgPathBuilder)))
        case Failure(error) =>
          runError.set(Some(Option(error.getMessage).getOrElse("The program could not be run.")))
      }
    }

    val runButton: HtmlButtonElement =
      HtmlButtonElement.withTextLabel("basic/runProgram", _ => runProgram())

    val stageOutput: Element = div(
      cls := "prog-ex-stage-output",
      child.maybe <-- runError.signal.map(_.map(message => div(role := "alert", message))),
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

    AtomarLineRendering.cardLine(workbookElement,
      List(buttonCard, canvasCard, staticRendering, runCard))
  }
}
