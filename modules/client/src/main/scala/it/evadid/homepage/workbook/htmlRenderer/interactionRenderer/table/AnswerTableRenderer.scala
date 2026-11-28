package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.table

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.basic.LabelSelection
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
  override protected def createRendering(fullInfo: FullInfo, e: AnswerTableInteraction): AtomarLineRendering = {
    val state = e.interactionVariable.createBoundStateWithUpdateImportance(UpdateImportance.MAJOR).toAirstreamVar
    val disabledSignal = e.isDisabledState.toAirstreamVar.signal
    // Cells that a failed check found wrong are marked until the answer changes.
    val wrong = fullInfo.reporters.reportersFor(e).grading.checks.map(_.wrongParts).distinct
    def cell(row: Int, column: Int, config: AnswerTableCell): Element = config match {
      case FixedTableCell(content) => td(InlineMarkdown.element(laminarHelper(fullInfo).plaintextStringSignal(content)))
      case editable: EditableTableCell =>
        val index = e.editableIndex(row, column).get
        val label = laminarHelper(fullInfo).plaintextStringSignal(e.rowLabels(row))
          .combineWith(laminarHelper(fullInfo).plaintextStringSignal(e.columnLabels(column))).map((r, c) => s"$r · $c")
        val valueSignal = state.signal.map(_.values(index))
        val control = if editable.choices.nonEmpty then select(
          aria.label <-- label, disabled <-- disabledSignal, value <-- valueSignal,
          onChange.mapToValue --> (value => state.update(e.update(_, row, column, value))),
          option(value := "", text <-- laminarHelper(fullInfo).plaintextStringSignal("digitalWorkbooks/tableChoose")),
          editable.choices.map(choice => option(value := choice, choice)))
        else input(typ := "text", aria.label <-- label, disabled <-- disabledSignal,
          controlled(value <-- valueSignal, onInput.mapToValue --> (value => state.update(e.update(_, row, column, value)))))
        td(cls("answer-part--wrong") <-- wrong.map(_.contains(index)), control)
    }
    val dom = div(cls := "answer-table",
      div(cls := "answer-table__scroll", table(
        caption(InlineMarkdown.element(laminarHelper(fullInfo).plaintextStringSignal(e.caption))),
        thead(tr(th(text <-- e.rowHeader.fold(laminarHelper(fullInfo).plaintextStringSignal("digitalWorkbooks/tableRow"))(id => laminarHelper(fullInfo).plaintextStringSignal(id))),
          e.columnLabels.map(id => th(headerScope := "col", InlineMarkdown.element(laminarHelper(fullInfo).plaintextStringSignal(id)))))),
        tbody(e.rows.zipWithIndex.map((cells, row) => tr(
          th(headerScope := "row", InlineMarkdown.element(laminarHelper(fullInfo).plaintextStringSignal(e.rowLabels(row)))),
          cells.zipWithIndex.map((config, column) => cell(row, column, config)))))
      )),
      p(cls := "answer-table__feedback", role := "status", aria.live := "polite",
        text <-- LabelSelection(state.signal, Seq("tableSaved", "tableIncomplete"), key => laminarHelper(fullInfo).plaintextStringSignal(s"digitalWorkbooks/$key"))(
          answer => Some(if e.isAnswered(answer) then "tableSaved" else "tableIncomplete")))
    )
    AtomarLineRendering.basicLine(fullInfo, e, dom)
  }
}
