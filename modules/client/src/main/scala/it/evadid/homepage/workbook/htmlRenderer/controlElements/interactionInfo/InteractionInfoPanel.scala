package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.{child, div, *}
import com.raquo.laminar.nodes.{ReactiveHtmlElement, ReactiveSvgElement}
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo.InteractionInfoPanel.interactionInfoCssString
import it.evadid.workbook.abstractions.WorkbookInteractionUntyped
import it.evadid.workbook.interaction.sync.UpdateImportance.DEFAULT
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import org.scalajs.dom.{HTMLDivElement, MouseEvent}

case class InteractionInfoPanel(workbookInteractionElement: WorkbookInteractionUntyped) extends HtmlAppElement {

  val iconGradingScaffolding = IconGradingAndScaffolding(fullInfo, laminarHelper, workbookInteractionElement)
  val iconSync = IconSync(fullInfo, laminarHelper, workbookInteractionElement)


  def historyToCss(curHistory: InteractionVariableHistory[?, ?, ?]): String =
    if (curHistory.historyStates.lastStateOption.exists(_.updateImportance != DEFAULT)) {
      interactionInfoCssString + " status-interacted"
    } else {
      interactionInfoCssString + " -default?"
    }

  def classSignalGradedEntity: EventStream[String] = {
    val gradingSignal = iconGradingScaffolding.gradingReporter.currentGradingSignal.toEventStream()
    val historySignal = iconGradingScaffolding.gradingReporter.currentHistorySignal.toEventStream()
    gradingSignal.combineWith(historySignal).map((curGradedState, curHistory) => try {
      if (curGradedState.totalEvaluation().isCorrect) {
        if (curGradedState.doesEvaluationRelyOnLlm()) interactionInfoCssString + " status-correct-ai"
        else interactionInfoCssString + " status-correct"
      } else historyToCss(curHistory)
    } catch case (err: Throwable) => interactionInfoCssString + s" -err:${err.getMessage}")
  }

  def classSignalCss: EventStream[String] = {
    if (!iconGradingScaffolding.gradingReporter.isGradingSupported) iconGradingScaffolding.gradingReporter.currentHistorySignal.toEventStream().map(historyToCss)
    else classSignalGradedEntity
  }


  lazy val domElement: Element = {
    div(cls <-- classSignalCss,
      div(cls := "interaction-info__icons",
        div(cls := "grading-report-icon", child <-- iconGradingScaffolding.gradingReporter.currentGradingSignal.toEventStream().map(iconGradingScaffolding.deriveGradingSymbol)),
        div(cls := "scaffolding-report-icon", child <-- iconGradingScaffolding.gradingReporter.currentScaffoldingSignal.toEventStream().map(iconGradingScaffolding.deriveScaffoldingSymbol)),
        div(cls := "sync-report-icon", child <-- iconSync.syncReport.map(iconSync.deriveSyncSymbol)),
      )
    )
  }

  override def getDomElement(): Element = domElement

}

object InteractionInfoPanel {

  def withEditor(interaction: WorkbookInteractionUntyped, editor: HtmlAppElement): HtmlAppElement =
    if interaction.scaffoldingElement.isEmpty && interaction.gradingElements.isEmpty then editor
    else new HtmlAppElement {
      override def dismissOnOutsideClick: Boolean = editor.dismissOnOutsideClick

      override def getDomElement(): Element = div(cls := "interaction-editor",
        InteractionInfoPanel(interaction).getDomElement(),
        div(cls := "interaction-editor__content", editor.getDomElement()))
    }

  /* CSS info */

  protected val interactionInfoCssString: String = "interaction-element-info"

  /* Tooltip functionality */

  def infoPanelIcon(additionalCssStr: String, svgIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement], tooltipSignal: Signal[String], onMouseClick: Option[MouseEvent => Any]): ReactiveHtmlElement[HTMLDivElement] = {
    val isHovering: Var[Boolean] = Var(false)
    div(
      onMouseOver.mapTo(true) --> isHovering,
      onMouseOut.mapTo(false) --> isHovering,
      cls <-- isHovering.signal.map(curHovering => s"info-svg-container ${additionalCssStr} ${if (curHovering && onMouseClick.nonEmpty) "hovering" else ""}"),
      LaminarRenderHelper.singleton.createTooltip(tooltipSignal),
      svgIcon,
      onClick --> (mouseEvent => if (onMouseClick.nonEmpty) onMouseClick.get.apply(mouseEvent))
    )
  }


}
