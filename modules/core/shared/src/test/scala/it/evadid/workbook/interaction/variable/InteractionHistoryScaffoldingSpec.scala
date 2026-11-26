package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.InteractionFormat.TextInputWithLlmGradingAndScaffoldingFormat
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus.{ScaffoldingFeedback, ScaffoldingRequestedButNotFinished}
import it.evadid.workbook.interaction.sync.UpdateImportance
import munit.FunSuite

import java.time.LocalDateTime

class InteractionHistoryScaffoldingSpec extends FunSuite {
  private type F = TextInputWithLlmGradingAndScaffoldingFormat.type
  private val t0 = LocalDateTime.of(2026, 3, 1, 9, 0)
  private def started(time: LocalDateTime) =
    ScaffoldingResult[String, MessengerModel]("answer", ScaffoldingRequestedButNotFinished(), FeedbackEntity.LlmEntity, None, time)
  private def finished(time: LocalDateTime, text: String = "hint") =
    ScaffoldingResult[String, MessengerModel]("answer", ScaffoldingFeedback(LanguageMap.universalMap(text)), FeedbackEntity.LlmEntity, None, time)

  test("scaffolding requests and results are recorded once and the latest lifecycle event wins") {
    val history = InteractionVariableHistory.empty[F]
      .addStates(Seq(InteractionVariableState("answer", UpdateImportance.MAJOR, t0)))
      .addScaffoldings(Seq(started(t0.plusSeconds(1)), started(t0.plusSeconds(1))))
    assertEquals(history.size, 2)
    assertEquals(history.scaffoldingForState("answer").map(_.scaffoldingStatus), Some(ScaffoldingRequestedButNotFinished()))
    val done = history.addScaffolding(finished(t0.plusSeconds(2))).addScaffolding(finished(t0.plusSeconds(2)))
    assertEquals(done.size, 3)
    assertEquals(done.scaffoldingForState("answer").map(_.scaffoldingStatus), Some(ScaffoldingFeedback(LanguageMap.universalMap("hint"))))
    assertEquals(done.scaffoldingForState("other"), None)
  }

  test("histories merge independent pieces and can be rebuilt from results") {
    val a = InteractionVariableHistory.empty[F].addState(InteractionVariableState("answer", UpdateImportance.MAJOR, t0))
    val b = InteractionVariableHistory.empty[F].addScaffolding(finished(t0.plusSeconds(1)))
    val c = InteractionVariableHistory.empty[F].addState(InteractionVariableState("draft", UpdateImportance.TEMPORARY, t0.plusSeconds(2)))
    val merged = a.mergeWith(Seq(b, c))
    assertEquals(merged.size, 3)
    assertEquals(merged.cleanedHistory(), merged, "the current temporary answer stays")
    val committed = merged.addState(InteractionVariableState("final", UpdateImportance.MAJOR, t0.plusSeconds(3)))
    assertEquals(committed.cleanedHistory().size, 3, "a superseded temporary answer is dropped")
    val rebuilt = InteractionVariableHistory.fromResults[F](Set(InteractionVariableState("answer", UpdateImportance.MAJOR, t0)),
      scaffoldings = Set(finished(t0.plusSeconds(1))))
    assertEquals(rebuilt.size, 2)
    val fromHistories = InteractionVariableHistory.fromResults[F](rebuilt.historyStates, rebuilt.historyGrading, rebuilt.historyScaffolding)
    assertEquals(fromHistories.scaffoldingForState("answer"), rebuilt.scaffoldingForState("answer"))
  }
}
