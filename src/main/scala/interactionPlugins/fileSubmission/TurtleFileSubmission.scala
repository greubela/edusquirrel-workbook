package interactionPlugins.fileSubmission

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.fileSubmission.turtleLogic.{TurtleRenderer, TurtleXmlParser}
import org.scalajs.dom.File
import org.scalajs.dom
import scala.scalajs.js

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.Thenable.Implicits.*
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
  private val programScriptHtml = Var(Option.empty[Element])
  private val originalImageMessage = Var("Please upload a turtle XML file.")
  private val simulatedImageMessage = Var("Please upload a turtle XML file.")
  private val programScriptMessage = Var("Please upload a turtle XML file.")

  private case class ScriptBlock(label: String, children: List[ScriptBlock])

  private def renderProgramScript(xml: String): Option[Element] = {
    val hasDomParser = {
      scala.util.Try(js.Dynamic.global.selectDynamic("DOMParser")).toOption
        .exists(parser => !(js.isUndefined(parser) || parser == null))
    }
    if (!hasDomParser) return None

    val parser = new dom.DOMParser()
    val document = parser.parseFromString(xml, "text/xml".asInstanceOf[dom.MIMEType])
    val scripts = document.getElementsByTagName("script")
    val topScript = (0 until scripts.length).toList
      .map(i => scripts.item(i))
      .find(node => Option(node.parentNode).exists(_.nodeName == "scripts"))

    topScript.map { scriptNode =>
      val stack = parseStack(scriptNode)
      div(
        styleAttr := "display: flex; flex-direction: column; gap: 0.25rem; align-items: flex-start;",
        renderStack(stack, indent = 0)
      )
    }
  }

  private def parseStack(scriptNode: dom.Node): List[ScriptBlock] = {
    val blocks = (0 until scriptNode.childNodes.length).toList
      .map(scriptNode.childNodes.item)
      .filter(_.nodeName == "block")

    blocks.map { block =>
      val element = block.asInstanceOf[dom.Element]
      val selector = Option(element.getAttribute("s")).getOrElse("")
      val literals = (0 until element.childNodes.length).toList
        .map(element.childNodes.item)
        .filter(_.nodeName == "l")
        .map(_.textContent.trim)
      val childScript = (0 until element.childNodes.length).toList
        .map(element.childNodes.item)
        .find(_.nodeName == "script")
        .map(parseStack)
        .getOrElse(Nil)
      ScriptBlock(formatBlock(selector, literals), childScript)
    }
  }

  private def formatBlock(selector: String, args: List[String]): String = selector match {
    case "receiveGo" => "when green flag clicked"
    case "gotoXY" if args.size >= 2 => s"go to x: ${args(0)} y: ${args(1)}"
    case "clear" => "clear"
    case "doRepeat" if args.nonEmpty => s"repeat ${args.head}"
    case "forward" if args.nonEmpty => s"move ${args.head} steps"
    case "arcRight" if args.size >= 2 => s"arc ↻ radius: ${args(0)} degrees: ${args(1)}"
    case "arcLeft" if args.size >= 2 => s"arc ↺ radius: ${args(0)} degrees: ${args(1)}"
    case "turn" if args.nonEmpty => s"turn ↻ ${args.head} degrees"
    case "turnLeft" if args.nonEmpty => s"turn ↺ ${args.head} degrees"
    case "changeYPosition" if args.nonEmpty => s"change y by ${args.head}"
    case "setHeading" if args.nonEmpty => s"point in direction ${args.head}"
    case other if args.nonEmpty => s"$other ${args.mkString(" ")}"
    case other => other
  }

  private def renderStack(stack: List[ScriptBlock], indent: Int): List[Element] = {
    stack.map { block =>
      div(
        styleAttr := s"margin-left: ${indent * 14}px; background: #4f67c9; color: white; border-radius: 6px; padding: 0.3rem 0.5rem; font-family: sans-serif; font-size: 0.95rem;",
        block.label,
        if (block.children.nonEmpty)
          div(
            styleAttr := "display: flex; flex-direction: column; gap: 0.25rem; margin-top: 0.25rem;",
            renderStack(block.children, indent + 1)
          )
        else emptyNode
      )
    }
  }

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
              programScriptHtml.set(None)
              originalImageMessage.set("Loading preview...")
              simulatedImageMessage.set("Loading simulation...")
              programScriptMessage.set("Loading program blocks...")

              readBytes(file).onComplete {
                case Success(bytes) =>
                  Try(renderFileAsTuple(bytes)) match {
                    case Success((existingPenTrailDataUrl, simulatedDataUrl)) =>
                      val existingPenTrail = Option(existingPenTrailDataUrl).filter(_.nonEmpty)
                      val simulatedPenTrail = Option(simulatedDataUrl).filter(_.nonEmpty)
                      val xmlText = new String(bytes.map(_.toByte), "UTF-8")
                      val scriptHtml = renderProgramScript(xmlText)

                      originalImageDataUrl.set(existingPenTrail)
                      simulatedImageDataUrl.set(simulatedPenTrail)
                      programScriptHtml.set(scriptHtml)

                      originalImageMessage.set(
                        if (existingPenTrail.isDefined) ""
                        else "Preview image is not available in the uploaded XML."
                      )
                      simulatedImageMessage.set(
                        if (simulatedPenTrail.isDefined) ""
                        else "Simulated image could not be created from the uploaded XML."
                      )
                      programScriptMessage.set(
                        if (scriptHtml.isDefined) ""
                        else "No script blocks could be rendered from the uploaded XML."
                      )

                    case Failure(_) =>
                      originalImageDataUrl.set(None)
                      simulatedImageDataUrl.set(None)
                      programScriptHtml.set(None)
                      originalImageMessage.set("Preview image could not be created from this file.")
                      simulatedImageMessage.set("Simulated image could not be created from this file.")
                      programScriptMessage.set("Program blocks could not be rendered from this file.")
                  }

                case Failure(_) =>
                  originalImageDataUrl.set(None)
                  simulatedImageDataUrl.set(None)
                  programScriptHtml.set(None)
                  originalImageMessage.set("Could not read the selected file.")
                  simulatedImageMessage.set("Could not read the selected file.")
                  programScriptMessage.set("Could not read the selected file.")
              }

            case None =>
              originalImageDataUrl.set(None)
              simulatedImageDataUrl.set(None)
              programScriptHtml.set(None)
              originalImageMessage.set("No file selected.")
              simulatedImageMessage.set("No file selected.")
              programScriptMessage.set("No file selected.")
          }
        }
      )
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
      h4("Program (from XML script)"),
      child <-- programScriptHtml.signal.combineWith(programScriptMessage.signal).map { case (maybeScript, message) =>
        maybeScript match {
          case Some(script) => script
          case None => p(message)
        }
      }
    )
  )
  
  override def getDomElement(): Element = domElement

}

object TurtleFileSubmission {
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
    val existingPenTrailDataUrl = extractPentrailsDataUrl(xml).getOrElse("")
    val commands = TurtleXmlParser.parse(xml)
    val simulatedDataUrl = TurtleRenderer.renderToPngDataUrl(commands)
    (existingPenTrailDataUrl, simulatedDataUrl)
  }

  private def extractPentrailsDataUrl(xml: String): Option[String] = {
    val hasDomParser = {
      scala.util.Try(scala.scalajs.js.Dynamic.global.selectDynamic("DOMParser")).toOption
        .exists(parser => !(scala.scalajs.js.isUndefined(parser) || parser == null))
    }

    val value =
      if (hasDomParser) {
        val parser = new dom.DOMParser()
        val document = parser.parseFromString(xml, "text/xml".asInstanceOf[dom.MIMEType])
        val pentrails = document.getElementsByTagName("pentrails")
        if (pentrails.length == 0) "" else Option(pentrails.item(0).textContent).map(_.trim).getOrElse("")
      } else {
        """(?s)<pentrails>\s*(.*?)\s*</pentrails>""".r
          .findFirstMatchIn(xml)
          .map(_.group(1).trim)
          .getOrElse("")
      }

    if (value.startsWith("data:image/png;base64,")) Some(value) else None
  }
}
