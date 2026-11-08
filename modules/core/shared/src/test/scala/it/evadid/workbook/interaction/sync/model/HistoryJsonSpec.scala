package it.evadid.workbook.interaction.sync.model

import it.evadid.workbook.abstractions.scaffolding.ScaffoldingStatus

import it.evadid.workbook.support.TestExecutionContext
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.workbook.interaction.variable.{InteractionVariableStateHistory, InteractionVariableGradingHistory, InteractionVariableScaffoldingHistory}

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.workbook.abstractions.FeedbackEntity
import it.evadid.workbook.abstractions.grading.{GradedState, GradingResult, GradingStatus, WorkbookInteractionGrader}
import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.gpt.{TextInputLlmGrader, TextInputWithScaffoldingAndGrading}
import it.evadid.workbook.interaction.sync.{SyncInformation, SyncStrategy, UsageContext}
import it.evadid.workbook.elements.interactionElements.slideshow.{SlideshowGrader, SlideshowState}
import it.evadid.workbook.interaction.sync.UpdateImportance
import it.evadid.workbook.interaction.variable.{InteractionVariableStateHistory, InteractionVariableGradingHistory, InteractionVariableScaffoldingHistory, InteractionVariableHistory, InteractionVariableState}
import it.evadid.workbook.jsonFactory.WorkbookElementReference
import munit.FunSuite
import upickle.default.*

import java.time.LocalDateTime

class HistoryJsonSpec extends FunSuite {
  private val time = LocalDateTime.of(2026, 1, 1, 0, 0)

  test("histories retain registered graders and scaffolding through JSON and MessagePack") {
    given ReadWriter[Nothing] = readwriter[ujson.Value].bimap[Nothing](
      (_: Nothing) => throw IllegalArgumentException("A grading payload cannot contain Nothing."),
      (_: ujson.Value) => throw IllegalArgumentException("A grading payload cannot contain Nothing."))
    type History = InteractionVariableHistory[SlideshowState, Nothing, String]
    val state = SlideshowState(List(WorkbookElementReference("slide-Grüße", None)), Set.empty)
    val grader: WorkbookInteractionGrader[SlideshowState, Nothing] = SlideshowGrader(true)
    val grading = GradingResult[SlideshowState, Nothing](grader, state, GradingStatus.Correct(), feedbackInformation = None, additionalData = None, gradingTime = time)
    val scaffolding = ScaffoldingResult(state, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[AppLanguage.HumanLanguage]("Try again")), FeedbackEntity.TestEntity(), Some("public observation"))
    val original = InteractionVariableHistory[SlideshowState, Nothing, String](InteractionVariableStateHistory[SlideshowState](Set(InteractionVariableState(state, UpdateImportance.MAJOR, time))), InteractionVariableGradingHistory[SlideshowState, Nothing](Map(state -> GradedState(state, Map(grading.grader -> grading)))), InteractionVariableScaffoldingHistory[SlideshowState, String](Map(state -> scaffolding)))
    val restored = List(read[History](write[History](original)),
      readBinary[History](writeBinary[History](original)))
    restored.foreach { history =>
      assertEquals(history, original)
      assertEquals(history.historyGrading.gradingsMap(state).testsWithResults.head.grader, grader)
      assertEquals(history.historyGrading.gradingsMap(state).timestampLatestTest, time)
      assertEquals(history.historyScaffolding.scaffoldingMap(state).forState, state)
    }
  }

  private def history[T](state: T, seconds: Int) = {
    val feedback = ScaffoldingResult(state, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[AppLanguage.HumanLanguage]("Try again")), FeedbackEntity.TestEntity(), Some(List(1, 2, 3)), scaffoldingTime = time.plusSeconds(seconds))
    InteractionVariableHistory[T, Int, List[Int]](InteractionVariableStateHistory[T](Set(InteractionVariableState(state, UpdateImportance.MAJOR, time.plusSeconds(seconds)))), new InteractionVariableGradingHistory[T, Int](gradingsMap = Map.empty), InteractionVariableScaffoldingHistory[T, List[Int]](Map(state -> feedback)))
  }

  test("grading and hint timestamps synchronize feedback without inventing answer edits") {
    val task = LanguageMapContentId("test/task")
    val criterion = LanguageMapContentId("test/criterion")
    val element = TextInputWithScaffoldingAndGrading("answer", task, contentGrading = List(TextInputWithScaffoldingAndGrading.ContentForLlmInteraction(criterion, Map.empty)))
    val state = "unchanged answer"
    val event = InteractionVariableState(state, UpdateImportance.MAJOR, time)
    val grade = GradingResult[String, MessengerModel](element.gradingElements.head, state, GradingStatus.Correct(), feedbackInformation = None, additionalData = None, gradingTime = time.plusSeconds(10))
    val graded = InteractionVariableHistory[String, MessengerModel, MessengerModel](InteractionVariableStateHistory[String](Set(event)), new InteractionVariableGradingHistory[String, MessengerModel](Map(state -> GradedState(state, Map(grade.grader -> grade)))), new InteractionVariableScaffoldingHistory[String, MessengerModel](scaffoldingMap = Map.empty))
    val location = SyncInformation(null, SyncStrategy.SYNC_LAST).forContext(UsageContext("p", "s", "u"), _ => null)
    val variable = element.interactionVariable
    variable.updateHistory(_ => graded, ExecutionContextInformation.default)
    assertEquals(graded.timestampLastChanged, Some(grade.gradingTime))
    assertEquals(HistoryJson.lastTimestamp(write(graded)), Some(grade.gradingTime))
    assertEquals(location.dataToStore(variable).head.timestamp, grade.gradingTime)
    val hint = ScaffoldingResult[String, MessengerModel](state, ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[AppLanguage.HumanLanguage]("A hint")), FeedbackEntity.LlmEntity(), None, scaffoldingTime = time.plusSeconds(20))
    val withHint = graded.copy(historyScaffolding = InteractionVariableScaffoldingHistory(Map(state -> hint)))
    variable.updateHistory(_ => withHint, ExecutionContextInformation.default)
    assertEquals(location.dataToStore(variable).head.timestamp, hint.scaffoldingTime)
    assertEquals(HistoryJson.lastTimestamp(HistoryJson.stored("answer_history",write(withHint))), Some(hint.scaffoldingTime))
    assertEquals(variable.currentHistory.historyStates.events, Set(event), "feedback never creates a fake edit")
    type History = InteractionVariableHistory[String, MessengerModel, MessengerModel]
    val key = location.usageContext.toSyncContext(variable.keyForSerialization)
    val stale = SyncStatus[it.evadid.workbook.interaction.sync.SyncContext, History](Some(grade.gradingTime), key, None)
    val report = it.evadid.workbook.meta.InteractionVariableSyncReport(
      withHint, Map(location -> stale), List(location))
    assertEquals(report.latestSyncedAt(location), false)
    assertEquals(report.copy(curRemoteHistory = Map(location -> stale.copy(lastCacheRequest = Some(hint.scaffoldingTime))))
      .latestSyncedAt(location), true)
    val fetched = FetchResponse.fromMap(time.plusMinutes(1), Map("answer" -> write(withHint)), HistoryJson.lastTimestamp)
    assertEquals(fetched.fetchedValues.head.timestamp, hint.scaffoldingTime)
    val structured = InteractionVariableHistory[List[String], Int, Int](InteractionVariableStateHistory[List[String]](Set(InteractionVariableState(List(state), UpdateImportance.MAJOR, time))), new InteractionVariableGradingHistory[List[String], Int](gradingsMap = Map.empty), InteractionVariableScaffoldingHistory[List[String], Int](Map(List(state) -> hint.copy(forState = List(state), additionalInformation = Some(1)))))
    assertEquals(HistoryJson.lastTimestamp(write(structured)), Some(hint.scaffoldingTime))
  }




  test("merging histories preserves feedback with string map keys and chooses the first duplicate") {
    val first = history("Grüße", 1)
    val second = history("second", 2)
    val merged = read[InteractionVariableHistory[String, Int, List[Int]]](HistoryJson.merge(List(write(first), write(second), write(first))))
    assertEquals(merged.historyStates.events, first.historyStates.events ++ second.historyStates.events)
    assertEquals(merged.historyScaffolding.scaffoldingMap, first.historyScaffolding.scaffoldingMap ++ second.historyScaffolding.scaffoldingMap)
    assertEquals(HistoryJson.lastTimestamp(write(merged)), Some(time.plusSeconds(2)))
  }

  test("merging histories preserves feedback with structured map keys") {
    val first = history(List("a", "b"), 1)
    val second = history(List("c"), 2)
    val merged = read[InteractionVariableHistory[List[String], Int, List[Int]]](HistoryJson.merge(List(write(first), write(second))))
    assertEquals(merged.historyStates.events, first.historyStates.events ++ second.historyStates.events)
    assertEquals(merged.historyScaffolding.scaffoldingMap, first.historyScaffolding.scaffoldingMap ++ second.historyScaffolding.scaffoldingMap)
  }


  test("restoring structured keys retains typed hints through JSON and MessagePack") {
    type History = InteractionVariableHistory[List[String], Int, List[Int]]
    val local = history(List("local"), 2)
    val remote = history(List("Grüße", "source"), 1)
    List(read[History](write(remote)), readBinary[History](writeBinary(remote))).foreach { decoded =>
      val restored = local.mergeWith(List(decoded))
      assertEquals(restored.historyStates.events, local.historyStates.events ++ remote.historyStates.events)
      assertEquals(restored.historyScaffolding.scaffoldingMap, local.historyScaffolding.scaffoldingMap ++ remote.historyScaffolding.scaffoldingMap)
      assertEquals(restored.historyScaffolding.scaffoldingMap(List("Grüße", "source")).additionalInformation, Some(List(1, 2, 3)))
      assertEquals(read[History](write(restored)), restored)
      assertEquals(readBinary[History](writeBinary(restored)), restored)
      assertEquals(ujson.read(write(restored)).obj.keySet.toSet, Set("historyStates", "historyGrading", "historyScaffolding"))
    }
  }


}
