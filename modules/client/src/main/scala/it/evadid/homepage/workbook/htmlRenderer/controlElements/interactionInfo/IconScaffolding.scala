package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import cats.mtl.syntax.state
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.model.FullInfo
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.workbook.abstractions.{WorkbookInteractionElementFull, WorkbookInteractionUntyped}

import java.util.UUID

case class IconScaffolding(fullInfo: FullInfo, laminarHelper: LaminarRenderHelper, workbookInteractionElement: WorkbookInteractionUntyped) {

  private lazy val hintAction = expanded.signal.flatMapSwitch(curVal => {
    val curStr = if (curVal) "basic/scaffoldingClose" else "basic/scaffoldingOpen"
    laminarHelper.plaintextStringSignal(curStr)
  })

  val expanded = Var(false)
  private val hintId = s"interaction-hint-${UUID.randomUUID()}"

  private def closeHints(): Unit = {
    expanded.set(false)
    hintButton.ref.focus()
  }

  def hintBody(): Element = sectionTag(
    idAttr := hintId, cls := "interaction-info__hint-body", aria.label <-- laminarHelper.plaintextStringSignal("basic/scaffoldingTitle"),
    onMountCallback(ctx => Option(ctx.thisNode.ref.querySelector(".interaction-scaffolding__request"))
      .foreach(_.asInstanceOf[org.scalajs.dom.html.Element].focus())),
    onKeyDown.filter(_.key == "Escape") --> { event =>
      event.preventDefault()
      event.stopPropagation()
      closeHints()
    },
    div(cls := "interaction-info__hint-heading",
      h3(child.text <-- laminarHelper.plaintextStringSignal("basic/scaffoldingTitle")),
      button(typ := "button", cls := "interaction-info__hint-close",
        aria.label <-- laminarHelper.plaintextStringSignal("basic/scaffoldingClose"), title <-- laminarHelper.plaintextStringSignal("basic/scaffoldingClose"),
        "×", onClick --> (_ => closeHints()))),
    scaffoldingPanel(workbookInteractionElement))

  lazy val hintButton = button(
    typ := "button", cls := "interaction-info__hint-toggle",
    aria.label <-- hintAction,
    title <-- hintAction,
    aria.expanded <-- expanded.signal, aria.controls := hintId,
    svg.svg(svg.cls := "interaction-info__hint-icon", svg.viewBox := "0 0 24 24",
      svg.fill := "none", svg.stroke := "currentColor", svg.strokeWidth := "1.7",
      svg.path(svg.d := "M9 18h6M10 21h4M8.5 14.5a6 6 0 1 1 7 0c-.9.7-1.5 1.3-1.5 3.5h-4c0-2.2-.6-2.8-1.5-3.5Z")),
    span(child.text <-- laminarHelper.plaintextStringSignal("basic/scaffoldingLabel")),
    onClick --> (_ => expanded.update(!_)))

  private def scaffoldingPanel[T, GA, SA](interaction: WorkbookInteractionElementFull[T, GA, SA]): Option[Element] = {
    interaction.scaffoldingElement.map { scaffolder =>
      val variable = interaction.interactionVariable
      new InteractionScaffoldingPanel[T, SA](
        () => variable.currentValue, variable.observableValue.toEventStream(), fullInfo.signals.currentLanguage,
        EventStream.merge(fullInfo.signals.user.distinct.changes.map(_ => ()),
          fullInfo.signals.workbook.map(_.map(_.loadedWorkbook)).distinct.changes.map(_ => ())),
        state => interaction.interactionVariable.control.getOrCalculateScaffolding(fullInfo.executionContextInformation, state),
        result => {
        },
        InteractionScaffoldingPanel.Labels(
          laminarHelper.plaintextStringSignal("basic/scaffoldingRequest"),
          laminarHelper.plaintextStringSignal("basic/scaffoldingLoading"),
          laminarHelper.plaintextStringSignal("basic/scaffoldingDismiss"),
          laminarHelper.plaintextStringSignal("basic/scaffoldingError"),
          laminarHelper.plaintextStringSignal("basic/scaffoldingTimeout"),
          laminarHelper.plaintextStringSignal("basic/scaffoldingNotice"))
      ).getDomElement()
    }
  }


}


