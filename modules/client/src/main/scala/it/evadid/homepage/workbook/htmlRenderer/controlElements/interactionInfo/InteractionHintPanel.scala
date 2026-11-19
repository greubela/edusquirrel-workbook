package it.evadid.homepage.workbook.htmlRenderer.controlElements.interactionInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.English
import it.evadid.core.datastructures.state.StateHelper.RichObservableValue
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.abstractions.InteractionFormat
import it.evadid.workbook.meta.InteractionVariableGradingReporter

/** Readable, collapsible hint content shared by below-exercise feedback panels. */
case class InteractionHintPanel[F <: InteractionFormat](reporter: InteractionVariableGradingReporter[F]) extends HtmlAppElement {
  override def getDomElement(): Element = {
    val feedback = reporter.currentScaffoldingFeedbackSignal.toSignal(None)
    val available = feedback.map(_.exists(_.availableLanguages.nonEmpty)).distinct
    val text = feedback.combineWith(fullInfo.signals.currentLanguage).map { (content, language) =>
      content.filter(_.availableLanguages.nonEmpty).map(_.getWithLanguagePreference(Seq(language, English))).getOrElse("")
    }
    div(cls := "interaction-feedback interaction-hint-panel", hidden <-- available.map(!_),
      detailsTag(cls := "feedback-section",
        htmlProp("open", com.raquo.laminar.codecs.BooleanAsIsCodec) <-- available,
        summaryTag(cls := "feedback-summary",
          strong(child.text <-- laminarHelper.plaintextStringSignal("basic/SidepanelScaffoldingTitle"))),
        p(cls := "feedback-text feedback-section-content", child.text <-- text)))
  }
}
