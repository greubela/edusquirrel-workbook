package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement, WorkbookInteractionElement, WorkbookStructureElement}
import it.evadid.core.datastructures.state.StateHelper.*
case class RenderingWorkbookElementLine(workbookElement: WorkbookElement, content: DomElementCollection, additionalCssStr: String) extends AtomarLineRendering {

  protected val lineCssStr: String = getCssString(workbookElement)

  protected def getCssString(workbookElement: WorkbookElement): String = workbookElement.match {
    case d: WorkbookDisplayElement => displayCssString
    case i: WorkbookInteractionElement[?] => interactionCssString
    case s: WorkbookStructureElement[?] => structureCssString
  }

  fullInfo

  override lazy val render: L.Element = workbookElement.match {
    case d: WorkbookDisplayElement =>
      div(
        cls := elementCssString + " " + displayCssString + " " + additionalCssStr,
        children <-- content.allElementsSignal
      )
    case i: WorkbookInteractionElement[?] =>
      div(
        cls := elementCssString + " " + interactionCssString ,
        div(
          cls := interactionInfoCssString,
          text <-- fullInfo.syncControl.createObservableReport(i.interactionVariable).deriveValue(_.latestStateIsSyncedTo.size + "").toEventStream()
        ),
        div(
          cls := interactionContentCssString +" " + additionalCssStr,
          children <-- content.allElementsSignal
        )
      )
    case s: WorkbookStructureElement[?] =>
      div(
        cls := elementCssString + " " + structureCssString + " " + additionalCssStr,
        children <-- content.allElementsSignal
      )

  }

  override lazy val elementsWithoutContainer: DomElementCollection = content
}

