package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection.*

case class RenderingAsContainerTitle(title: Signal[String], level: Int) extends AtomarLineRendering {

  val isInteraction: Boolean = false

  private def normalizedLevel: Int = math.max(0, math.min(5, level))

  private def headingElement(content: String): Element = normalizedLevel match {
    case 0 => h1(content)
    case 1 => h2(content)
    case 2 => h3(content)
    case 3 => h4(content)
    case 4 => h5(content)
    case _ => h6(content)
  }

  override lazy val render: Element = div(
    cls := elementCssString + " " + structureCssString + " " + s"container-title container-title-level-$normalizedLevel",
    children <-- elementsWithoutContainer.allElementsSignal
  )

  override lazy val elementsWithoutContainer: DomElementCollection = title.map(headingElement)


}

