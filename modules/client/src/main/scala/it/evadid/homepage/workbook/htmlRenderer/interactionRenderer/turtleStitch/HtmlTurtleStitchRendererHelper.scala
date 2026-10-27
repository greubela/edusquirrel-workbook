package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateSnapXml}
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.{RichObservableValue, RichSignal}
import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.basic.{HtmlButtonElement, HtmlImageElement}
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.toRefactor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleStitchProjectState
import todomove.datastructures.web.file.FullImage

import scala.concurrent.ExecutionContext
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
  def renderDownloadButton(label: LanguageMapContentId, projectRelativeToResources: String): Element = {
    val projectFromFile: file.FileDescription = fullInfo.contentControl.fileFactory.relativeToTechnicalResources(projectRelativeToResources)
    val desiredFilename: String = "TurtleStitch_" + InfoUtil.datetimeFormattedForFilenames() + "_" + projectFromFile.filenameWithExtension
    HtmlButtonElement.withTextLabel(label, event =>
      projectFromFile.loadData().onComplete {
        case Success(projectData) => fullInfo.contentControl.downloadToDisc.downloadFile(desiredFilename, projectData.data)
        case Failure(err) => println("HtmlExploreTurtleStitchExploreProjectRenderer::downloadButton error: " + err.getMessage)
      }(using ExecutionContext.global), HtmlButtonElement.stdConfig).getDomElement()
  }

  def renderDownloadButton(label: LanguageMapContentId, workbookInteraction: WorkbookInteractionElement[TurtleStitchProjectState]): Element = {
    val desiredFilename: String = "TurtleStitch_" + InfoUtil.datetimeFormattedForFilenames() + "_" + workbookInteraction.elementId + ".xml"
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

  def renderProjectPreviewImage(filenameRelToResources: String): Element = {
    val fileDescription: FileDescription = fullInfo.contentControl.fileFactory.relativeToTechnicalResources(filenameRelToResources)
    val xmlAsync: AsyncData[Nothing, String] = AsyncData.forFuture(fileDescription.loadData()).map(_.fileDataAsUtf8String)
    renderProjectCodePreviewWithAsyncXml(xmlAsync)

  }

  /*
  Project Preview Helper
   */

  private def renderProjectCodePreviewWithAsyncXml(xmlAsync: AsyncData[Nothing, String]): Element = {

    val language = HtmlFullWorkbookApp.fullInfo.signals.currentLanguage

    div(
      cls := "preview-card",
      div(
        cls := "preview-content",
        child <-- xmlAsync.observeLoadedValues.toEventStream().map(xml => {
          val editor = SnapPreviewEditor(Var[ProgrammingState](ProgrammingStateSnapXml(xml)),
            SnapCodeEditorConfig.Testing,
            language)
          editor.getDomElement()
        })
      )
    )

  }

}
