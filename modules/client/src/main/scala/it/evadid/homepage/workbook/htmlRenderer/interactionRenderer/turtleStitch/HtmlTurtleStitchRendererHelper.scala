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
import it.evadid.homepage.workbook.htmlRenderer.{HtmlRenderFactory, LaminarRenderHelper}
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

  private val laminarHelper: LaminarRenderHelper = LaminarRenderHelper.singleton

  /*
   * Basic Elements
   */
  def renderProjectEmpty(): Element = {
    div(text <-- laminarHelper.plaintextStringSignal("TurtleStitch/showEmptyPreview"))
  }

  /*
  * Reusable Card Elements
   */
  def renderDownloadButton(label: LanguageMapContentId, projectFromFile: FileDescription): Element = {
    val desiredFilename: String = "TurtleStitch_" + InfoUtil.datetimeFormattedForFilenames() + "_" + projectFromFile.filenameWithExtension
    button(
      text <-- laminarHelper.plaintextStringSignal(label),
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
      text <-- laminarHelper.plaintextStringSignal(label),
      onClick --> { _ =>
        workbookInteraction.interactionVariable.currentValue.programXml.foreach(f = currentXml => {
          DownloadHelper.downloadFile(desiredFilename, currentXml)
        })
      }
    )
  }

  def cardHeadline(label: LanguageMapContentId): Element = h3(
    text <-- laminarHelper.plaintextStringSignal(label)
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
  }


}
