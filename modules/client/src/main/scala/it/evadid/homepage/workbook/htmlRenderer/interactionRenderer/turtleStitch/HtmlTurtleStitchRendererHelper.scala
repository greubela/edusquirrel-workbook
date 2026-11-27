package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.turtleStitch

import it.evadid.homepage.control.model.FullInfo

import it.evadid.core.datastructures.language.AppLanguage.{Java, Python, SnapLanguage}
import it.evadid.workbook.abstractions.InteractionFormat.{TurtleProjectFormat}

import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateRaw}
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.file
import it.evadid.core.datastructures.file.FileDescription
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.{RichObservableValue, RichSignal}
import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.core.util.InfoUtil
import it.evadid.homepage.webElements.basic.{HtmlButtonElement, HtmlImageElement}
import it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapPreviewEditor
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapCodeEditorConfig
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.workbook.abstractions.WorkbookInteractionElement
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleStitchProjectState
import it.evadid.homepage.webElements.basic.FullImage

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object HtmlTurtleStitchRendererHelper {

  private def laminarHelper(fullInfo: FullInfo): LaminarRenderHelper = LaminarRenderHelper(fullInfo)

  /*
   * Basic Elements
   */
  def renderProjectEmpty(fullInfo: FullInfo): Element = {
    div(text <-- laminarHelper(fullInfo).plaintextStringSignal("TurtleStitch/showEmptyPreview"))
  }

  /*
  * Reusable Card Elements
   */
  def renderDownloadButton(fullInfo: FullInfo, label: LanguageMapContentId, projectRelativeToResources: String): Element = {
    val projectFromFile: file.FileDescription = fullInfo.contentControl.fileFactory.relativeToTechnicalResources(projectRelativeToResources)
    val desiredFilename: String = InfoUtil.downloadName("TurtleStitch " + projectFromFile.structure.filenameWithoutExtension) + projectFromFile.structure.extensionWithPointOrEmpty
    HtmlButtonElement.withTextLabel(fullInfo, label, event =>
      projectFromFile.loadData().onComplete {
        case Success(projectData) => fullInfo.contentControl.downloadToDisc.downloadFile(desiredFilename, projectData.data)
        case Failure(err) => println("HtmlExploreTurtleStitchExploreProjectRenderer::downloadButton error: " + err.getMessage)
      }(using ExecutionContext.global), HtmlButtonElement.stdConfig).getDomElement()
  }

  def renderDownloadButton(fullInfo: FullInfo, label: LanguageMapContentId, workbookInteraction: WorkbookInteractionElement[TurtleProjectFormat.type]): Element = {
    val desiredFilename: String = InfoUtil.downloadName("TurtleStitch " + workbookInteraction.elementId) + ".xml"
    HtmlButtonElement.withTextLabel(fullInfo, label, event =>
      workbookInteraction.interactionVariable.currentValue.programXml.foreach(f = currentXml => {
        fullInfo.contentControl.downloadToDisc.downloadFile(desiredFilename, currentXml)
      }), HtmlButtonElement.stdConfig).getDomElement()
  }

  def cardHeadline(fullInfo: FullInfo, label: LanguageMapContentId): Element = h3(
    text <-- laminarHelper(fullInfo).plaintextStringSignal(label)
  )

  def renderUploadButton(fullInfo: FullInfo, workbookInteraction: WorkbookInteractionElement[TurtleProjectFormat.type], label: LanguageMapContentId = LanguageMapContentId("TurtleStitch/uploadButton")): Element = {
    HtmlTurtleStitchFileUploadCard(fullInfo, workbookInteraction, label).getDomElement()
  }

  /*
  Project Preview
   */

  def renderProjectPreviewImage(fullInfo: FullInfo, workbookInteraction: WorkbookInteractionElement[TurtleProjectFormat.type]): Element = {
    val xmlSignal: AsyncData[Nothing, String] = workbookInteraction.interactionVariable.asAsync.map(_.programXml.get)
    //xmlSignal.foreach(newContent => println("xml signal changed for workbook interaction " + workbookInteraction.id + ": " + newContent))(using unsafeWindowOwner)
    renderProjectCodePreviewWithAsyncXml(fullInfo, xmlSignal)
  }

  def renderProjectPreviewImage(fullInfo: FullInfo, filenameRelToResources: String): Element = {
    val fileDescription: FileDescription = fullInfo.contentControl.fileFactory.relativeToTechnicalResources(filenameRelToResources)
    val xmlAsync: AsyncData[Nothing, String] = AsyncData.forFuture(fileDescription.loadData()).map(_.fileDataAsUtf8String)
    renderProjectCodePreviewWithAsyncXml(fullInfo, xmlAsync)

  }

  /*
  Project Preview Helper
   */

  private def renderProjectCodePreviewWithAsyncXml(fullInfo: FullInfo, xmlAsync: AsyncData[Nothing, String]): Element = {

    val language = fullInfo.signals.currentLanguage

    div(
      cls := "preview-card",
      div(
        cls := "preview-content",
        child <-- xmlAsync.observeLoadedValues.toEventStream().map(xml => {
          val editor = SnapPreviewEditor(Var[ProgrammingState](ProgrammingStateRaw(SnapLanguage, xml)),
            SnapCodeEditorConfig.Testing,
            language)
          editor.getDomElement()
        })
      )
    )

  }

}
