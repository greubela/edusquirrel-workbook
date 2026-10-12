package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.blockchain

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.blockchain.*
import it.evadid.workbook.model.blockchain.Sha256
import it.evadid.workbook.interaction.sync.UpdateImportance

case object Sha256Renderer extends LineBasedRenderingFactory[Sha256Interaction] {
  override protected def createRendering(e: Sha256Interaction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    val disabledSignal = e.isDisabledState.toAirstreamVar.signal
    def textFor(key: String): Signal[String] = laminarHelper.plaintextStringSignal(s"blockchainworkbook/$key")
    def textField(first: Boolean): Element = label(
      span(text <-- textFor(if first then "firstInput" else "secondInput")),
      textArea(maxLength := Sha256Interaction.maxTextLength, disabled <-- disabledSignal,
        controlled(value <-- state.signal.map(a => if first then a.first else a.second),
          onInput.mapToValue --> (value => state.update(a => if first then a.copy(first = value) else a.copy(second = value))))))
    val compare = e.task == CompareSha256()
    val zeros = e.task match { case FindSha256Prefix(n) => n; case _ => 0 }
    val dom = fieldSet(cls := "sha256-interaction",
      legend(text <-- laminarHelper.plaintextStringSignal(e.title)),
      p(text <-- textFor(if compare then "shaCompareTask" else "shaPrefixTask").map(_.replace("{zeros}", zeros.toString))),
      div(cls := "hash-controls", textField(true), Option.when(compare)(textField(false)),
        button(typ := "button", disabled <-- disabledSignal, text <-- textFor("reset"), onClick --> (_ => state.set(e.initial)))),
      div(cls := "hash-results sha256-results", table(
        caption(text <-- textFor("shaResults")),
        tbody(children <-- state.signal.map(answer => e.hashes(answer).zipWithIndex.map { (hash, i) =>
          tr(th(text <-- textFor(if i == 0 then "firstInput" else "secondInput")), td(code(hash)))
        })))),
      Option.when(compare)(p(cls := "sha256-difference", role := "status", aria.live := "polite",
        text <-- state.signal.map(a => Sha256.differingBits(a.first, a.second)).combineWith(textFor("shaDifference"))
          .map((count, template) => template.replace("{count}", count.toString)))),
      p(cls := "hash-feedback", role := "status", aria.live := "polite",
        text <-- state.signal.map(a => e.isCorrect(a) match {
          case None => "shaComparisonSaved"
          case Some(true) => "correct"
          case Some(false) => "shaTryAgain"
        }).flatMapSwitch(textFor))
    )
    AtomarLineRendering.basicLine(e, dom)
  }
}
