package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, ScaffoldingStatus, WorkbookInteractionScaffolder}
import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future, Promise}

class InteractionScaffoldingAndGradingControlSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val context = TestExecutionContext()

  private class CountingGrader(status: GradingStatus = GradingStatus.Correct(), failFirst: Boolean = false,
      synchronous: Boolean = false, gate: Option[Promise[Unit]] = None, retryGate: Option[Promise[Unit]] = None) extends WorkbookInteractionGrader[String, String] {
    val calls = State(0)
    override val importance = GradingImportance.REQUIRED_TEST
    override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
    override val gradingEntity = FeedbackEntity.TestEntity()
    override protected def handleGrading(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation): Future[GradingResult[String, String]] = {
      calls.update(_ + 1)
      val first = calls.now() == 1
      if first && failFirst && synchronous then throw IllegalStateException("temporary outage")
      (if first then gate else retryGate).map(_.future).getOrElse(Future.unit).flatMap { _ =>
        if first && failFirst then Future.failed(IllegalStateException("temporary outage"))
        else Future.successful(GradingResult(this, state, status))
      }
    }
  }

  private def variable(graders: List[WorkbookInteractionGrader[String, String]]) =
    new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "retry-tests"
      override val defaultValue = "answer"
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = graders
    }.interactionVariable

  for synchronous <- List(true, false) do
    test(s"retry a cached execution error without repeating completed assessments (synchronous=$synchronous)") {
      val failed = new CountingGrader(failFirst = true, synchronous = synchronous)
      val correct = new CountingGrader()
      val incorrect = new CountingGrader(GradingStatus.Incorrect())
      val value = variable(List(failed, correct, incorrect))
      value.control.getOrCalculateGrading(context).flatMap { first =>
        assert(first.graderResultMap(failed).gradingStatus.isInstanceOf[GradingStatus.TestError])
        assertEquals(value.currentHistory.historyGrading.gradingsMap("answer"), first)
        value.control.getOrCalculateGrading(context)
      }.flatMap { second =>
        assertEquals(second.graderResultMap(failed).gradingStatus, GradingStatus.Correct())
        assertEquals(second.graderResultMap(incorrect).gradingStatus, GradingStatus.Incorrect())
        assertEquals(value.currentHistory.historyGrading.gradingsMap("answer"), second)
        value.control.getOrCalculateGrading(context)
      }.map { _ =>
        assertEquals(failed.calls.now(), 2)
        assertEquals(correct.calls.now(), 1)
        assertEquals(incorrect.calls.now(), 1)
      }
    }

  test("concurrent requests share the pending attempt, including a retry") {
    val gate = Promise[Unit]()
    val retryGate = Promise[Unit]()
    val grader = new CountingGrader(failFirst = true, gate = Some(gate), retryGate = Some(retryGate))
    val value = variable(List(grader))
    val first = value.control.getOrCalculateGrading(context)
    assert(first eq value.control.getOrCalculateGrading(context))
    assertEquals(grader.calls.now(), 1)
    gate.success(())
    first.flatMap { _ =>
      val retry = value.control.getOrCalculateGrading(context)
      assert(retry eq value.control.getOrCalculateGrading(context))
      retryGate.success(())
      retry.map(_ => assertEquals(grader.calls.now(), 2))
    }
  }

  test("restored errors and absent graders are executed while restored incorrect answers are cached") {
    val failed = new CountingGrader()
    val missing = new CountingGrader()
    val incorrect = new CountingGrader(GradingStatus.Incorrect())
    val value = variable(List(failed, missing, incorrect))
    value.updateHistory(_.addGradings(List(
      GradingResult(failed, "answer", GradingStatus.TestError(it.evadid.distribution.command.SerializedException("old failure"))),
      GradingResult(incorrect, "answer", GradingStatus.Incorrect()))), context)
    value.control.getOrCalculateGrading(context).map { result =>
      assertEquals(result.testsWithResults.size, 3)
      assertEquals(failed.calls.now(), 1)
      assertEquals(missing.calls.now(), 1)
      assertEquals(incorrect.calls.now(), 0)
    }
  }

  test("a successful retry replaces an error even when both attempts have the same clock timestamp") {
    val time = java.time.LocalDateTime.of(2026, 11, 7, 0, 0)
    val grader = new CountingGrader() {
      override protected def handleGrading(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation): Future[GradingResult[String, String]] = {
        calls.update(_ + 1)
        val status = if calls.now() == 1 then
          GradingStatus.TestError(it.evadid.distribution.command.SerializedException("outage")) else GradingStatus.Correct()
        Future.successful(GradingResult(this, state, status, gradingTime = time))
      }
    }
    val value = variable(List(grader))
    value.control.getOrCalculateGrading(context).flatMap(_ => value.control.getOrCalculateGrading(context)).map { result =>
      assertEquals(result.graderResultMap(grader).gradingStatus, GradingStatus.Correct())
      assertEquals(value.currentHistory.historyGrading.gradingsMap("answer"), result)
      assertEquals(grader.calls.now(), 2)
    }
  }

  test("a changed grade does not retain feedback or payload from a superseded assessment") {
    val grader = new CountingGrader()
    val time = java.time.LocalDateTime.of(2026, 11, 7, 0, 0)
    val previous = GradingResult[String, String](grader, "answer", GradingStatus.PartiallyCorrect(),
      Some(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Teilweise richtig"))), Some("old evidence"), time)
    val current = previous.copy(gradingStatus = GradingStatus.Correct(),
      feedbackInformation = Some(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "Correct"))),
      additionalData = None, gradingTime = time.plusSeconds(1))
    List(previous.merge(current), current.merge(previous)).foreach { saved =>
      assertEquals(saved, current)
      assertEquals(saved.feedbackInformation.flatMap(_.tryGetInLanguage(German)), None)
    }
    val sameGrade = previous.copy(gradingStatus = GradingStatus.Correct())
    val combined = sameGrade.merge(current)
    assertEquals(combined.feedbackInformation.flatMap(_.tryGetInLanguage(German)), Some("Teilweise richtig"))
    assertEquals(combined.feedbackInformation.flatMap(_.tryGetInLanguage(English)), Some("Correct"))
    assertEquals(combined.additionalData, None)
  }

  test("batch history insertion selects the newest result of each grader regardless of input order") {
    val first = new CountingGrader()
    val second = new CountingGrader()
    val time = java.time.LocalDateTime.of(2026, 11, 7, 0, 0)
    val old = GradingResult[String, String](first, "answer", GradingStatus.Incorrect(), gradingTime = time)
    val current = old.copy(gradingStatus = GradingStatus.Correct(), gradingTime = time.plusSeconds(1))
    val other = GradingResult[String, String](second, "answer", GradingStatus.Incorrect(), gradingTime = time)
    List(old, current, other).permutations.foreach { results =>
      val saved = InteractionVariableGradingHistory.fromResults(results).gradingsMap("answer")
      assertEquals(saved.graderResultMap, Map[WorkbookInteractionGrader[String, String], GradingResult[String, String]](first -> current, second -> other))
    }
  }

  private def hintVariable(scaffolder: WorkbookInteractionScaffolder[String, String]) =
    new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "hint-cache-tests"
      override val defaultValue = "answer"
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val scaffoldingElement = Some(scaffolder)
    }.interactionVariable

  test("an obsolete-language hint completion is not published as cached current feedback") {
    val language = State[HumanLanguage](German)
    val pending = State(Vector.empty[Promise[ScaffoldingResult[String, String]]])
    val scaffolder = new WorkbookInteractionScaffolder[String, String] {
      override val scaffoldingEntity = FeedbackEntity.TestEntity()
      override def handleFeedbackGeneration(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation) = {
        val promise = Promise[ScaffoldingResult[String, String]]()
        pending.update(_ :+ promise)
        promise.future
      }
    }
    val value = hintVariable(scaffolder)
    val context = TestExecutionContext(language = () => language.now())
    val first = value.control.getOrCalculateScaffolding(context)
    assert(first eq value.control.getOrCalculateScaffolding(context))
    language.set(English)
    pending.now().head.success(ScaffoldingResult("answer", ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Old hint"))), FeedbackEntity.TestEntity(), None))
    first.flatMap { _ =>
      assertEquals(value.currentHistory.historyScaffolding.scaffoldingMap, Map.empty)
      val current = value.control.getOrCalculateScaffolding(context)
      assertEquals(pending.now().size, 2)
      pending.now().last.success(ScaffoldingResult("answer", ScaffoldingStatus.ScaffoldingFeedback(
        LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "Current hint"))), FeedbackEntity.TestEntity(), None))
      current.flatMap { expected =>
        assertEquals(value.currentHistory.historyScaffolding.scaffoldingMap("answer"), expected)
        value.control.getOrCalculateScaffolding(context).map { cached =>
          assertEquals(cached, expected)
          assertEquals(pending.now().size, 2)
        }
      }
    }
  }

  test("a failed cached hint can be retried even when both results have the same timestamp") {
    val calls = State(0)
    val time = java.time.LocalDateTime.of(2026, 11, 7, 0, 0)
    val scaffolder = new WorkbookInteractionScaffolder[String, String] {
      override val scaffoldingEntity = FeedbackEntity.TestEntity()
      override def handleFeedbackGeneration(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation) = {
        calls.update(_ + 1)
        val status = if calls.now() == 1 then ScaffoldingStatus.ScaffoldingError(
          it.evadid.distribution.command.SerializedException("outage")) else
          ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("A hint"))
        Future.successful(ScaffoldingResult[String, String](state, status, scaffoldingEntity, None, time))
      }
    }
    val value = hintVariable(scaffolder)
    value.control.getOrCalculateScaffolding(context).flatMap(_ => value.control.getOrCalculateScaffolding(context)).flatMap { current =>
      assert(current.scaffoldingStatus.isInstanceOf[ScaffoldingStatus.ScaffoldingFeedback])
      assertEquals(value.currentHistory.historyScaffolding.scaffoldingMap("answer"), current)
      value.control.getOrCalculateScaffolding(context).map(_ => assertEquals(calls.now(), 2))
    }
  }

  test("reading feedback status never starts a load and exposes pending cache requests") {
    val gate = Promise[Unit]()
    val grader = new CountingGrader(gate = Some(gate))
    val graded = variable(List(grader))
    assertEquals(graded.control.getCurrentStateOfGrading().get.graderResultMap(grader).gradingStatus, GradingStatus.NotRequested())
    assertEquals(grader.calls.now(), 0)
    val grading = graded.control.getOrCalculateGrading(context)
    assertEquals(graded.control.getCurrentStateOfGrading().get.graderResultMap(grader).gradingStatus, GradingStatus.NotFinished())
    gate.success(())
    val reply = Promise[ScaffoldingResult[String, String]]()
    val calls = State(0)
    val hinted = hintVariable(new WorkbookInteractionScaffolder[String, String] {
      override val scaffoldingEntity = FeedbackEntity.TestEntity()
      override def handleFeedbackGeneration(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation) = {
        calls.update(_ + 1)
        reply.future
      }
    })
    assertEquals(hinted.control.getCurrentStateOfScaffolding(), None)
    assertEquals(calls.now(), 0)
    val hint = hinted.control.getOrCalculateScaffolding(context)
    assertEquals(hinted.control.getCurrentStateOfScaffolding().get.scaffoldingStatus, ScaffoldingStatus.ScaffoldingRequestedButNotFinished())
    reply.success(ScaffoldingResult("answer", ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.universalMap[HumanLanguage]("A hint")), FeedbackEntity.TestEntity(), None))
    grading.zip(hint).map { _ =>
      assertEquals(grader.calls.now(), 1)
      assertEquals(calls.now(), 1)
    }
  }

  test("completed AsyncDataCache entries remain reusable after local history is reset") {
    val grader = new CountingGrader()
    val value = variable(List(grader))
    val hintCalls = State(0)
    val hinted = hintVariable(new WorkbookInteractionScaffolder[String, String] {
      override val scaffoldingEntity = FeedbackEntity.TestEntity()
      override def handleFeedbackGeneration(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation) = {
        hintCalls.update(_ + 1)
        Future.successful(ScaffoldingResult[String, String](state, ScaffoldingStatus.ScaffoldingFeedback(
          LanguageMap.universalMap[HumanLanguage]("Cached hint")), scaffoldingEntity, None))
      }
    })
    value.control.getOrCalculateGrading(context).zip(hinted.control.getOrCalculateScaffolding(context)).flatMap { expected =>
      value.resetLocalHistory()
      hinted.resetLocalHistory()
      assertEquals(value.control.getCurrentStateOfGrading().get, expected._1)
      assertEquals(hinted.control.getCurrentStateOfScaffolding().get, expected._2)
      value.control.getOrCalculateGrading(context).zip(hinted.control.getOrCalculateScaffolding(context)).map { reused =>
        assertEquals(reused, expected)
        assertEquals(grader.calls.now(), 1)
        assertEquals(hintCalls.now(), 1)
        assertEquals(value.currentHistory.historyGrading.gradingsMap("answer"), expected._1)
        assertEquals(hinted.currentHistory.historyScaffolding.scaffoldingMap("answer"), expected._2)
      }
    }
  }

  test("an interaction with no graders has an empty completed result") {
    variable(Nil).control.getOrCalculateGrading(context).map(result => assertEquals(result.testsWithResults, Set.empty))
  }
}
