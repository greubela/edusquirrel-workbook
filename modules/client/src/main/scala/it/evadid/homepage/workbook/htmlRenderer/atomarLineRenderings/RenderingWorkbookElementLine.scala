package it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.workbook.htmlRenderer.DomElementCollection
import it.evadid.workbook.abstractions.{WorkbookDisplayElement, WorkbookElement, WorkbookInteractionElementFull, WorkbookInteractionUntyped, WorkbookStructureElement}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.*
import it.evadid.homepage.workbook.htmlRenderer.controlElements.{InteractionInfoPanel, InteractionScaffoldingPanel}
case class RenderingWorkbookElementLine(workbookElement: WorkbookElement, content: DomElementCollection, additionalCssStr: String) extends AtomarLineRendering {

  private def scaffoldingPanel[T, GA, SA](interaction: WorkbookInteractionElementFull[T, GA, SA]): Option[Element] =
    interaction.scaffoldingElement.map { scaffolder =>
      val variable = interaction.interactionVariable
      def label(key: String): Signal[String] = fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId(s"basic/$key"))
      new InteractionScaffoldingPanel[T, SA](
        () => variable.currentValue, variable.observableValue.toEventStream(), fullInfo.signals.currentLanguage,
        EventStream.merge(fullInfo.signals.user.distinct.changes.map(_ => ()),
          fullInfo.signals.workbook.map(_.map(_.loadedWorkbook)).distinct.changes.map(_ => ())),
        scaffolder.generateFeedback,
        result => {
          variable.updateHistory(_.updateScaffoldings(_ + (result.forState -> result)))
          fullInfo.syncControl.requestStore(variable)
        },
        InteractionScaffoldingPanel.Labels(label("scaffoldingRequest"), label("scaffoldingLoading"),
          label("scaffoldingDismiss"), label("scaffoldingError"), label("scaffoldingTimeout"), label("scaffoldingNotice"))
      ).getDomElement()
    }

  protected val lineCssStr: String = getCssString(workbookElement)

  protected def getCssString(workbookElement: WorkbookElement): String = workbookElement.match {
    case d: WorkbookDisplayElement => displayCssString
    case i: WorkbookInteractionUntyped => interactionCssString
    case s: WorkbookStructureElement[?] => structureCssString
  }


  override lazy val render: L.Element = workbookElement.match {
    case d: WorkbookDisplayElement =>
      div(
        cls := elementCssString + " " + displayCssString + " " + additionalCssStr,
        children <-- content.allElementsSignal
      )
    case i: WorkbookInteractionUntyped =>
      val interactionInfoPanel: InteractionInfoPanel = InteractionInfoPanel(i)
      div(
        cls := elementCssString + " " + interactionCssString ,
        interactionInfoPanel.getDomElement(),
        div(
          cls := interactionContentCssString +" " + additionalCssStr,
          children <-- content.allElementsSignal
        ),
        scaffoldingPanel(i)
      )
    case s: WorkbookStructureElement[?] =>
      div(
        cls := elementCssString + " " + structureCssString + " " + additionalCssStr,
        children <-- content.allElementsSignal
      )

  }

  override lazy val elementsWithoutContainer: DomElementCollection = content
}

