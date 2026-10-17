package it.evadid.workbook.interaction.variable

import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import munit.FunSuite
import upickle.default.*

import java.time.LocalDateTime
import scala.collection.immutable.ListSet
import scala.concurrent.Future

class InteractionVariableGradingSpec extends FunSuite {
  private class RecordingGrader extends WorkbookInteractionGrader[String, String] {
    var requests = List.empty[(String, Boolean)]
    override val importance = GradingImportance.REQUIRED_TEST
    override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
    override val gradingEntity = FeedbackEntity.TestEntity()
    override def gradeState(state: String): Future[GradingResult[String, String]] =
      Future.successful(GradingResult(this, state, GradingStatus.Correct()))
    override def gradeStateSafe(state: String, forced: Boolean): Future[GradingResult[String, String]] = {
      requests = requests :+ (state -> forced)
      gradeState(state)
    }
  }

  private def variable(grader: RecordingGrader): InteractionVariable[String, String, String] = {
    val interaction = new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "grading-test"
      override val defaultValue = ""
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = List(grader)
    }
    interaction.interactionVariable
  }

  test("grading uses the newest timestamp when history iteration is out of order") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val time = LocalDateTime.of(2026, 10, 12, 0, 0)
    value.updateHistory(_.copy(events = ListSet(
      InteractionVariableState("older", MAJOR, time),
      InteractionVariableState("newest", MAJOR, time.plusSeconds(1))
    )))
    value.startGrading(null)
    assertEquals(grader.requests, List("newest" -> false))
  }

  test("cached grading is reused unless grading is explicitly forced") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val cached = GradingResult[String, String](grader, "", GradingStatus.Correct())
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", Set(cached)))))
    value.startGrading(null)
    assertEquals(grader.requests, Nil)
    value.startGrading(null, forceGrading = true)
    assertEquals(grader.requests, List("" -> true))
  }

  test("empty histories do not request grading") {
    val grader = new RecordingGrader
    val value = variable(grader)
    value.updateHistory(_.copy(events = Set.empty))
    value.startGrading(null, forceGrading = true)
    assertEquals(grader.requests, Nil)
  }

  import it.evadid.core.datastructures.language.LanguageMap
  import it.evadid.core.datastructures.state.State
  import it.evadid.core.datastructures.state.observable.ObservableValue
  import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
  import it.evadid.util.logging.Logger
  import it.evadid.util.logging.derived.{PrintToStdLogger, SyncLogger}
  import it.evadid.workbook.abstractions.scaffolding.ScaffoldingResult
  import it.evadid.workbook.interaction.sync.*
  import it.evadid.workbook.interaction.sync.SyncControl.InteractionVariableSyncReport
  import it.evadid.workbook.interaction.sync.destination.{SyncDestination, SyncDestinationHistory}
  import it.evadid.workbook.interaction.sync.model.{DataEntryToSync, FetchResponse, SyncDirection, SyncStatus}

  private def restoreVariable(graders: List[RecordingGrader]): InteractionVariable[String, String, String] = {
    val interaction = new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "grading-test"
      override val defaultValue = ""
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = graders
    }
    interaction.interactionVariable
  }

  private type History = InteractionVariableHistory[String, String, String]
  private val restoreTime = LocalDateTime.of(2026, 10, 17, 12, 0)
  private def event(value: String, seconds: Long, importance: UpdateImportance = MAJOR) =
    InteractionVariableState(value, importance, restoreTime.plusSeconds(seconds))
  private def hint(state: String, text: String) = ScaffoldingResult[String, String](state,
    LanguageMap.universalMap(text), FeedbackEntity.TestEntity(), None)
  private def grade(grader: RecordingGrader, state: String, seconds: Long, status: GradingStatus) =
    GradingResult(grader, state, status, gradingTime = restoreTime.plusSeconds(seconds))
  private def history(events: Set[InteractionVariableState[String]] = Set.empty,
      grades: Map[String, GradedState[String, String]] = Map.empty,
      hints: Map[String, ScaffoldingResult[String, String]] = Map.empty): History =
    InteractionVariableHistory(events, grades, hints)

  private def restoreLocation(): SyncInformationWithContext = {
    val destination = new SyncDestinationHistory {
      override def shouldBePersistant(): Boolean = true
      override def isLocal: Boolean = true
      override def fetchAll(logger: SyncLogger, context: UsageContext): Future[FetchResponse[SyncContext, String]] =
        throw AssertionError("Restore must use its existing report")
      override def storeTo(logger: SyncLogger, context: SyncContext, payload: String): Future[SyncSuccess] =
        throw AssertionError("Restore must not write")
      override def clearValues(logger: SyncLogger, context: SyncContext): Future[SyncSuccess] =
        throw AssertionError("Restore must not clear storage")
      override def clearAllValues(logger: SyncLogger, context: UsageContext): Future[SyncSuccess] =
        throw AssertionError("Restore must not clear storage")
      override protected def deserializeFromConstructorLikeString(from: ConstructorLikeReadResult): Option[SyncDestination] = None
      override protected def serializeToConstructorLikeString(): ConstructorLikeReadResult = ConstructorLikeReadResult("RestoreTest", Nil)
    }
    SyncInformation(destination, SyncStrategy.SYNC_EVERYTHING).forContext(UsageContext("test", "workbook", "user"))
  }

  private class RestoreSync(value: InteractionVariable[String, String, String], histories: List[History]) extends SyncControl {
    val stores = State(0)
    override val controlLogger = Logger.withNameAndPrefixes(printMap = PrintToStdLogger.printNothing)
    override val syncLogger = SyncLogger(controlLogger)
    override def createCurrentReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): InteractionVariableSyncReport[T, GA, SA] = {
      assert(forVariable eq value)
      val remote = histories.map { current =>
        val location = restoreLocation()
        val key = location.usageContext.toSyncContext(value.keyForSerialization)
        location -> SyncStatus(Some(restoreTime), key, Some(DataEntryToSync(key, current, restoreTime, SyncDirection.SERVER_TO_CLIENT)))
      }.toMap
      InteractionVariableSyncReport(value.history, remote, remote.keys.toList).asInstanceOf[InteractionVariableSyncReport[T, GA, SA]]
    }
    override def createObservableReport[T, GA, SA](forVariable: InteractionVariable[T, GA, SA]): ObservableValue[InteractionVariableSyncReport[T, GA, SA]] =
      throw AssertionError("Restore must not subscribe")
    override def ensureCachesAreAtLeastThisRecent(maxAge: LocalDateTime): Future[?] = throw AssertionError("Restore must not fetch")
    override def ensureCachesContainLastElementsToWrite(values: List[InteractionVariableUntyped]): Future[?] =
      throw AssertionError("Restore must not prepare writes")
    override def requestStore(values: List[InteractionVariableUntyped]): Future[?] = { stores.update(_ + 1); Future.successful(()) }
  }

  test("history restore keeps full feedback and the newest result of each grader in any source order") {
    val first = new RecordingGrader
    val second = new RecordingGrader
    val third = new RecordingGrader
    val oldFirst = grade(first, "current", 3, GradingStatus.Incorrect())
    val localSecond = grade(second, "current", 9, GradingStatus.Correct())
    val newFirst = grade(first, "current", 7, GradingStatus.Correct())
    val remoteThird = grade(third, "current", 8, GradingStatus.Incorrect())
    val local = history(Set(event("current", 10)), Map("current" -> GradedState("current", Set(oldFirst, localSecond))),
      Map("current" -> hint("current", "Local hint")))
    val sources = List(
      history(Set(event("older", 1)), Map("current" -> GradedState("current", Set(newFirst,
        grade(second, "current", 6, GradingStatus.Incorrect())))), Map("older" -> hint("older", "Older hint"))),
      history(Set(event("other", 2)), Map("current" -> GradedState("current", Set(remoteThird,
        grade(first, "current", 5, GradingStatus.PartiallyCorrect())))), Map("other" -> hint("other", "Other hint"))))
    sources.permutations.foreach { ordered =>
      val restored = local.restoreFrom(ordered)
      assertEquals(restored.events, local.events ++ sources.flatMap(_.events))
      assertEquals(restored.gradings("current").testsWithResults, Set(newFirst, localSecond, remoteThird))
      assertEquals(restored.scaffoldings, local.scaffoldings ++ sources.flatMap(_.scaffoldings))
      assertEquals(restored.restoreFrom(ordered), restored)
    }
    assertEquals(local.gradings("current").testsWithResults, Set(oldFirst, localSecond))
    assertEquals(sources.map(_.events), List(Set(event("older", 1)), Set(event("other", 2))))
  }

  test("history restore retains conflicting results tied at the latest grading time") {
    val grader = new RecordingGrader
    val correct = grade(grader, "answer", 5, GradingStatus.Correct())
    val incorrect = grade(grader, "answer", 5, GradingStatus.Incorrect())
    val older = grade(grader, "answer", 4, GradingStatus.PartiallyCorrect())
    val local = history(grades = Map("answer" -> GradedState("answer", Set(older))))
    val sources = List(correct, incorrect).map(result => history(grades = Map("answer" -> GradedState("answer", Set(result)))))
    sources.permutations.foreach(ordered =>
      assertEquals(local.restoreFrom(ordered).gradings("answer").testsWithResults, Set(correct, incorrect)))
  }

  test("history restore rejects feedback whose state does not match its map key") {
    val grader = new RecordingGrader
    val valid = grade(grader, "answer", 2, GradingStatus.Correct())
    val wrong = grade(grader, "elsewhere", 9, GradingStatus.Incorrect())
    val local = history(grades = Map("answer" -> GradedState("elsewhere", Set(wrong))),
      hints = Map("answer" -> hint("elsewhere", "Invalid local hint")))
    val remote = history(Set(event("answer", 1)), Map(
      "answer" -> GradedState("answer", Set(valid, wrong)), "wrong-key" -> GradedState("elsewhere", Set(wrong))),
      Map("answer" -> hint("answer", "Valid remote hint"), "wrong-key" -> hint("elsewhere", "Invalid remote hint")))
    val restored = local.restoreFrom(List(remote))
    assertEquals(restored.gradings, Map("answer" -> GradedState("answer", Set(valid))))
    assertEquals(restored.scaffoldings, Map("answer" -> hint("answer", "Valid remote hint")))
  }

  test("history restore keeps valid local hints and restores only unambiguous remote hints as one batch") {
    val local = history(hints = Map("local" -> hint("local", "Accepted locally")))
    val shared = hint("shared", "Same saved hint")
    val sources = List(
      history(hints = Map("local" -> hint("local", "Remote replacement"), "shared" -> shared,
        "conflict" -> hint("conflict", "First candidate"))),
      history(hints = Map("shared" -> shared, "conflict" -> hint("conflict", "Second candidate"))))
    sources.permutations.foreach { ordered =>
      val restored = local.restoreFrom(ordered)
      assertEquals(restored.scaffoldings, local.scaffoldings + ("shared" -> shared))
      assertEquals(restored.restoreFrom(ordered), restored)
    }
  }

  test("history restore retains the existing default cleanup without inventing answer events") {
    val first = event("default", 0, UpdateImportance.DEFAULT)
    val last = event("default", 1, UpdateImportance.DEFAULT)
    val local = history(Set(first), hints = Map("default" -> hint("default", "Hint only")))
    val defaults = local.restoreFrom(List(history(Set(last))))
    assertEquals(defaults.events, Set(last))
    assertEquals(defaults.scaffoldings, local.scaffoldings)
    val edited = defaults.restoreFrom(List(history(Set(event("edited", 2)))))
    assertEquals(edited.events, Set(event("edited", 2)))
    assertEquals(edited.restoreFrom(Nil), edited)
    assertEquals(local.events, Set(first))
  }

  test("executeLoad restores hints and grading results from the current typed cache report") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val result = grade(grader, "saved", 3, GradingStatus.Correct())
    val saved = history(Set(event("saved", 2)), Map("saved" -> GradedState("saved", Set(result))),
      Map("saved" -> hint("saved", "Saved hint")))
    val sync = new RestoreSync(value, List(saved))
    value.executeLoad(sync)
    assertEquals(value.history, saved)
    assertEquals(value.currentValue, "saved")
    value.executeLoad(sync)
    assertEquals(value.history, saved)
    assertEquals(sync.stores.now(), 0)
    assertEquals(grader.requests, Nil)
  }

  test("executeLoad batches conflicting remote hints without replacing local feedback or the answer") {
    val grader = new RecordingGrader
    val value = variable(grader)
    val local = history(Set(event("current", 10)), hints = Map("current" -> hint("current", "Local hint")))
    value.updateHistory(_ => local)
    val sources = List(
      history(Set(event("older", 1)), hints = Map("current" -> hint("current", "Remote hint"), "older" -> hint("older", "First"))),
      history(Set(event("older", 1)), hints = Map("older" -> hint("older", "Second"))))
    val sync = new RestoreSync(value, sources)
    value.executeLoad(sync)
    assertEquals(value.history.events, local.events ++ sources.flatMap(_.events))
    assertEquals(value.history.scaffoldings, local.scaffoldings)
    assertEquals(value.currentValue, "current")
    assertEquals(sync.stores.now(), 0)
    assertEquals(grader.requests, Nil)
    assertEquals(local.events, Set(event("current", 10)))
  }

  test("a cached foreign grader does not suppress the currently configured grader") {
    val current = new RecordingGrader
    val foreign = new RecordingGrader
    val value = variable(current)
    val cached = grade(foreign, "", 1, GradingStatus.Correct())
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", Set(cached)))))
    value.startGrading(null)
    assertEquals(current.requests, List("" -> false))
    assertEquals(foreign.requests, Nil)
  }

  test("a partially covered grading cache reruns every configured grader") {
    val first = new RecordingGrader
    val second = new RecordingGrader
    val value = restoreVariable(List(first, second))
    val cached = grade(first, "", 1, GradingStatus.Correct())
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", Set(cached)))))
    value.startGrading(null)
    assertEquals(first.requests, List("" -> false))
    assertEquals(second.requests, List("" -> false))
  }

  test("cached grading groups and individual results must both match the current answer") {
    List("group", "result").foreach { mismatch =>
      val current = new RecordingGrader
      val value = variable(current)
      val cached = grade(current, if mismatch == "result" then "another answer" else "", 1, GradingStatus.Correct())
      val grouped = GradedState(if mismatch == "group" then "another answer" else "", Set(cached))
      value.updateHistory(_.copy(gradings = Map("" -> grouped)))
      value.startGrading(null)
      assertEquals(current.requests, List("" -> false), mismatch)
    }
  }

  test("complete current grader coverage reuses the cache even when foreign results remain") {
    val first = new RecordingGrader
    val second = new RecordingGrader
    val foreign = new RecordingGrader
    val value = restoreVariable(List(first, second))
    val results = List(first, second, foreign).map(grade(_, "", 1, GradingStatus.Correct())).toSet
    value.updateHistory(_.copy(gradings = Map("" -> GradedState("", results))))
    value.startGrading(null)
    assertEquals(first.requests, Nil)
    assertEquals(second.requests, Nil)
    assertEquals(foreign.requests, Nil)
    value.startGrading(null, forceGrading = true)
    assertEquals(first.requests, List("" -> true))
    assertEquals(second.requests, List("" -> true))
    assertEquals(foreign.requests, Nil)
  }

}
