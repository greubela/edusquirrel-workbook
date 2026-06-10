package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.file.{FileDescription, LoadedFile}
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.ObservableValue
import it.evadid.core.datastructures.state.StateHelper.{InteractionVariableOnJS, StateBasedVar}
import it.evadid.core.datastructures.storage.AsyncData
import it.evadid.core.datastructures.storage.AsyncData.*
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.control.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.contentIdStringSignal
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchWorkerFacade
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.plugins.TurtleStitch.TurtleStitchProjectState
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLInputElement}
import todomove.datastructures.web.file.FullImage

import it.evadid.core.datastructures.storage.AsyncData
import it.evadid.core.datastructures.storage.AsyncData.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object HtmlTurtleStitchRendererHelper {
  /*
   * Basic Elements
   */
  def renderProjectEmpty(): Element = {
    div(text <-- contentIdStringSignal(LanguageMapContentId("TurtleStitch/showEmptyPreview")))
  }

  /*
  * Reusable Card Elements
   */
  def renderDownloadButton(label: LanguageMapContentId, projectFromFile: FileDescription): Element = {
    val desiredFilename: String = "TurtleStitch_" + InfoUtil.datetimeFormattedForFilenames() + "_" + projectFromFile.filenameWithExtension
    button(
      text <-- HtmlRenderFactory.contentIdStringSignal(label),
      onClick --> { _ =>
        fullInfo.technical.fileStore.loadAsFuture(projectFromFile).onComplete {
          case Success(projectData) => DownloadHelper.downloadFile(desiredFilename, projectData.data)
          case Failure(err) => println("HtmlExploreTurtleStitchExploreProjectRenderer::downloadButton error: " + err.getMessage)
        }(using ExecutionContext.global)
      }
    )
  }

  def renderDownloadButton(label: LanguageMapContentId, workbookInteraction: WorkbookInteraction[TurtleStitchProjectState]): Element = {
    val desiredFilename: String = "TurtleStitch_" + InfoUtil.datetimeFormattedForFilenames() + "_" + workbookInteraction.id + ".xml"
    button(
      text <-- HtmlRenderFactory.contentIdStringSignal(label),
      onClick --> { _ =>
        workbookInteraction.interactionVariable.currentValue.programXml.foreach(f = currentXml => {
          DownloadHelper.downloadFile(desiredFilename, currentXml)
        })
      }
    )
  }

  def cardHeadline(label: LanguageMapContentId): Element = h3(
    text <-- HtmlRenderFactory.contentIdStringSignal(label)
  )

  def renderUploadButton(workbookInteraction: WorkbookInteraction[TurtleStitchProjectState], label: LanguageMapContentId = LanguageMapContentId("TurtleStitch/uploadButton")): Element = {
    HtmlTurtleStitchFileUploadCard(workbookInteraction, label).getDomElement()
  }

  /*
  Project Preview
   */

  def renderProjectPreviewImage(workbookInteraction: WorkbookInteraction[TurtleStitchProjectState]): Element = {
    val xmlSignal: Signal[AsyncData[String]] = workbookInteraction.interactionVariable.createInteractionSignal().map(_.programXml).map(AsyncData.fromOption)
    //xmlSignal.foreach(newContent => println("xml signal changed for workbook interaction " + workbookInteraction.id + ": " + newContent))(using unsafeWindowOwner)
    renderProjectPreviewWithXmlSignal(xmlSignal)
  }

  def renderProjectPreviewImage(fileDescription: FileDescription): Element = {
    val file: Signal[AsyncData[LoadedFile]] = fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global).toAirstreamVar.signal
    val xmlSignal: Signal[AsyncData[String]] = file.map(_.map(_.fileDataAsUtf8String))
    //  val xmlSignal: Signal[AsyncData[String]] = fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global).mapLazy(_..map(_.fileDataAsUtf8String)).map(AsyncData.fromOption)
    renderProjectPreviewWithXmlSignal(xmlSignal)
  }

  /*
  Project Preview Helper
   */

  private def renderProjectPreviewWithXmlSignal(xmlSignal: Signal[AsyncData[String]]): Element = {
    val signalForImg: StrictSignal[AsyncData[FullImage]] = getImageSignal(xmlSignal, HtmlFullWorkbookApp.fullInfo.signals.currentLanguage)
    div(
      cls := "preview-card",
      div(
        cls := "preview-content",
        child <-- HtmlImageElement(signalForImg).getDomSignal
      )
    )
  }

  private def getImageSignal(xmlSignal: Signal[AsyncData[String]], languageSignal: Signal[HumanLanguage]): StrictSignal[AsyncData[FullImage]] = {
    val res = Var[AsyncData[FullImage]](AsyncData.AsyncDataLoading())
    xmlSignal.combineWith(languageSignal).foreach {
      case (AsyncDataLoading(), _) => res.set(AsyncData.AsyncDataLoading[FullImage]())
      case (AsyncDataFailed(cause), _) => res.set(AsyncData.AsyncDataFailed[FullImage](cause))
      case (AsyncDataSuccess(xml), h) => {
        val snapshot: ObservableValue[AsyncData[FullImage]] = TurtleStitchWorkerFacade.getGreenFlagProgramSnapshotDataSrc(xml, h)
        snapshot.addObserver(newValue => res.set(newValue))
      }
    }(using unsafeWindowOwner)
    res.signal
  }

  /*
    private def getImageSignal(xmlSignal: Signal[Option[String]], languageSignal: Signal[HumanLanguage]): Signal[Element] = {
      xmlSignal.map(_.getOrElse("")).combineWith(languageSignal).flatMapSwitch(tup => {
        // println("signal changed, xml: " + tup._1.size + ", language: " + tup._2)
        if (tup._1.trim.isEmpty) Var(renderProjectEmpty()).signal
        else convertTurtleStitchXmlAndLanguageToProgramSrcStringState(tup._1, tup._2).toAirstreamVar.signal.map {
          case AsyncData.AsyncDataSuccess(imgSrc) => tryRenderStringAsImageSrc(imgSrc)
          case AsyncData.AsyncDataLoading => renderImageLoading()
          case AsyncData.AsyncDataFailed(cause) => renderImageFailed(cause)
        }
      })
    }
  
  private def convertTurtleStitchXmlAndLanguageToProgramSrcStringState(xml: String, humanLanguage: HumanLanguage): State[AsyncData[String]] = {
    //println("try to render xml: " + xml.take(20) + ".../" + xml.size + " in language: " + humanLanguage)
    if (xml.trim.isEmpty) State(AsyncData.AsyncDataFailed(new IllegalArgumentException("xml is empty")))
    else TurtleStitchWorkerFacade.getGreenFlagProgramSnapshotDataSrc(xml, humanLanguage)

  }*/

  /*
  Upload Button
   */

  private case class HtmlTurtleStitchFileUploadCard(workbookInteraction: WorkbookInteraction[TurtleStitchProjectState], label: LanguageMapContentId) extends HtmlAppElement {

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

    private def onFileReadSuccessfully(bytes: Array[Byte]): Unit = {
      println("file read successfully, content has " + bytes.length + " bytes!")
      TurtleStitchProjectState.parseFromBytes(bytes).match {
        case Success(newState) => workbookInteraction.interactionVariable.setStateFromUserInteraction(newState, UpdateImportance.MAJOR)
        case Failure(err) => println("HtmlTurtleStitchFileUploadCardRenderer::onFileReadSuccessfully error: " + err.getMessage)
      }
    }

    private def onNewFileSelected(file: File): Unit = {
      val fileFut: Future[Array[Byte]] = DownloadHelper.fetchFile(file)

      fileFut.onComplete {
        case Success(data) => onFileReadSuccessfully(data)
        case Failure(error) => println("[WARN] could not load file, ignoring content: " + error.getMessage)
      }(using ExecutionContext.global)

    }

    def getDomElement(): Element = button(
      text <-- HtmlRenderFactory.contentIdStringSignal(label),
      uploadInput,
      onClick --> { _ =>
        uploadInput.ref.click()
      }
    )
  }
}
