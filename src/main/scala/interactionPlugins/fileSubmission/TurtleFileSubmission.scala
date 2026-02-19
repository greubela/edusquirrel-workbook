package interactionPlugins.fileSubmission

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.fileSubmission.turtleLogic.TurtleRenderer
import interactionPlugins.fileSubmission.turtleStitch.{TurtleStitchFromBeExpressionSerializer, TurtleStitchProgramRenderer, TurtleStitchToBeExpressionParser, TurtleStitchXmlLoader}
import org.scalajs.dom
import org.scalajs.dom.File
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.URIUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class TurtleFileSubmission() extends HtmlAppElement {

  private given ExecutionContext = scala.concurrent.ExecutionContext.global

  def readBytes(file: File): Future[Array[Byte]] =
    file.arrayBuffer().toFuture.map { buffer =>
      val array = new Uint8Array(buffer)
      Array.tabulate(array.length)(i => array(i).toByte)
    }

  def renderFile(fileBytes: Array[Byte]): String = TurtleFileSubmission.renderFile(fileBytes)

  def renderFileAsTuple(fileBytes: Array[Byte]): (String, String) = TurtleFileSubmission.renderFileAsTuple(fileBytes)

  def renderXmlAsTuple(xml: String): (String, String) = TurtleFileSubmission.renderXmlAsTuple(xml)

  private val originalImageDataUrl = Var(Option.empty[String])
  private val simulatedImageDataUrl = Var(Option.empty[String])
  private val programImageDataUrl = Var(Option.empty[String])
  private val originalImageMessage = Var("Please upload a turtle XML file.")
  private val simulatedImageMessage = Var("Please upload a turtle XML file.")
  private val programImageMessage = Var("Please upload a turtle XML file.")
  private val batchAnalysisMessage = Var("Run analysis for bundled files in resources/turtlestitchxml.")

  private val domElement: Element = div(
    styleAttr := "display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 1rem; align-items: start;",
    div(
      styleAttr := "display: flex; flex-direction: column; gap: 0.5rem;",
      h4("Upload"),
      input(
        typ := "file",
        accept := ".xml,text/xml",
        onChange --> { event =>
          val inputElement = event.target.asInstanceOf[dom.html.Input]
          val maybeFile = Option(inputElement.files).flatMap { files =>
            if (files.length > 0) Option(files.item(0)) else None
          }

          maybeFile match {
            case Some(file) =>
              originalImageDataUrl.set(None)
              simulatedImageDataUrl.set(None)
              programImageDataUrl.set(None)
              originalImageMessage.set("Loading preview...")
              simulatedImageMessage.set("Loading simulation...")
              programImageMessage.set("Loading program image...")

              readBytes(file).onComplete {
                case Success(bytes) =>
                  Try {
                    val xmlText = new String(bytes.map(_.toByte), "UTF-8")
                    val (existingPenTrailDataUrl, simulatedDataUrl) = renderXmlAsTuple(xmlText)
                    val programDataUrl = TurtleFileSubmission.renderProgramAsSvg(xmlText)
                    (existingPenTrailDataUrl, simulatedDataUrl, programDataUrl)
                  } match {
                    case Success((existingPenTrailDataUrl, simulatedDataUrl, programDataUrl)) =>
                      val existingPenTrail = Option(existingPenTrailDataUrl).filter(_.nonEmpty)
                      val simulatedPenTrail = Option(simulatedDataUrl).filter(_.nonEmpty)

                      originalImageDataUrl.set(existingPenTrail)
                      simulatedImageDataUrl.set(simulatedPenTrail)
                      programImageDataUrl.set(programDataUrl)

                      originalImageMessage.set(
                        if (existingPenTrail.isDefined) ""
                        else "Preview image is not available in the uploaded XML."
                      )
                      simulatedImageMessage.set(
                        if (simulatedPenTrail.isDefined) ""
                        else "Simulated image could not be created from the uploaded XML."
                      )
                      programImageMessage.set(
                        if (programDataUrl.isDefined) ""
                        else "No script blocks could be rendered from the uploaded XML."
                      )

                    case Failure(_) =>
                      originalImageDataUrl.set(None)
                      simulatedImageDataUrl.set(None)
                      programImageDataUrl.set(None)
                      originalImageMessage.set("Preview image could not be created from this file.")
                      simulatedImageMessage.set("Simulated image could not be created from this file.")
                      programImageMessage.set("Program image could not be created from this file.")
                  }

                case Failure(_) =>
                  originalImageDataUrl.set(None)
                  simulatedImageDataUrl.set(None)
                  programImageDataUrl.set(None)
                  originalImageMessage.set("Could not read the selected file.")
                  simulatedImageMessage.set("Could not read the selected file.")
                  programImageMessage.set("Could not read the selected file.")
              }

            case None =>
              originalImageDataUrl.set(None)
              simulatedImageDataUrl.set(None)
              programImageDataUrl.set(None)
              originalImageMessage.set("No file selected.")
              simulatedImageMessage.set("No file selected.")
              programImageMessage.set("No file selected.")
          }
        }
      )
      ,
      child.text <-- batchAnalysisMessage.signal
    ),
    div(
      h4("Preview (from XML pen trail)"),
      child <-- originalImageDataUrl.signal.combineWith(originalImageMessage.signal).map { case (maybeDataUrl, message) =>
        maybeDataUrl match {
          case Some(dataUrl) => img(src := dataUrl, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
          case None => p(message)
        }
      }
    ),
    div(
      h4("Simulated"),
      child <-- simulatedImageDataUrl.signal.combineWith(simulatedImageMessage.signal).map { case (maybeDataUrl, message) =>
        maybeDataUrl match {
          case Some(dataUrl) => img(src := dataUrl, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
          case None => p(message)
        }
      }
    ),
    div(
      h4("Program (rendered from scripts)"),
      child <-- programImageDataUrl.signal.combineWith(programImageMessage.signal).map { case (maybeDataUrl, message) =>
        maybeDataUrl match {
          case Some(dataUrl) => img(src := dataUrl, styleAttr := "max-width: 100%; border: 1px solid #ccc;")
          case None => p(message)
        }
      }
    )
  )

  override def getDomElement(): Element = domElement

  private def readTextFile(path: String): Future[String] =
    dom.fetch(path).toFuture.flatMap { response =>
      if (response.ok) response.text().toFuture
      else Future.failed(new RuntimeException(s"Fetch failed for '$path' with status ${response.status}"))
    }

}

object TurtleFileSubmission {
  private val bundledResourceFiles = List(
    "resources/turtlestitchxml/asdf%20(1).xml",
    "resources/turtlestitchxml/test.html",
    "resources/turtlestitchxml/test2%20(1).xml",
    "resources/turtlestitchxml/test2.xml",
    "resources/turtlestitchxml/test3%20(1).xml",
    "resources/turtlestitchxml/test3.xml"
  )
  /*
   * Known limitations / follow-up ideas (non-trivial to fix correctly right now):
   * 1) XML model coverage is intentionally partial for advanced Snap/TurtleStitch features
   *    (custom block bodies, inheritance/nesting dispatch behavior, comments/watchers/media metadata).
   *    Full fidelity requires a significantly more complete serializer/deserializer parity layer.
   * 2) BeExpression <-> TurtleStitch conversion currently focuses on function-call style scripts.
   *    Rich control-flow nodes outside the represented call/nested-script subset are not lossless yet.
   * 3) Script image rendering in this project outputs a simplified SVG representation.
   *    Matching Snap's pixel-exact script picture pipeline would require morphic renderer integration.
   * 4) Current round-trip tests assert green-flag survivability and structural continuity.
   *    Future tests should add selector+argument equivalence matrices for broader command coverage.
   * 5) String-fallback XML parsing is regex-based and may diverge on deeply nested/malformed mixes
   *    compared to Snap's full XML parser pipeline (`SnapSerializer.load*` in store.js).
   * 6) Serializer currently emits a normalized single-sprite project shell for stability.
   *    Preserving original multi-scene layout, sprite metadata ordering, and IDs is a future enhancement.
   * 7) BeExpression->TurtleStitch mapping currently preserves call-centric script semantics,
   *    but not all VM-level constructs can be represented as native TurtleStitch blocks yet.
   */
  def apply(): TurtleFileSubmission = new TurtleFileSubmission()

  def renderFile(fileBytes: Array[Byte]): String = {
    val (existingPenTrailDataUrl, simulatedDataUrl) = renderFileAsTuple(fileBytes)
    if (existingPenTrailDataUrl.nonEmpty) existingPenTrailDataUrl else simulatedDataUrl
  }

  def renderFileAsTuple(fileBytes: Array[Byte]): (String, String) = {
    val xml = new String(fileBytes.map(_.toByte), "UTF-8")
    renderXmlAsTuple(xml)
  }

  def renderXmlAsTuple(xml: String): (String, String) = {
    val projectTry = Try(TurtleStitchXmlLoader.load(xml)).recoverWith { case error =>
      logError("Parsing Turtle XML failed", error)
      Success(TurtleStitchXmlLoader.load("<project name=\"Untitled\" />"))
    }
    val project = projectTry.get
    val existingPenTrailDataUrl = Try {
      project.scenes
        .lift(project.selectedScene - 1)
        .orElse(project.scenes.headOption)
        .flatMap(_.stage.pentrails)
        .filter(_.startsWith("data:image/png;base64,"))
        .getOrElse("")
    }.recover { case error =>
      logError("Extracting pen trail image failed", error)
      ""
    }.get

    val simulatedDataUrl = Try {
      val commands = TurtleStitchProgramRenderer.commandsFrom(project)
      TurtleRenderer.renderToPngDataUrl(commands)
    }.recover { case error =>
      logError("Simulating Turtle program failed", error)
      TurtleRenderer.renderToPngDataUrl(Nil)
    }.get
    (existingPenTrailDataUrl, simulatedDataUrl)
  }

  def loadProject(xml: String) = TurtleStitchXmlLoader.load(xml)

  def parseToBeExpression(xml: String): contentmanagement.model.vm.code.BeExpression =
    TurtleStitchToBeExpressionParser.parseXml(xml)

  def serializeFromBeExpression(expression: contentmanagement.model.vm.code.BeExpression, projectName: String = "fromBeExpression"): String =
    TurtleStitchFromBeExpressionSerializer.toXml(expression, projectName)

  def renderProgramAsSvg(xml: String): Option[String] =
    Try {
      val project = TurtleStitchXmlLoader.load(xml)
      TurtleStitchProgramRenderer.renderScriptsAsSvgDataUrl(project)
    }.recover { case error =>
      logError("Rendering Turtle program as SVG failed", error)
      None
    }.get

  def analyzeBundledFiles(readTextFile: String => Future[String])(using ExecutionContext): Future[List[(String, Boolean)]] =
    Future.sequence {
      bundledResourceFiles.map { encodedPath =>
        val decodedPath = URIUtils.decodeURIComponent(encodedPath)
        readTextFile(encodedPath)
          .map { xml =>
            val (_, simulated) = renderXmlAsTuple(xml)
            renderProgramAsSvg(xml)
            val isAnalyzed = simulated.startsWith("data:image/png;base64,")
            if (!isAnalyzed) logError(s"Simulated image has unexpected format for '$decodedPath'")
            decodedPath -> isAnalyzed
          }
          .recover { case error =>
            logError(s"Analyzing bundled file '$decodedPath' failed", error)
            decodedPath -> false
          }
      }
    }

  def logError(message: String, error: Throwable): Unit =
    dom.console.error(s"[Error] $message", error)

  def logError(message: String): Unit =
    dom.console.error(s"[Error] $message")
}
