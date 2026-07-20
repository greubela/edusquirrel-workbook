package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.RichSignal
import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.basic.{HtmlButtonElement, HtmlImageElement}
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.codeTaskToggle.HtmlSketchDownloadRenderer.fullInfo
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchWorkerFacade
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.elements.interactionElements.TurtleStitch.TurtleStitchProjectState
import todomove.datastructures.web.file.FullImage

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
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
    HtmlButtonElement.withTextLabel(label, event =>
      projectFromFile.loadData().onComplete {
        case Success(projectData) => fullInfo.contentControl.downloadToDisc.downloadFile(desiredFilename, projectData.data)
        case Failure(err) => println("HtmlExploreTurtleStitchExploreProjectRenderer::downloadButton error: " + err.getMessage)
      }(using ExecutionContext.global), HtmlButtonElement.stdConfig).getDomElement()

  }

  def renderDownloadButton(label: LanguageMapContentId, workbookInteraction: WorkbookInteractionElement[TurtleStitchProjectState]): Element = {
    val desiredFilename: String = "TurtleStitch_" + InfoUtil.datetimeFormattedForFilenames() + "_" + workbookInteraction.id + ".xml"
    HtmlButtonElement.withTextLabel(label, event =>
      workbookInteraction.interactionVariable.currentValue.programXml.foreach(f = currentXml => {
        fullInfo.contentControl.downloadToDisc.downloadFile(desiredFilename, currentXml)
      }), HtmlButtonElement.stdConfig).getDomElement()
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
    val xmlSignal: AsyncData[Nothing, String] = AsyncData.forFuture(fileDescription.loadData()).map(_.fileDataAsUtf8String)
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
