package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.matching

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.InlineMarkdown
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.matching.LikertScaleInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance

/** Survey matrix: one row per statement, one radio button per scale point. On wide screens the scale labels
 * form a shared header row; on narrow screens every row shows its own labels (see `.likert-*` CSS).
 */
object HtmlLikertScaleRenderer extends LineBasedRenderingFactory[LikertScaleInteraction] {

  override protected def createRendering(e: LikertScaleInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar
    val locked = e.isDisabledState.toAirstreamVar
    val clearLabel = laminarHelper.plaintextStringSignal(LanguageMapContentId("basic/likertClear"))

    def rating(row: Int): Signal[Option[Int]] =
      state.signal.map(_.selectedRightIndex(row, e.statements.size, e.scale.size)).distinct

    val rows = e.statements.zipWithIndex.map { (statement, row) =>
      val statementId = s"${e.elementId}-statement-$row"
      div(cls := "likert-row", role := "radiogroup", aria.labelledBy := statementId,
        div(cls := "likert-row__statement", idAttr := statementId,
          InlineMarkdown.element(laminarHelper.plaintextStringSignal(statement))),
        e.scale.zipWithIndex.map { (point, column) =>
          label(cls := "likert-option",
            input(typ := "radio", nameAttr := s"${e.elementId}-$row",
              checked <-- rating(row).map(_.contains(column)),
              disabled <-- locked.signal,
              onChange.mapToChecked.filter(identity) --> (_ => state.update(e.rate(_, row, Some(column))))),
            span(cls := "likert-option__label", text <-- laminarHelper.plaintextStringSignal(point)))
        },
        button(typ := "button", cls := "likert-row__clear",
          visibility <-- rating(row).map(answer => if answer.isEmpty then "hidden" else "visible"),
          disabled <-- locked.signal.combineWith(rating(row)).map((isLocked, answer) => isLocked || answer.isEmpty),
          onClick --> (_ => state.update(e.rate(_, row, None))),
          text <-- clearLabel))
    }

    val content = div(cls := "workbook-interaction likert-interaction",
      styleProp("--likert-points") := e.scale.size.toString,
      e.instruction.map(id => div(cls := "likert-instruction", InlineMarkdown.element(laminarHelper.plaintextStringSignal(id)))),
      div(cls := "likert-header", aria.hidden := true,
        span(),
        e.scale.map(point => span(cls := "likert-header__point", text <-- laminarHelper.plaintextStringSignal(point))),
        span()),
      rows)
    AtomarLineRendering.basicLine(e, content)
  }
}
