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
      value.control.getOrCalculateGrading(context, forceGrading = true).flatMap { first =>
        assert(first.graderResultMap(failed).gradingStatus.isInstanceOf[GradingStatus.TestError])
        assertEquals(value.currentHistory.historyGrading.gradingsMap("answer"), first)
        value.control.getOrCalculateGrading(context, forceGrading = true)
      }.flatMap { second =>
        assertEquals(second.graderResultMap(failed).gradingStatus, GradingStatus.Correct())
        assertEquals(second.graderResultMap(incorrect).gradingStatus, GradingStatus.Incorrect())
        assertEquals(value.currentHistory.historyGrading.gradingsMap("answer"), second)
        value.control.getOrCalculateGrading(context, forceGrading = true)
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
    val first = value.control.getOrCalculateGrading(context, forceGrading = true)
    val joined = value.control.getOrCalculateGrading(context, forceGrading = true)
    assertEquals(grader.calls.now(), 1)
    gate.success(())
    first.flatMap { _ =>
      val retry = value.control.getOrCalculateGrading(context, forceGrading = true)
      val joinedRetry = value.control.getOrCalculateGrading(context, forceGrading = true)
      retryGate.success(())
      retry.map(_ => assertEquals(grader.calls.now(), 2))
    }
  }

  test("explicit retries execute errors and unrequested graders while retaining completed assessments") {
    val failed = new CountingGrader()
    val missing = new CountingGrader()
    val incorrect = new CountingGrader(GradingStatus.Incorrect())
    val value = variable(List(failed, missing, incorrect))
    value.updateHistory(_.addGradings(List(
      GradingResult(failed, "answer", GradingStatus.TestError(it.evadid.distribution.command.SerializedException("old failure"))),
      GradingResult(missing, "answer", GradingStatus.NotRequested()),
      GradingResult(incorrect, "answer", GradingStatus.Incorrect()))), context)
    value.control.getOrCalculateGrading(context).flatMap(_ => value.control.getOrCalculateGrading(context, forceGrading = true)).map { result =>
      assertEquals(result.testsWithResults.size, 3)
      assertEquals(failed.calls.now(), 1)
      assertEquals(missing.calls.now(), 1)
      assertEquals(incorrect.calls.now(), 0)
    }
  }

  test("forced requests join pending automatic grading without scheduling another execution") {
    val gate = Promise[Unit]()
    val automatic = new CountingGrader(gate = Some(gate))
    val manual = new CountingGrader() {
      override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
    }
    val value = variable(List(automatic, manual))
    val first = value.control.getOrCalculateGrading(context)
    val joined = value.control.getOrCalculateGrading(context, forceGrading = true)
    gate.success(())
    first.zip(joined).flatMap { (original, shared) =>
      assertEquals(shared, original)
      assertEquals(shared.graderResultMap(manual).gradingStatus, GradingStatus.NotRequested())
      assertEquals(automatic.calls.now(), 1)
      assertEquals(manual.calls.now(), 0)
      value.control.getOrCalculateGrading(context, forceGrading = true).map { explicit =>
        assertEquals(explicit.graderResultMap(manual).gradingStatus, GradingStatus.Correct())
        assertEquals(automatic.calls.now(), 1)
        assertEquals(manual.calls.now(), 1)
      }
    }
  }

  test("a successful retry replaces an error even when both attempts have the same clock timestamp") {
    val time = java.time.LocalDateTime.of(2026, 11, 7, 0, 0)
    val grader = new CountingGrader() {
      override protected def handleGrading(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation): Future[GradingResult[String, String]] = {
        calls.update(_ + 1)
        val status = if calls.now() == 1 then
          GradingStatus.TestError(it.evadid.distribution.command.SerializedException("outage")) else GradingStatus.Correct()
        Future.successful(GradingResult(this, state, status, feedbackInformation = None, additionalData = None, gradingTime = time))
      }
    }
    val value = variable(List(grader))
    value.control.getOrCalculateGrading(context, forceGrading = true).flatMap(_ => value.control.getOrCalculateGrading(context, forceGrading = true)).map { result =>
      assertEquals(result.graderResultMap(grader).gradingStatus, GradingStatus.Correct())
      assertEquals(value.currentHistory.historyGrading.gradingsMap("answer"), result)
      assertEquals(grader.calls.now(), 2)
    }
  }

  test("grading merges retain feedback in every available language") {
    val grader = new CountingGrader()
    val time = java.time.LocalDateTime.of(2026, 11, 7, 0, 0)
    val previous = GradingResult[String, String](grader, "answer", GradingStatus.PartiallyCorrect(),
      Some(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Teilweise richtig"))), Some("old evidence"), time)
    val current = previous.copy(gradingStatus = GradingStatus.Correct(),
      feedbackInformation = Some(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "Correct"))),
      additionalData = None, gradingTime = time.plusSeconds(1))
    List(previous.merge(current), current.merge(previous)).foreach { saved =>
      assertEquals(saved.gradingStatus, current.gradingStatus)
      assertEquals(saved.feedbackInformation.flatMap(_.tryGetInLanguage(German)), Some("Teilweise richtig"))
      assertEquals(saved.feedbackInformation.flatMap(_.tryGetInLanguage(English)), Some("Correct"))
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
    val old = GradingResult[String, String](first, "answer", GradingStatus.Incorrect(), feedbackInformation = None, additionalData = None, gradingTime = time)
    val current = old.copy(gradingStatus = GradingStatus.Correct(), gradingTime = time.plusSeconds(1))
    val other = GradingResult[String, String](second, "answer", GradingStatus.Incorrect(), feedbackInformation = None, additionalData = None, gradingTime = time)
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

  test("language changes preserve an in-flight hint and reuse its completed feedback") {
    val language = State[HumanLanguage](German)
    val pending = Promise[ScaffoldingResult[String, String]]()
    val calls = State(0)
    val value = hintVariable(new WorkbookInteractionScaffolder[String, String] {
      override val scaffoldingEntity = FeedbackEntity.TestEntity()
      override def handleFeedbackGeneration(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation) = {
        calls.update(_ + 1)
        pending.future
      }
    })
    val context = TestExecutionContext(language = () => language.now())
    val first = value.control.getOrCalculateScaffolding(context)
    language.set(English)
    val second = value.control.getOrCalculateScaffolding(context)
    assertEquals(calls.now(), 1)
    val original = ScaffoldingResult[String, String]("answer", ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Ein Hinweis"))), FeedbackEntity.TestEntity(), None)
    pending.success(original)
    first.zip(second).flatMap { (a, b) =>
      assertEquals(a, original)
      assertEquals(b, original)
      assertEquals(value.currentHistory.historyScaffolding.scaffoldingMap("answer"), original)
      value.control.getOrCalculateScaffolding(context).map { cached =>
        assertEquals(cached, original)
        assertEquals(calls.now(), 1)
      }
    }
  }

  test("scaffolding merges add translations without losing earlier feedback") {
    val time = java.time.LocalDateTime.of(2026, 11, 7, 0, 0)
    val german = ScaffoldingResult[String, String]("answer", ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Hinweis"))), FeedbackEntity.TestEntity(), Some("old"), time)
    val english = german.copy(scaffoldingStatus = ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "Hint"))), scaffoldingTime = time.plusSeconds(1))
    List(german.merge(english), english.merge(german),
      InteractionVariableScaffoldingHistory(Set(german, english)).scaffoldingMap("answer")).foreach { result =>
      val feedback = result.scaffoldingStatus.asInstanceOf[ScaffoldingStatus.ScaffoldingFeedback].feedback
      assertEquals(feedback.tryGetInLanguage(German), Some("Hinweis"))
      assertEquals(feedback.tryGetInLanguage(English), Some("Hint"))
      assertEquals(result.merge(result), result)
    }
  }

  test("completed hint cache entries are reused even when they contain an execution error") {
    val calls = State(0)
    val scaffolder = new WorkbookInteractionScaffolder[String, String] {
      override val scaffoldingEntity = FeedbackEntity.TestEntity()
      override def handleFeedbackGeneration(state: String, context: it.evadid.workbook.meta.ExecutionContextInformation) = {
        calls.update(_ + 1)
        Future.successful(ScaffoldingResult[String, String](state, ScaffoldingStatus.ScaffoldingError(
          it.evadid.distribution.command.SerializedException("outage")), scaffoldingEntity, None))
      }
    }
    val value = hintVariable(scaffolder)
    value.control.getOrCalculateScaffolding(context).flatMap { first =>
      value.control.getOrCalculateScaffolding(context).map { cached =>
        assertEquals(cached, first)
        assertEquals(calls.now(), 1)
      }
    }
  }

  test("reading feedback status never starts a load and exposes pending cache requests") {
    val gate = Promise[Unit]()
    val grader = new CountingGrader(gate = Some(gate))
    val graded = variable(List(grader))
    assertEquals(graded.control.getCurrentStateOfGrading("answer").graderResultMap(grader).gradingStatus, GradingStatus.NotRequested())
    assertEquals(grader.calls.now(), 0)
    val grading = graded.control.getOrCalculateGrading(context, forceGrading = true)
    assertEquals(graded.control.getCurrentStateOfGrading("answer").graderResultMap(grader).gradingStatus, GradingStatus.NotFinished())
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
    assertEquals(hinted.control.getCurrentStateOfScaffolding("answer"), None)
    assertEquals(calls.now(), 0)
    val hint = hinted.control.getOrCalculateScaffolding(context)
    assertEquals(hinted.control.getCurrentStateOfScaffolding("answer").get.scaffoldingStatus, ScaffoldingStatus.ScaffoldingRequestedButNotFinished())
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
    value.control.getOrCalculateGrading(context, forceGrading = true).zip(hinted.control.getOrCalculateScaffolding(context)).flatMap { expected =>
      value.resetLocalHistory()
      hinted.resetLocalHistory()
      assertEquals(value.control.getCurrentStateOfGrading("answer"), expected._1)
      assertEquals(hinted.control.getCurrentStateOfScaffolding("answer").get, expected._2)
      value.control.getOrCalculateGrading(context, forceGrading = true).zip(hinted.control.getOrCalculateScaffolding(context)).map { reused =>
        assertEquals(reused, expected)
        assertEquals(grader.calls.now(), 1)
        assertEquals(hintCalls.now(), 1)
        assertEquals(value.control.getCurrentStateOfGrading("answer"), expected._1)
        assertEquals(hinted.control.getCurrentStateOfScaffolding("answer"), Some(expected._2))
      }
    }
  }

  test("an interaction with no graders has an empty completed result") {
    variable(Nil).control.getOrCalculateGrading(context, forceGrading = true).map(result => assertEquals(result.testsWithResults, Set.empty))
  }
}
