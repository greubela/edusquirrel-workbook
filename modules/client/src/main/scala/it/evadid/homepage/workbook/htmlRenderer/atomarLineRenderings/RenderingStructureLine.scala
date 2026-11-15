package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.workbook.abstractions.WorkbookStructureElement

case class RenderingStructureLine(structureElement: WorkbookStructureElement[?], content: DomElementCollection, additionalCssStr: String) extends AtomarLineRendering {

  protected val lineCssStr: String = getCssString(structureElement)

  override lazy val render: L.Element =
    div(
      cls := elementCssString + " " + structureCssString + " " + additionalCssStr,
      children <-- content.allElementsSignal
    )


  override lazy val elementsWithoutContainer: DomElementCollection = content
}