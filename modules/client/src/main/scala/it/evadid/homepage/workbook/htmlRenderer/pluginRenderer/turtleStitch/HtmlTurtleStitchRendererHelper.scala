package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.state.StateHelper.{InteractionVariableOnJS, StateBasedVar}
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.control.HtmlFullWorkbookApp
import it.evadid.homepage.control.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.util.web.{DownloadHelper, JsHelpers}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.contentIdStringSignal

import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchWorkerFacade
import it.evadid.workbook.model.interaction.WorkbookInteraction
import it.evadid.workbook.model.interaction.sync.UpdateImportance
import it.evadid.workbook.plugins.TurtleStitch.TurtleStitchProjectState
import org.scalajs.dom
import org.scalajs.dom.{File, HTMLButtonElement, HTMLInputElement}

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

  def renderImageLoading(): Element = {
    div(text <-- contentIdStringSignal(LanguageMapContentId("basic/imageLoadingMap")))
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
    val xmlSignal: Signal[Option[String]] = workbookInteraction.interactionVariable.createInteractionSignal().map(_.programXml)
    renderProjectPreviewWithXmlSignal(xmlSignal)
  }

  def renderProjectPreviewImage(fileDescription: FileDescription): Element = {
    val xmlSignal: Signal[Option[String]] = fullInfo.technical.fileStore.loadIntoVariable(fileDescription)(using ExecutionContext.global).toAirstreamVar.signal.mapLazy(_.map(_.fileDataAsUtf8String))
    renderProjectPreviewWithXmlSignal(xmlSignal)
  }

  /*
  Project Preview Helper
   */

  private def tryRenderStringAsImageSrc(strValue: String): Element = {
    if (strValue.trim.isEmpty) renderProjectEmpty()
    else if (strValue.startsWith("data:image")) img(src := strValue, styleAttr := "max-width: 100%")
    else if (strValue.contains("Error")) span(strValue)
    //else span("[Error while rendering as image: " + value + "]")
    else renderImageLoading()
  }

  private def renderProjectPreviewWithXmlSignal(xmlSignal: Signal[Option[String]]): Element = {
    div(
      cls := "preview-card",
      div(
        cls := "preview-content",
        child <-- getImageSignal(xmlSignal, HtmlFullWorkbookApp.fullInfo.signals.currentLanguage)
      )
    )
  }

  private def getImageSignal(xmlSignal: Signal[Option[String]], languageSignal: Signal[HumanLanguage]): Signal[Element] = {
    xmlSignal.map(_.getOrElse("")).combineWith(languageSignal).flatMapSwitch(tup => {
      if (tup._1.trim.isEmpty) Var(renderProjectEmpty()).signal
      else convertTurtleStitchXmlAndLanguageToProgramSrcStringState(tup._1, tup._2).toAirstreamVar.signal.map {
        case Some(imgSrc) => tryRenderStringAsImageSrc(imgSrc)
        case None => renderImageLoading()
      }
    })
  }

  private def convertTurtleStitchXmlAndLanguageToProgramSrcStringState(xml: String, humanLanguage: HumanLanguage): State[Option[String]] = {
    if (xml.trim.isEmpty) State(None)
    else try {
      TurtleStitchWorkerFacade.getGreenFlagProgramSnapshotDataSrc(xml, humanLanguage)
    } catch case e: Throwable =>
      e.printStackTrace()
      State(Some("[Error at loading image: " + e.getMessage + "]"))
  }


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
