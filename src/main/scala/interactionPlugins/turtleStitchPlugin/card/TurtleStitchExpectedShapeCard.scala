package interactionPlugins.turtleStitchPlugin.card

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.image.{FullImage, ImageDescription}
import contentmanagement.storage.ImageStorage
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.fileSubmission.*
import interactionPlugins.turtleStitchPlugin.TurtleStitchLanguageMaps
import util.HtmlHelper
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.*

case class TurtleStitchExpectedShapeCard(workbookInfoVar: L.Var[WorkbookInfo], expectedOutcome: ImageDescription) extends HtmlWorkbookElement {

  private val headline: Element = h3(
    child <-- workbookInfoVar.signal.map(_.languageStringFromMap(TurtleStitchLanguageMaps.languageMapShowExpected))
  )

  private val domElementSignal: Signal[List[Element]] = {
    HtmlHelper.imagePreview("preview-content", expectedOutcome).map(preview => List(headline, preview))
  }

  Map("a" -> "b")
  
  private val domElement: Element = div(
    cls := "preview-card",
    children <-- domElementSignal,
  )

  override def getDomElement(): Element = domElement

}
