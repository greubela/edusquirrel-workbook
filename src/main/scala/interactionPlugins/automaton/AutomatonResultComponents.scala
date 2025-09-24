package interactionPlugins.automaton

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import workbook.model.display.InteractionComponent.InteractionComponentWithReactiveVars

class AutomatonScaffoldingResultComponent(resultVar: Var[Option[AutomatonScaffoldingFeedback]])
    extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      cls := "automaton-hints",
      child <-- resultVar.signal.map {
        case Some(feedback) if feedback.hints.nonEmpty =>
          ul(cls := "automaton-hint-list", feedback.hints.map(hint => li(hint)))
        case Some(_) =>
          div(cls := "automaton-empty-placeholder", "No additional guidance right now.")
        case None =>
          div(cls := "automaton-empty-placeholder", "Run guidance to analyze the automaton structure.")
      }
    )

  override def getDomElement(): L.Element = domElement
}

class AutomatonExpectedWordsComponent(stateVar: Var[AutomatonGradingState])
    extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      child <-- stateVar.signal.map { state =>
        div(
          h4("Should Accept"),
          if (state.shouldAccept.nonEmpty) ul(cls := "automaton-test-list", state.shouldAccept.map(word => li(word)))
          else div(cls := "automaton-empty-placeholder", "No positive examples provided."),
          h4("Should Reject"),
          if (state.shouldReject.nonEmpty) ul(cls := "automaton-test-list", state.shouldReject.map(word => li(word)))
          else div(cls := "automaton-empty-placeholder", "No negative examples provided.")
        )
      }
    )

  override def getDomElement(): L.Element = domElement
}

class AutomatonGradingResultComponent(resultVar: Var[Option[AutomatonGradingFeedback]])
    extends InteractionComponentWithReactiveVars {

  private val domElement =
    div(
      child <-- resultVar.signal.map {
        case Some(feedback) =>
          val total = feedback.results.length
          val passed = feedback.results.count(_.isCorrect)
          val summaryText =
            if (total == 0) "No tests configured." else s"Passed $passed of $total test${if (total == 1) "" else "s"}."
          div(
            div(cls := "automaton-test-result-summary", summaryText),
            if (feedback.results.nonEmpty) {
              div(
                cls := "automaton-test-list",
                feedback.results.map { result =>
                  val statusClass = if (result.isCorrect) "pass" else "fail"
                  val expected = if (result.expectedAccept) "accept" else "reject"
                  val actual = if (result.actualAccept) "accept" else "reject"
                  div(
                    cls := s"automaton-test-result $statusClass",
                    span(result.word),
                    span(s"expected: $expected"),
                    span(s"actual: $actual")
                  )
                }
              )
            } else div(cls := "automaton-empty-placeholder", "No detailed results available.")
          )
        case None =>
          div(cls := "automaton-empty-placeholder", "Run tests to evaluate your automaton.")
      }
    )

  override def getDomElement(): L.Element = domElement
}
