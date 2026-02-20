package interactionPlugins.fileSubmission.cards

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import interactionPlugins.fileSubmission.TurtleStitchFileFactory
import org.scalajs.dom.URL
import util.HtmlHelper
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

case class TurtleFileExistingProjectCard(workbookInfoVar: Var[WorkbookInfo], filename: String, existingProjectImg: ImageDescription, existingProject: URL) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchFileFactory.languageMapProvidedProjectLabel))
  )

  private val downloadButton: Element = button(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchFileFactory.languageMapDownloadButton)),
    onClick --> { _ =>
      HtmlHelper.downloadFromUrl("TurtleStitch_" + filename + ".xml", existingProject)
    }
  )

  private val domElementSignal: Signal[List[Element]] = {
    HtmlHelper.imagePreview(existingProjectImg).map(preview => List(headline, preview, downloadButton))
  }

  private val domElement: Element = div(
    cls := "preview-card",
    children <-- domElementSignal,
  )

  private def getWorkshopInfoVar = workbookInfoVar

  override def getDomElement(): Element = domElement

  def getAsPreviewLine: HtmlWorkbookElement = new HtmlWorkbookElement {
    override def workbookInfoVar: L.Var[WorkbookInfo] = getWorkshopInfoVar

    override def getDomElement(): L.Element = div(
      cls := "workbook-interaction preview-line",
      domElement
    )
  }

}
