package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.table

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.keys.HtmlAttr
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.InlineMarkdown
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.table.*
import it.evadid.workbook.interaction.sync.UpdateImportance

case object AnswerTableRenderer extends LineBasedRenderingFactory[AnswerTableInteraction] {
  private val headerScope = new HtmlAttr[String]("scope", StringAsIsCodec)
  override protected def createRendering(e: AnswerTableInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR, fullInfo.workbookContextInformation).toAirstreamVar
    val disabledSignal = e.isDisabledState.toAirstreamVar.signal
    def cell(row: Int, column: Int, config: AnswerTableCell): Element = config match {
      case FixedTableCell(content) => td(InlineMarkdown.element(laminarHelper.plaintextStringSignal(content)))
      case editable: EditableTableCell =>
        val index = e.editableIndex(row, column).get
        val label = laminarHelper.plaintextStringSignal(e.rowLabels(row))
          .combineWith(laminarHelper.plaintextStringSignal(e.columnLabels(column))).map((r, c) => s"$r · $c")
        val valueSignal = state.signal.map(_.values(index))
        val control = if editable.choices.nonEmpty then select(
          aria.label <-- label, disabled <-- disabledSignal, value <-- valueSignal,
          onChange.mapToValue --> (value => state.update(e.update(_, row, column, value))),
          option(value := "", text <-- laminarHelper.plaintextStringSignal("digitalWorkbooks/tableChoose")),
          editable.choices.map(choice => option(value := choice, choice)))
        else input(typ := "text", aria.label <-- label, disabled <-- disabledSignal,
          controlled(value <-- valueSignal, onInput.mapToValue --> (value => state.update(e.update(_, row, column, value)))))
        td(control)
    }
    val dom = div(cls := "answer-table",
      div(cls := "answer-table__scroll", table(
        caption(InlineMarkdown.element(laminarHelper.plaintextStringSignal(e.caption))),
        thead(tr(th(text <-- e.rowHeader.fold(laminarHelper.plaintextStringSignal("digitalWorkbooks/tableRow"))(id => laminarHelper.plaintextStringSignal(id))),
          e.columnLabels.map(id => th(headerScope := "col", InlineMarkdown.element(laminarHelper.plaintextStringSignal(id)))))),
        tbody(e.rows.zipWithIndex.map((cells, row) => tr(
          th(headerScope := "row", InlineMarkdown.element(laminarHelper.plaintextStringSignal(e.rowLabels(row)))),
          cells.zipWithIndex.map((config, column) => cell(row, column, config)))))
      )),
      p(cls := "answer-table__feedback", role := "status", aria.live := "polite",
        text <-- state.signal.map(answer => if e.isAnswered(answer) then "tableSaved" else "tableIncomplete")
          .flatMapSwitch(key => laminarHelper.plaintextStringSignal(s"digitalWorkbooks/$key")))
    )
    AtomarLineRendering.basicLine(e, dom)
  }
}
