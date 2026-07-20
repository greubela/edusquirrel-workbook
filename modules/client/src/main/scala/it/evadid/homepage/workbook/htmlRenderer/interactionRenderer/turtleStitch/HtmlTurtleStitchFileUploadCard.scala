package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlButtonElement
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.codeTaskToggle.HtmlSketchDownloadRenderer.fullInfo
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.elements.interactionElements.TurtleStitch.TurtleStitchProjectState
import it.evadid.workbook.interaction.sync.UpdateImportance
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLInputElement}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


private[turtleStitch] case class HtmlTurtleStitchFileUploadCard(workbookInteraction: WorkbookInteractionElement[TurtleStitchProjectState], label: LanguageMapContentId) extends HtmlAppElement {

  private val acceptedTypes: List[String] = List("text/turtle", "text/xml")

  private lazy val uploadInput: ReactiveHtmlElement[HTMLInputElement] = input(
    styleAttr := "display:none;",
    typ := "file",
    accept := acceptedTypes.mkString(","),
    onChange --> { event =>
      val inputElement = event.target.asInstanceOf[dom.html.Input]
      if (inputElement.files.length > 0) onNewFileSelected(inputElement.files.item(0))
    }
  )
  private lazy val buttonElement: Element = HtmlButtonElement.withTextLabel(label, event => uploadInput.ref.click(), HtmlButtonElement.stdConfig).getDomElement()

  private def onFileReadSuccessfully(bytes: Array[Byte]): Unit = {
    println("file read successfully, content has " + bytes.length + " bytes!")
    TurtleStitchProjectState.parseFromBytes(bytes).match {
      case Success(newState) => workbookInteraction.interactionVariable.setStateFromUserInteraction(fullInfo.syncControl, newState, UpdateImportance.MAJOR)
      case Failure(err) => println("HtmlTurtleStitchFileUploadCardRenderer::onFileReadSuccessfully error: " + err.getMessage)
    }
  }

  private def onNewFileSelected(file: File): Unit = {
    fullInfo.contentControl.fileFactory.fromFile(file).loadData().onComplete {
      case Success(data) => onFileReadSuccessfully(data.data)
      case Failure(error) => println("[WARN] could not load file, ignoring content: " + error.getMessage)
    }(using ExecutionContext.global)

  }

  def getDomElement(): Element = div(uploadInput, buttonElement)

}
