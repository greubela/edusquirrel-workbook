package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.turtleStitch

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.util.web.DownloadHelper
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory
import it.evadid.workbook.plugins.TurtleStitch.TurtleStitchExploreProjectElement

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.*

object HtmlTurtleStitchExploreProjectRenderer extends HtmlRenderFactory[TurtleStitchExploreProjectElement] {

  override protected def createDomElement(workbookElement: TurtleStitchExploreProjectElement): Element = {

    val preview: Element = HtmlTurtleStitchPreviewRenderer.render(workbookElement.projectToDownload)

    val downloadButton: Element = button(
      text <-- HtmlRenderFactory.contentIdStringSignal(LanguageMapContentId("TurtleStitch/downloadButton")),
      onClick --> { _ =>
        fullInfo.technical.fileStore.loadAsFuture(workbookElement.projectToDownload).onComplete {
          case Success(projectData) => DownloadHelper.downloadFile(workbookElement.projectToDownload.filenameWithExtension, projectData.data)
          case Failure(err) => println("HtmlExploreTurtleStitchExploreProjectRenderer::downloadButton error: " + err.getMessage)
        }(using ExecutionContext.global)
      }
    )

    val headline: Element = h3(
      text <-- HtmlRenderFactory.contentIdStringSignal(LanguageMapContentId("TurtleStitch/providedProjectLabel"))
    )

    div(
      cls := "workbook-interaction preview-line",
      div(
        cls := "preview-card",
        headline,
        preview,
        downloadButton
      )
    )
  }


}
