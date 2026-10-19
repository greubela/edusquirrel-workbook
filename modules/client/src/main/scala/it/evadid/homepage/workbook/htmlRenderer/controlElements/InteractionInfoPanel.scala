package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.{child, svg as svgtag, *}
import com.raquo.laminar.nodes.{ReactiveHtmlElement, ReactiveSvgElement}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp.fullInfo
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.workbook.htmlRenderer.LaminarRenderHelper
import it.evadid.homepage.workbook.htmlRenderer.controlElements.InteractionInfoPanel.*
import it.evadid.workbook.abstractions.TypeOfTextDisplay.{PLAINTEXT, PLAINTEXT_UNDERSCORE_REPLACABLE}
import it.evadid.workbook.abstractions.{WorkbookInteractionElementFull, WorkbookInteractionUntyped}
import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
import org.scalajs.dom.{HTMLDivElement, SVGPathElement, SVGSVGElement}
import java.util.UUID

case class InteractionInfoPanel(workbookInteractionElement: WorkbookInteractionUntyped) extends HtmlAppElement {

  private val expanded = Var(false)
  private val hintId = s"interaction-hint-${UUID.randomUUID()}"
  private def label(key: String): Signal[String] =
    fullInfo.signals.stringFromLanguageMapId(LanguageMapContentId(s"basic/$key"))
  private lazy val hintAction = expanded.signal.flatMapSwitch(open => label(if open then "scaffoldingClose" else "scaffoldingOpen"))

  private def closeHints(): Unit = {
    expanded.set(false)
    hintButton.ref.focus()
  }

  private lazy val hintButton = button(
    typ := "button", cls := "interaction-info__hint-toggle",
    aria.label <-- hintAction, title <-- hintAction,
    aria.expanded <-- expanded.signal, aria.controls := hintId,
    svgtag.svg(svgtag.cls := "interaction-info__hint-icon", svgtag.viewBox := "0 0 24 24",
      svgtag.fill := "none", svgtag.stroke := "currentColor", svgtag.strokeWidth := "1.7",
      svgtag.path(svgtag.d := "M9 18h6M10 21h4M8.5 14.5a6 6 0 1 1 7 0c-.9.7-1.5 1.3-1.5 3.5h-4c0-2.2-.6-2.8-1.5-3.5Z")),
    span(child.text <-- label("scaffoldingLabel")),
    onClick --> (_ => expanded.update(!_)))

  private def scaffoldingPanel[T, GA, SA](interaction: WorkbookInteractionElementFull[T, GA, SA]): Option[Element] =
    interaction.scaffoldingElement.map { scaffolder =>
      val variable = interaction.interactionVariable
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

  private def hintBody(): Element = sectionTag(
    idAttr := hintId, cls := "interaction-info__hint-body", aria.label <-- label("scaffoldingTitle"),
    onMountCallback(ctx => Option(ctx.thisNode.ref.querySelector(".interaction-scaffolding__request"))
      .foreach(_.asInstanceOf[org.scalajs.dom.html.Element].focus())),
    onKeyDown.filter(_.key == "Escape") --> { event =>
      event.preventDefault()
      event.stopPropagation()
      closeHints()
    },
    div(cls := "interaction-info__hint-heading",
      h3(child.text <-- label("scaffoldingTitle")),
      button(typ := "button", cls := "interaction-info__hint-close",
        aria.label <-- label("scaffoldingClose"), title <-- label("scaffoldingClose"),
        "×", onClick --> (_ => closeHints()))),
    scaffoldingPanel(workbookInteractionElement))

  lazy val syncReport = fullInfo.syncControl.createObservableReport(workbookInteractionElement.interactionVariable).toEventStream()

  lazy val domElement: Element = {
    div(
      cls := interactionInfoCssString,
      div(cls := "interaction-info__icons",
        div(cls := "current-grading-icon"),
        div(cls := "sync-report-icon", child <-- syncReport.map(deriveSyncSymbol)),
        Option.when(workbookInteractionElement.scaffoldingElement.nonEmpty)(hintButton)),
      child.maybe <-- expanded.signal.map(open => Option.when(open)(hintBody()))
    )
  }

  override def getDomElement(): Element = domElement



  def deriveSyncSymbol(report: InteractionVariableSyncReport[?, ?, ?]): Element = {

    val svgSyncIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement] =
      if (report.allSyncLocations.isEmpty) createCloudDisabledSvg()
      else if (report.latestStateIsSyncedTo.isEmpty) createCloudUnsyncedSvg()
      else if (report.latestStateIsSyncedTo.size == report.allSyncLocations.size) createCloudFullySyncedSvg()
      else createCloudPartiallySyncedSvg()

    val svgSyncTooltip: Signal[String] =
      if (report.allSyncLocations.isEmpty) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipDisabled", PLAINTEXT, List())
      else if (report.latestStateIsSyncedTo.isEmpty) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipWarning", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.allSyncLocations.size.toString))
      else if (report.latestStateIsSyncedTo.size == report.allSyncLocations.size) laminarHelper.contentIdStringSignal("basic/syncInfoTooltipFull", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.allSyncLocations.size.toString))
      else laminarHelper.contentIdStringSignal("basic/syncInfoTooltipPartial", PLAINTEXT_UNDERSCORE_REPLACABLE, List[String](report.latestStateIsSyncedTo.size.toString, report.allSyncLocations.size.toString))

    InteractionInfoPanel.infoPanelIcon("sync-info", svgSyncIcon, svgSyncTooltip)


  }
}

object InteractionInfoPanel {

  def withEditor(interaction: WorkbookInteractionUntyped, editor: HtmlAppElement): HtmlAppElement =
    if interaction.scaffoldingElement.isEmpty then editor
    else new HtmlAppElement {
      override def dismissOnOutsideClick: Boolean = editor.dismissOnOutsideClick
      override def getDomElement(): Element = div(cls := "interaction-editor",
        InteractionInfoPanel(interaction).getDomElement(),
        div(cls := "interaction-editor__content", editor.getDomElement()))
    }

  /* CSS info */

  protected val interactionInfoCssString: String = "interaction-element-info"

  /* Tooltip functionality */

  def infoPanelIcon(additionalCssStr: String, svgIcon: ReactiveSvgElement[org.scalajs.dom.SVGSVGElement], tooltipSignal: Signal[String]): ReactiveHtmlElement[HTMLDivElement] = div(
    cls := s"info-svg-container ${additionalCssStr}",
    LaminarRenderHelper.singleton.createTooltip(tooltipSignal),
    svgIcon,
  )

  /* Cloud Sync Elements */


  private def createCloudSyncSvg(additionalCssStr: String, paths: List[ReactiveSvgElement[SVGPathElement]]): ReactiveSvgElement[SVGSVGElement] = {
    svgtag.svg(
      svgtag.cls := s"info-svg sync-info-svg $additionalCssStr",
      svgtag.viewBox := "0 0 15 12"
    ).amend(paths*)
  }

  private def createSvgPath(pathCssString: String, dString: String): ReactiveSvgElement[SVGPathElement] = {
    svgtag.path(
      svgtag.cls := s"svg-path ${pathCssString}",
      svgtag.d := dString
    )
  }


  def createCloudFullySyncedSvg(): ReactiveSvgElement[SVGSVGElement] = {
    createCloudSyncSvg("sync-info-svg-fully", List(
      pathCloudSmall, pathDecoTick
    ))
  }

  def createCloudPartiallySyncedSvg(): ReactiveSvgElement[SVGSVGElement] = {
    createCloudSyncSvg("sync-info-svg-fully", List(
      pathCloudSmall, pathDecoExclamationLine, pathDecoExclamationCircle
    ))
  }

  def createCloudUnsyncedSvg(): ReactiveSvgElement[SVGSVGElement] = {
    createCloudSyncSvg("sync-info-svg-fully", List(
      pathCloudSmall, pathDecoCross
    ))
  }

  def createCloudDisabledSvg(): ReactiveSvgElement[SVGSVGElement] = {
    createCloudSyncSvg("sync-info-svg-fully", List(
      pathCloudSmall, pathDecoMissing
    ))
  }

  def pathCloudSmall: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-cloud-small", "M 7.8410257,0.01089608 A 5.2633304,5.2909292 0 0 0 2.2833358,4.6140038 3.0076174,3.0233881 0 0 0 1.9976271,10.396234 0.75190436,0.75584704 0 1 0 2.4788602,8.98283 1.5038088,1.5116942 0 0 1 1.4863465,7.5467055 1.5038088,1.5116942 0 0 1 2.9901408,6.0350268 0.75190436,0.75584704 0 0 0 3.7420595,5.2791646 3.7595217,3.7792351 0 0 1 11.05806,4.0697947 a 0.75190436,0.75584704 0 0 0 0.586486,0.4988583 2.255713,2.267541 0 0 1 0.180456,4.414177 0.75775582,0.76172919 0 0 0 0.187991,1.511679 h 0.187992 A 3.7595217,3.7792351 0 0 0 12.328793,3.2232755 V 3.1779246 A 5.2633304,5.2909292 0 0 0 7.8410257,0.01089608 Z")

  def pathDecoMissing: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-missing", "M 7.1249997,7.575 A 2.25,2.25 0 0 0 4.875,9.825 2.25,2.25 0 0 0 7.1249997,12.075 2.25,2.25 0 0 0 9.375,9.825 2.25,2.25 0 0 0 7.1249997,7.575 Z m 0.01692,1.1052627 a 1.1785715,1.1785715 0 0 1 0.287594,0.039471 L 6.0535736,10.090035 A 1.1785715,1.1785715 0 0 1 6.0141014,9.80808 1.1278196,1.1278196 0 0 1 7.1419213,8.6802597 Z M 8.2302663,9.526128 a 1.1785715,1.1785715 0 0 1 0.039471,0.287594 1.1278196,1.1278196 0 0 1 -0.3327065,0.789474 1.1278196,1.1278196 0 0 1 -1.077068,0.281955 z")

  def pathDecoCross: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-cross", "m 8.6221134,7.575 a 0.74855308,0.74855402 0 0 1 0.5314582,0.2214527 0.74855308,0.74855402 0 0 1 0,1.0629173 L 8.1804818,9.825005 9.1535716,10.79064 a 0.74855308,0.74855402 0 0 1 0,1.062962 0.74855308,0.74855402 0 0 1 -1.0629163,0 L 7.1250219,10.880466 6.1593884,11.853602 a 0.74855308,0.74855402 0 0 1 -1.0629599,0 0.74855308,0.74855402 0 0 1 0,-1.062962 L 6.0695621,9.825005 5.0964285,8.85937 A 0.75161586,0.75161681 0 0 1 6.1593884,7.7964527 L 7.1250219,8.769544 8.0906553,7.7964527 A 0.74855308,0.74855402 0 0 1 8.6221134,7.575 Z")

  def pathDecoTick: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-tick", "m 9.3745651,7.575 a 0.74985501,0.74979885 0 0 1 0.5323824,0.2217759 0.74985501,0.74979885 0 0 1 0,1.0572168 L 6.9075573,11.853203 a 0.74985501,0.74979885 0 0 1 -1.0648091,0 l -1.499696,-1.499628 a 0.74985501,0.74979885 0 0 1 0,-1.057216 0.74985501,0.74979885 0 0 1 1.0647649,0 l 0.9673131,0.96724 2.4670534,-2.4668231 A 0.74985501,0.74979885 0 0 1 9.3745651,7.575 Z")

  def pathDecoExclamationLine: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-exclamation-line", "m 7.4942629,6.7936909 a 0.74845709,0.74845709 0 0 0 -0.748472,0.748472 v 2.245372 a 0.74847206,0.74847206 0 0 0 1.496944,0 v -2.245372 a 0.74845709,0.74845709 0 0 0 -0.748472,-0.748472 z")

  def pathDecoExclamationCircle: ReactiveSvgElement[SVGPathElement] = createSvgPath("path-deco-exclamation-circle", "m 7.4929037,11.285837 a 0.74845709,0.74845709 0 0 0 -0.5300304,0.215679 0.78587994,0.78587994 0 0 0 -0.2170824,0.53139 0.74845709,0.74845709 0 0 0 0.2170824,0.53139 0.86072565,0.86072565 0 0 0 0.2469909,0.157176 A 0.74845709,0.74845709 0 0 0 8.1828289,11.748507 0.74845709,0.74845709 0 0 0 8.0256529,11.501516 0.86072565,0.86072565 0 0 0 7.7786616,11.34434 0.74845709,0.74845709 0 0 0 7.4929037,11.28583 Z")


}
