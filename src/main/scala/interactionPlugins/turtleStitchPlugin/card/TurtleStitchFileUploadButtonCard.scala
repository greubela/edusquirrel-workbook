package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import interactionPlugins.fileSubmission.*
import interactionPlugins.turtleStitchPlugin.card.TurtleStitchFileUploadButtonCard.*
import interactionPlugins.turtleStitchPlugin.card.TurtleStitchFileUploadButtonCard.StorageFormat.{BYTES_AS_BASE64_STRING, BYTES_AS_RAW_STRING}
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLButtonElement, HTMLDivElement, HTMLInputElement}
import util.web.{DownloadHelper, JsHelpers}
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.{AllWorkbookInfo, WorkbookInfo}
import workbook.model.interaction.InteractionVariable
import workbook.model.interaction.history.UpdateImportance

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.typedarray.*
import scala.util.{Failure, Success}

case class TurtleStitchFileUploadButtonCard(
                                 workbookInfo: AllWorkbookInfo,
                                 id: String,
                                 acceptedTypes: List[String],
                                 storageFormat: StorageFormat = BYTES_AS_BASE64_STRING
                               ) extends WorkbookInteraction[String] {

  override val interactionVariable: InteractionVariable[String] = InteractionVariable.stringVariable(this, "")

  private lazy val uploadInput: ReactiveHtmlElement[HTMLInputElement] = input(
    styleAttr := "display:none;",
    typ := "file",
    accept := acceptedTypes.mkString(","),
    onChange --> { event =>
      val inputElement = event.target.asInstanceOf[dom.html.Input]
      if (inputElement.files.length > 0) onNewFileSelected(inputElement.files.item(0))
    }
  )

  private lazy val uploadButton: ReactiveHtmlElement[HTMLButtonElement] = button(
    text <-- workbookInfo.stringSignalFromLanguageMapId("TurtleStitch/uploadButton")(ExecutionContext.global),
    uploadInput,
    onClick --> { _ =>
      uploadInput.ref.click()
    }
  )

  private lazy val fileDom: ReactiveHtmlElement[HTMLDivElement] = div(
    cls := "preview-card",
    h3(
      text <-- workbookInfo.stringSignalFromLanguageMapId("TurtleStitch/uploadButton")(ExecutionContext.global),
    ),
    div(
      cls := "preview-content",
      uploadButton
    )
  )


  private def onFileReadSuccessfully(bytes: Array[Byte]): Unit = {
    val contentAsString: String = storageFormat match {
      case BYTES_AS_RAW_STRING => new String(bytes.map(_.toByte), "UTF-8")
      case BYTES_AS_BASE64_STRING => JsHelpers.byteArrayToBase64String(bytes)
    }

    interactionVariable.updateStateFromUserInteraction(contentAsString, System.currentTimeMillis(), UpdateImportance.MAJOR)

  }

  private def onNewFileSelected(file: File): Unit = {
    val fileFut: Future[Array[Byte]] = DownloadHelper.fetchFile(file)

    fileFut.onComplete {
      case Success(data) => onFileReadSuccessfully(data)
      case Failure(error) => println("[WARN] could not load file, ignoring content: " + error.getMessage)
    }(ExecutionContext.global)

  }
  
  def getDomElement(): Element = fileDom

}

object TurtleStitchFileUploadButtonCard {

  enum StorageFormat {
    case BYTES_AS_RAW_STRING, BYTES_AS_BASE64_STRING
  }

}
