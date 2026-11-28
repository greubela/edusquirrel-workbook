package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.text

import it.evadid.workbook.control.InteractionSignals.*

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.LabelSelection
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.interactions.text.*
import it.evadid.workbook.interactions.text.UnicodeText
import it.evadid.workbook.sync.UpdateImportance

case object UnicodeComparisonRenderer extends LineBasedRenderingFactory[UnicodeComparisonInteraction] {
  override protected def createRendering(fullInfo: FullInfo, e: UnicodeComparisonInteraction): AtomarLineRendering = {
    val state = fullInfo.interactions.boundVar(e, UpdateImportance.MAJOR)
    val locked = e.isDisabledState.toAirstreamVar.signal
    def localized(key: String): Signal[String] = laminarHelper(fullInfo).plaintextStringSignal(s"digitalWorkbooks/$key")
    def inputField(first: Boolean): Element = label(
      span(text <-- localized(if first then "unicodeFirst" else "unicodeSecond")),
      textArea(maxLength := UnicodeComparisonInteraction.maxTextLength, disabled <-- locked,
        controlled(value <-- state.signal.map(a => if first then a.first else a.second),
          onInput.mapToValue --> (value => state.update(a => if first then a.copy(first = value) else a.copy(second = value))))))
    def codeTable(first: Boolean): Element = table(
      caption(text <-- localized(if first then "unicodeFirst" else "unicodeSecond")),
      thead(tr(List("unicodePosition", "unicodeGlyph", "unicodeHex", "unicodeDecimal", "unicodeValidity").map(key => th(text <-- localized(key))))),
      tbody(children <-- state.signal.map { answer =>
        UnicodeText.characters(if first then answer.first else answer.second).map { character =>
          tr(td(character.position.toString), td(code(character.glyph)), td(code(character.hexadecimal)), td(character.codePoint.toString),
            td(text <-- localized(if character.wellFormed then "unicodeScalar" else "unicodeUnpaired")),
            cls("unicode-invalid") := !character.wellFormed)
        }
      }))
    val dom = fieldSet(cls := "unicode-comparison",
      legend(text <-- laminarHelper(fullInfo).plaintextStringSignal(e.title)),
      p(text <-- localized("unicodeExplanation")),
      div(cls := "unicode-controls", inputField(true), inputField(false),
        button(typ := "button", disabled <-- locked, text <-- localized("unicodeReset"), onClick --> (_ => state.set(e.initial)))),
      p(cls := "unicode-status", role := "status", aria.live := "polite",
        text <-- LabelSelection(state.signal, Seq("unicodeEqual", "unicodeDifferent"), localized)(a =>
          Some(if a.first == a.second then "unicodeEqual" else "unicodeDifferent"))),
      div(cls := "unicode-tables", codeTable(true), codeTable(false)))
    AtomarLineRendering.basicLine(fullInfo, e, dom)
  }
}
