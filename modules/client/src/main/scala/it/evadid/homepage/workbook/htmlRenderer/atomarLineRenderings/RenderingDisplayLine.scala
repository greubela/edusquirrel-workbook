package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{children, cls, div}
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.workbook.abstractions.WorkbookDisplayElement

case class RenderingDisplayLine(displayElement: WorkbookDisplayElement, content: DomElementCollection, additionalCssStr: String) extends AtomarLineRendering {

  protected val lineCssStr: String = getCssString(displayElement)

  override lazy val render: L.Element = div(
    cls := elementCssString + " " + displayCssString + " " + additionalCssStr,
    children <-- content.allElementsSignal
  )

  override lazy val elementsWithoutContainer: DomElementCollection = content
}

