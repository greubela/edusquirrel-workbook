package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.RichSignal
import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.webElements.basic.HtmlImageElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.contentIdStringSignal
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic.HtmlBasicCheckboxRenderer.fullInfo
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchWorkerFacade
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.elements.interactionElements.TurtleStitch.TurtleStitchProjectState
import it.evadid.workbook.interaction.sync.UpdateImportance
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLInputElement}
import todomove.datastructures.web.file.FullImage

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

  def renderDownloadButton(label: LanguageMapContentId, workbookInteraction: WorkbookInteractionElement[TurtleStitchProjectState]): Element = {
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

  def renderUploadButton(workbookInteraction: WorkbookInteractionElement[TurtleStitchProjectState], label: LanguageMapContentId = LanguageMapContentId("TurtleStitch/uploadButton")): Element = {
    HtmlTurtleStitchFileUploadCard(workbookInteraction, label).getDomElement()
  }

  /*
  Project Preview
   */

  def renderProjectPreviewImage(workbookInteraction: WorkbookInteractionElement[TurtleStitchProjectState]): Element = {
    val xmlSignal: AsyncData[Nothing, String] = workbookInteraction.interactionVariable.asAsync.map(_.programXml.get)
    //xmlSignal.foreach(newContent => println("xml signal changed for workbook interaction " + workbookInteraction.id + ": " + newContent))(using unsafeWindowOwner)
    renderProjectCodePreviewWithAsyncXml(xmlSignal)
  }

  def renderProjectPreviewImage(fileDescription: FileDescription): Element = {
    val xmlSignal: AsyncData[Nothing, String] = fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global).map(_.fileDataAsUtf8String)
    renderProjectCodePreviewWithAsyncXml(xmlSignal)
  }

  /*
  Project Preview Helper
   */

  private def renderProjectCodePreviewWithAsyncXml(xmlSignal: AsyncData[Nothing, String]): Element = {
    val signalForImg: AsyncData[Nothing, FullImage] = getImageSignal(xmlSignal, HtmlFullWorkbookApp.fullInfo.signals.currentLanguage)
    div(
      cls := "preview-card",
      div(
        cls := "preview-content",
        child <-- HtmlImageElement(signalForImg, None).getDomSignal
      )
    )
  }

  private def getImageSignal(xmlSignal: AsyncData[Nothing, String], languageSignal: Signal[HumanLanguage]): AsyncData[Nothing, FullImage] = {
    val asyncLanguage: AsyncData[Nothing, HumanLanguage] = languageSignal.toAsync
    val combined: AsyncData[Nothing, (String, HumanLanguage)] = xmlSignal.combineIgnoreErrorData(asyncLanguage)
    combined.mapAsync(tup => TurtleStitchWorkerFacade.getGreenFlagProgramSnapshotDataSrc(tup._1, tup._2).futureFirstValue)
    /*
        val obsStates: StrictSignal[AsyncDataState[?, String]] = xmlSignal.toStateSignal
        val res = State[AsyncDataState[Nothing, FullImage]](AsyncDataLoading())

        obsStates.combineWith(languageSignal).foreach {
          case (AsyncDataLoading(), _) => res.set(AsyncDataLoading[Nothing, FullImage]())
          case (AsyncDataFailed(cause, data), _) => res.set(AsyncDataFailed[FullImage](cause, data))
          case (AsyncDataSuccess(xml), h) => {
            val snapshot: AsyncData[Nothing, FullImage] = TurtleStitchWorkerFacade.getGreenFlagProgramSnapshotDataSrc(xml, h)
            snapshot.observeAllStates.addObserver(newState => res.set(newState))
          }
        }(using unsafeWindowOwner)
        res.observable*/
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

  private case class HtmlTurtleStitchFileUploadCard(workbookInteraction: WorkbookInteractionElement[TurtleStitchProjectState], label: LanguageMapContentId) extends HtmlAppElement {

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
        case Success(newState) => workbookInteraction.interactionVariable.setStateFromUserInteraction(fullInfo.syncControl,newState, UpdateImportance.MAJOR)
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
