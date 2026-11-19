package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.homepage.control.info.interaction.GradingReporter

/** Readable, collapsible hint content shared by below-exercise feedback panels. */
case class InteractionHintPanel(reporter: GradingReporter) extends HtmlAppElement {
  override def getDomElement(): Element = {
    val feedback = reporter.hintFeedback
    val available = feedback.map(_.exists(_.availableLanguages.nonEmpty)).distinct
    val text = feedback.flatMapSwitch {
      case Some(content) => laminarHelper.plaintextStringSignal(content)
      case None => Val("")
    }

    div(cls := "interaction-feedback interaction-hint-panel", hidden <-- available.map(!_),
      detailsTag(cls := "feedback-section",
        htmlProp("open", com.raquo.laminar.codecs.BooleanAsIsCodec) <-- available,
        summaryTag(cls := "feedback-summary",
          strong(child.text <-- laminarHelper.plaintextStringSignal("basic/SidepanelScaffoldingTitle"))),
        p(cls := "feedback-text feedback-section-content", child.text <-- text)))
  }
}
