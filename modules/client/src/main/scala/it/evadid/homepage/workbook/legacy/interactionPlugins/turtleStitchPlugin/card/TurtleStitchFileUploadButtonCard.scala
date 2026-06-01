package it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import TurtleStitchFileUploadButtonCard.*
import TurtleStitchFileUploadButtonCard.StorageFormat.{BYTES_AS_BASE64_STRING, BYTES_AS_RAW_STRING}
import it.evadid.core.util.io.Serializer
import it.evadid.homepage.control.info.FullInfo
import it.evadid.homepage.util.web.{DownloadHelper, JsHelpers}
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.model.interaction.variable.InteractionVariable
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLButtonElement, HTMLDivElement, HTMLInputElement}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
case class TurtleStitchFileUploadButtonCard(
                                             fullInfo: FullInfo,
                                             id: String,
                                             acceptedTypes: List[String],
                                             storageFormat: StorageFormat = BYTES_AS_BASE64_STRING
                               ) extends WorkbookInteraction[Option[String]] {

  override val serializer: Serializer[Option[String]] = Serializer.stringOptionIO
  
  override val defaultValue: Option[String] = None
  
  override val interactionVariable: InteractionVariable[Option[String]] = InteractionVariable[Option[String]](this)

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
    text <-- fullInfo.signals.stringFromLanguageMapId("TurtleStitch/uploadButton"),
    uploadInput,
    onClick --> { _ =>
      uploadInput.ref.click()
    }
  )

  private lazy val fileDom: ReactiveHtmlElement[HTMLDivElement] = div(
    cls := "preview-card",
    h3(
      text <-- fullInfo.signals.stringFromLanguageMapId("TurtleStitch/uploadTitle"),
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
    interactionVariable.setStateFromUserInteraction(Some(contentAsString), UpdateImportance.MAJOR)

  }

  private def onNewFileSelected(file: File): Unit = {
    val fileFut: Future[Array[Byte]] = DownloadHelper.fetchFile(file)

    fileFut.onComplete {
      case Success(data) => onFileReadSuccessfully(data)
      case Failure(error) => println("[WARN] could not load file, ignoring content: " + error.getMessage)
    }(using ExecutionContext.global)

  }
  
  def getDomElement(): Element = fileDom

}

object TurtleStitchFileUploadButtonCard {

  enum StorageFormat {
    case BYTES_AS_RAW_STRING, BYTES_AS_BASE64_STRING
  }

}
