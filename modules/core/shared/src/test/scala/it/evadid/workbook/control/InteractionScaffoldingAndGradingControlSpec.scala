package it.evadid.workbook.control

import it.evadid.workbook.model.history.{InteractionVariableGradingHistory, InteractionVariableScaffoldingHistory}

import it.evadid.workbook.model.InteractionScope

import it.evadid.workbook.support.OfflineController
import it.evadid.workbook.sync.SyncConnection
import it.evadid.workbook.sync.UsageContext
import it.evadid.core.datastructures.language.WorkbookContent
import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.workbook.abstractions.InteractionFormat.TextInputWithLlmGradingAndScaffoldingFormat
import it.evadid.core.datastructures.state.State
import it.evadid.core.datastructures.language.AppLanguage.{English, German, HumanLanguage}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingEvidence, ScaffoldingResult, ScaffoldingStatus, WorkbookInteractionScaffolder}
import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.interactions.basic.TextInteraction
import it.evadid.workbook.abstractions.ExecutionContextInformation
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.sync.UpdateImportance
import it.evadid.workbook.support.TestExecutionContext
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future, Promise}

class InteractionScaffoldingAndGradingControlSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val context = TestExecutionContext()
  private val time = java.time.LocalDateTime.of(2026, 11, 7, 0, 0)

  private class CountingGrader(status: GradingStatus = GradingStatus.Correct, failFirst: Boolean = false,
      synchronous: Boolean = false, gate: Option[Promise[Unit]] = None, retryGate: Option[Promise[Unit]] = None,
      behavior: GraderRunningBehavior = GraderRunningBehavior.RUN_ALWAYS) extends WorkbookInteractionGrader[String, MessengerModel] {
    override val key = java.util.UUID.randomUUID().toString
    val calls = State(0)
    override val importance = GradingImportance.REQUIRED_TEST
    override val runningBehavior = behavior
    override val gradingEntity = FeedbackEntity.TestEntity
    override protected def handleGrading(state: String, context: ExecutionContextInformation): Future[GradingResult[String, MessengerModel]] = {
      calls.update(_ + 1)
      val first = calls.now() == 1
      if first && failFirst && synchronous then throw IllegalStateException("temporary outage")
      (if first then gate else retryGate).map(_.future).getOrElse(Future.unit).flatMap { _ =>
        if first && failFirst then Future.failed(IllegalStateException("temporary outage"))
        else Future.successful(GradingResult(this, state, status))
      }
    }
  }

  private class CountingScaffolder(reply: String => Future[ScaffoldingResult[String, MessengerModel]])
      extends WorkbookInteractionScaffolder[String, MessengerModel, MessengerModel] {
    val calls = State(0)
    override val scaffoldingEntity = FeedbackEntity.TestEntity
    override def handleFeedbackGeneration(state: String, evidence: ScaffoldingEvidence.Reader[String, MessengerModel], context: ExecutionContextInformation): Future[ScaffoldingResult[String, MessengerModel]] = {
      calls.update(_ + 1)
      reply(state)
    }
  }

  private def element(id: String, graders: List[WorkbookInteractionGrader[String, MessengerModel]],
      scaffolder: Option[WorkbookInteractionScaffolder[String, MessengerModel, MessengerModel]]) =
    Exercise(new WorkbookInteractionElement[TextInputWithLlmGradingAndScaffoldingFormat.type] {
      override val elementId = id
      override val defaultValue = "answer"
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = graders
      override val scaffoldingElement = scaffolder
    })

  /** An exercise with its answers in an offline controller of its own that executes feedback with `context`. */
  private final case class Exercise(interaction: WorkbookInteractionElement[TextInputWithLlmGradingAndScaffoldingFormat.type]) {
    val controller: InteractionController = OfflineController.of(context, interaction)
    def control: InteractionScaffoldingAndGradingControl[TextInputWithLlmGradingAndScaffoldingFormat.type] = controller.feedback(interaction)
  }

  private def variable(graders: List[WorkbookInteractionGrader[String, MessengerModel]]) = element("retry-tests", graders, None)

  private def hintVariable(scaffolder: WorkbookInteractionScaffolder[String, MessengerModel, MessengerModel]) =
    element("hint-cache-tests", Nil, Some(scaffolder))

  private def feedbackIn(result: GradingResult[String, MessengerModel], language: HumanLanguage): Option[String] =
    result.feedbackInformation.collect { case WorkbookContent.LanguageMapBasedContent(map) => map }.flatMap(_.tryGetInLanguage(language))

  for synchronous <- List(true, false) do
    test(s"retry a cached execution error without repeating completed assessments (synchronous=$synchronous)") {
      val failed = new CountingGrader(failFirst = true, synchronous = synchronous)
      val correct = new CountingGrader()
      val incorrect = new CountingGrader(GradingStatus.Incorrect)
      val value = variable(List(failed, correct, incorrect))
      value.control.getOrCalculateGrading(forceGrading = true).flatMap { first =>
        assert(first.graderResultMap(failed).gradingStatus.isInstanceOf[GradingStatus.TestError])
        assert(first.graderResultMap(failed).gradingStatus.isError)
        value.control.getOrCalculateGrading(forceGrading = true)
      }.flatMap { second =>
        assertEquals(second.graderResultMap(failed).gradingStatus, GradingStatus.Correct)
        assertEquals(second.graderResultMap(incorrect).gradingStatus, GradingStatus.Incorrect)
        value.control.getOrCalculateGrading(forceGrading = true)
      }.map { _ =>
        assertEquals(failed.calls.now(), 2)
        assertEquals(correct.calls.now(), 1)
        assertEquals(incorrect.calls.now(), 1)
      }
    }

  test("unrequested grading follows each grader's behaviour for the answer's importance; a request always grades") {
    val onEveryChange = new CountingGrader()
    val onMinor = new CountingGrader(behavior = GraderRunningBehavior.RUN_ON_AT_LEAST_MINOR)
    val manual = new CountingGrader(behavior = GraderRunningBehavior.RUN_ONLY_MANUALLY)
    val value = variable(List(onEveryChange, onMinor, manual))
    def statuses(graded: GradedState[String, MessengerModel]) =
      List(onEveryChange, onMinor, manual).map(grader => graded.graderResultMap(grader).gradingStatus.isCorrect)
    value.controller.setStateFromUserInteraction(value.interaction, "half-typed", UpdateImportance.TEMPORARY)
    value.control.getOrCalculateGrading().flatMap { temporary =>
      assertEquals(statuses(temporary), List(true, false, false))
      value.controller.setStateFromUserInteraction(value.interaction, "finished word", UpdateImportance.MINOR)
      value.control.getOrCalculateGrading()
    }.flatMap { minor =>
      assertEquals(statuses(minor), List(true, true, false))
      value.controller.setStateFromUserInteraction(value.interaction, "half-typed again", UpdateImportance.TEMPORARY)
      // A request joins a pending automatic grading; requesting afterwards grades the skipped graders.
      value.control.getOrCalculateGrading().flatMap(_ => value.control.getOrCalculateGrading(forceGrading = true))
    }.map(requested => assertEquals(statuses(requested), List(true, true, true)))
  }

  test("concurrent requests share the pending attempt, including a retry") {
    val gate = Promise[Unit]()
    val retryGate = Promise[Unit]()
    val grader = new CountingGrader(failFirst = true, gate = Some(gate), retryGate = Some(retryGate))
    val value = variable(List(grader))
    val first = value.control.getOrCalculateGrading(forceGrading = true)
    val joined = value.control.getOrCalculateGrading(forceGrading = true)
    assertEquals(grader.calls.now(), 1)
    gate.success(())
    first.flatMap { _ =>
      val retry = value.control.getOrCalculateGrading(forceGrading = true)
      val joinedRetry = value.control.getOrCalculateGrading(forceGrading = true)
      retryGate.success(())
      retry.map(_ => assertEquals(grader.calls.now(), 2))
    }
  }

  test("explicit retries execute errors and unrequested graders while retaining completed assessments") {
    val failed = new CountingGrader()
    val missing = new CountingGrader()
    val incorrect = new CountingGrader(GradingStatus.Incorrect)
    val value = variable(List(failed, missing, incorrect))
    value.controller.updateHistory(value.interaction)(_.addGradings(List(
      GradingResult(failed, "answer", GradingStatus.TestError(SerializedException("old failure"))),
      GradingResult(missing, "answer", GradingStatus.NotRequested),
      GradingResult(incorrect, "answer", GradingStatus.Incorrect))))
    value.control.getOrCalculateGrading().flatMap(_ => value.control.getOrCalculateGrading(forceGrading = true)).map { result =>
      assertEquals(result.testsWithResults.size, 3)
      assertEquals(failed.calls.now(), 1)
      assertEquals(missing.calls.now(), 1)
      assertEquals(incorrect.calls.now(), 0)
    }
  }

  test("a forced request retries an error and grades a missing grader in one attempt") {
    val failed = new CountingGrader(failFirst = true)
    val manual = new CountingGrader(behavior = GraderRunningBehavior.RUN_ONLY_MANUALLY)
    val value = variable(List(failed, manual))
    value.control.getOrCalculateGrading().flatMap { automatic =>
      assert(automatic.graderResultMap(failed).gradingStatus.isError)
      assertEquals(automatic.graderResultMap(manual).gradingStatus, GradingStatus.NotRequested)
      value.control.getOrCalculateGrading(forceGrading = true)
    }.map { requested =>
      assertEquals(requested.graderResultMap(failed).gradingStatus, GradingStatus.Correct)
      assertEquals(requested.graderResultMap(manual).gradingStatus, GradingStatus.Correct)
      assertEquals((failed.calls.now(), manual.calls.now()), (2, 1))
    }
  }

  test("forced requests join pending automatic grading without scheduling another execution") {
    val gate = Promise[Unit]()
    val automatic = new CountingGrader(gate = Some(gate))
    val manual = new CountingGrader() {
      override val runningBehavior = GraderRunningBehavior.RUN_ONLY_MANUALLY
    }
    val value = variable(List(automatic, manual))
    val first = value.control.getOrCalculateGrading()
    val joined = value.control.getOrCalculateGrading(forceGrading = true)
    gate.success(())
    first.zip(joined).flatMap { (original, shared) =>
      assertEquals(shared, original)
      assertEquals(shared.graderResultMap(manual).gradingStatus, GradingStatus.NotRequested)
      assertEquals(automatic.calls.now(), 1)
      assertEquals(manual.calls.now(), 0)
      value.control.getOrCalculateGrading(forceGrading = true).map { explicit =>
        assertEquals(explicit.graderResultMap(manual).gradingStatus, GradingStatus.Correct)
        assertEquals(automatic.calls.now(), 1)
        assertEquals(manual.calls.now(), 1)
      }
    }
  }

  test("a successful retry replaces an error even when both attempts have the same clock timestamp") {
    val grader = new CountingGrader() {
      override protected def handleGrading(state: String, context: ExecutionContextInformation): Future[GradingResult[String, MessengerModel]] = {
        calls.update(_ + 1)
        val status = if calls.now() == 1 then
          GradingStatus.TestError(SerializedException("outage")) else GradingStatus.Correct
        Future.successful(GradingResult(this, state, status, feedbackInformation = None, additionalData = None, gradingTime = time))
      }
    }
    val value = variable(List(grader))
    value.control.getOrCalculateGrading(forceGrading = true).flatMap(_ => value.control.getOrCalculateGrading(forceGrading = true)).map { result =>
      assertEquals(result.graderResultMap(grader).gradingStatus, GradingStatus.Correct)
      assertEquals(grader.calls.now(), 2)
    }
  }

  test("grading merges retain feedback in every available language") {
    val grader = new CountingGrader()
    val previous = GradingResult[String, MessengerModel](grader, "answer", GradingStatus.PartiallyCorrect,
      Some(WorkbookContent(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Teilweise richtig")))), Some(MessengerModel.empty), time)
    val current = previous.copy(gradingStatus = GradingStatus.Correct,
      feedbackInformation = Some(WorkbookContent(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(English -> "Correct")))),
      additionalData = None, gradingTime = time.plusSeconds(1))
    List(previous.merge(current), current.merge(previous)).foreach { saved =>
      assertEquals(saved.gradingStatus, current.gradingStatus)
      assertEquals(feedbackIn(saved, German), Some("Teilweise richtig"))
      assertEquals(feedbackIn(saved, English), Some("Correct"))
    }
    val sameGrade = previous.copy(gradingStatus = GradingStatus.Correct)
    val combined = sameGrade.merge(current)
    assertEquals(feedbackIn(combined, German), Some("Teilweise richtig"))
    assertEquals(feedbackIn(combined, English), Some("Correct"))
    assertEquals(combined.additionalData, None)
  }

  test("batch history insertion selects the newest result of each grader regardless of input order") {
    val first = new CountingGrader()
    val second = new CountingGrader()
    val old = GradingResult[String, MessengerModel](first, "answer", GradingStatus.Incorrect, feedbackInformation = None, additionalData = None, gradingTime = time)
    val current = old.copy(gradingStatus = GradingStatus.Correct, gradingTime = time.plusSeconds(1))
    val other = GradingResult[String, MessengerModel](second, "answer", GradingStatus.Incorrect, feedbackInformation = None, additionalData = None, gradingTime = time)
    List(old, current, other).permutations.foreach { results =>
      val saved = InteractionVariableGradingHistory.fromResults(results).gradingsMap("answer")
      assertEquals(saved.graderResultMap, Map[WorkbookInteractionGrader[String, MessengerModel], GradingResult[String, MessengerModel]](first -> current, second -> other))
    }
  }

  test("language changes preserve an in-flight hint and reuse its completed feedback") {
    val language = State[HumanLanguage](German)
    val pending = Promise[ScaffoldingResult[String, MessengerModel]]()
    val scaffolder = CountingScaffolder(_ => pending.future)
    val value = hintVariable(scaffolder)
    val context = TestExecutionContext(language = () => language.now())
    val first = value.control.getOrCalculateScaffolding()
    language.set(English)
    val second = value.control.getOrCalculateScaffolding()
    assertEquals(scaffolder.calls.now(), 1)
    val original = ScaffoldingResult[String, MessengerModel]("answer", ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Ein Hinweis"))), FeedbackEntity.TestEntity, None)
    pending.success(original)
    first.zip(second).flatMap { (a, b) =>
      assertEquals(a, original)
      assertEquals(b, original)
      assertEquals(value.controller.currentHistory(value.interaction).historyScaffolding.scaffoldingMap("answer"), original)
      value.control.getOrCalculateScaffolding().map { cached =>
        assertEquals(cached, original)
        assertEquals(scaffolder.calls.now(), 1)
      }
    }
  }

  test("scaffolding merges add translations without losing earlier feedback") {
    val german = ScaffoldingResult[String, MessengerModel]("answer", ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(German -> "Hinweis"))), FeedbackEntity.TestEntity, Some(MessengerModel.empty), time)
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

  test("only an explicit hint request retries an error and concurrent retries share the result") {
    val firstReply = Promise[ScaffoldingResult[String, MessengerModel]]()
    val retryReply = Promise[ScaffoldingResult[String, MessengerModel]]()
    val scaffolder = CountingScaffolder(_ => if firstReply.isCompleted then retryReply.future else firstReply.future)
    val value = hintVariable(scaffolder)
    val initial = value.control.getOrCalculateScaffolding()
    val error = ScaffoldingResult[String, MessengerModel]("answer", ScaffoldingStatus.ScaffoldingError(SerializedException("outage")), FeedbackEntity.TestEntity, None)
    val hint = error.copy(scaffoldingStatus = ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("A hint")))
    firstReply.success(error)
    for {
      _ <- initial
      cached <- value.control.getOrCalculateScaffolding()
      (first, second) <- {
        assertEquals(cached, error)
        assertEquals(scaffolder.calls.now(), 1)
        val retry = value.control.getOrCalculateScaffolding(retryErrors = true)
        val joined = value.control.getOrCalculateScaffolding(retryErrors = true)
        assertEquals(scaffolder.calls.now(), 2)
        retryReply.success(hint)
        retry.zip(joined)
      }
      saved <- value.control.getOrCalculateScaffolding(retryErrors = true)
    } yield {
      assertEquals(List(first, second, saved), List.fill(3)(hint))
      assertEquals(scaffolder.calls.now(), 2)
    }
  }

  test("reading feedback status never starts a load and exposes pending cache requests") {
    val gate = Promise[Unit]()
    val grader = new CountingGrader(gate = Some(gate))
    val graded = variable(List(grader))
    assertEquals(graded.control.getCurrentStateOfGrading("answer").graderResultMap(grader).gradingStatus, GradingStatus.NotRequested)
    assertEquals(grader.calls.now(), 0)
    val grading = graded.control.getOrCalculateGrading(forceGrading = true)
    assertEquals(graded.control.getCurrentStateOfGrading("answer").graderResultMap(grader).gradingStatus, GradingStatus.NotFinished)
    gate.success(())
    val reply = Promise[ScaffoldingResult[String, MessengerModel]]()
    val scaffolder = CountingScaffolder(_ => reply.future)
    val hinted = hintVariable(scaffolder)
    assertEquals(hinted.control.getCurrentStateOfScaffolding("answer"), None)
    assertEquals(scaffolder.calls.now(), 0)
    val hint = hinted.control.getOrCalculateScaffolding()
    assertEquals(hinted.control.getCurrentStateOfScaffolding("answer").get.scaffoldingStatus, ScaffoldingStatus.ScaffoldingRequestedButNotFinished())
    reply.success(ScaffoldingResult("answer", ScaffoldingStatus.ScaffoldingFeedback(
      LanguageMap.universalMap[HumanLanguage]("A hint")), FeedbackEntity.TestEntity, None))
    grading.zip(hint).map { _ =>
      assertEquals(grader.calls.now(), 1)
      assertEquals(scaffolder.calls.now(), 1)
    }
  }

  test("results computed in the session are reused and recorded again after the local history is reset") {
    val grader = new CountingGrader()
    val value = variable(List(grader))
    val scaffolder = CountingScaffolder(state => Future.successful(ScaffoldingResult[String, MessengerModel](state,
      ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap[HumanLanguage]("Cached hint")), FeedbackEntity.TestEntity, None)))
    val hinted = hintVariable(scaffolder)
    value.control.getOrCalculateGrading(forceGrading = true).zip(hinted.control.getOrCalculateScaffolding()).flatMap { expected =>
      value.controller.resetLocalHistory(value.interaction)
      hinted.controller.resetLocalHistory(hinted.interaction)
      assert(value.control.getCurrentStateOfGrading("answer").testsWithResults.forall(_.gradingStatus == GradingStatus.NotRequested),
        "the status is the recorded one")
      assertEquals(hinted.control.getCurrentStateOfScaffolding("answer"), None)
      value.control.getOrCalculateGrading(forceGrading = true).zip(hinted.control.getOrCalculateScaffolding()).map { reused =>
        assertEquals(reused, expected)
        assertEquals(grader.calls.now(), 1)
        assertEquals(scaffolder.calls.now(), 1)
        assertEquals(value.control.getCurrentStateOfGrading("answer"), expected._1)
        assertEquals(hinted.control.getCurrentStateOfScaffolding("answer"), Some(expected._2))
      }
    }
  }

  test("an interaction with no graders has an empty completed result") {
    variable(Nil).control.getOrCalculateGrading(forceGrading = true).map(result => assertEquals(result.testsWithResults, Set.empty))
  }

  test("received scaffolding lifecycle events expose pending and completed states without executing feedback") {
    import it.evadid.workbook.model.history.events.*
    type F = TextInputWithLlmGradingAndScaffoldingFormat.type
    val value = variable(Nil)
    val timestamp = java.time.LocalDateTime.of(2026, 11, 17, 12, 0)
    val entity = FeedbackEntity.LlmEntity
    value.controller.updateHistory(value.interaction)(_.addEvent(ScaffoldingStartedEvent[F]("answer", timestamp, entity)))
    assertEquals(value.control.getCurrentStateOfScaffolding("answer").map(_.scaffoldingStatus), Some(ScaffoldingStatus.ScaffoldingRequestedButNotFinished()))
    val completed = ScaffoldingResult[String, MessengerModel]("answer", ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap("Hint")), entity, None, timestamp)
    value.controller.updateHistory(value.interaction)(_.addEvent(ScaffoldingCompletedEvent[F](completed)))
    assertEquals(value.control.getCurrentStateOfScaffolding("answer"), Some(completed))
    value.controller.updateHistory(value.interaction)(_.addEvent(ScaffoldingStartedEvent[F]("answer", timestamp.plusNanos(1), entity)))
    assertEquals(value.control.getCurrentStateOfScaffolding("answer").map(_.scaffoldingStatus), Some(ScaffoldingStatus.ScaffoldingRequestedButNotFinished()))
  }
  private val nextUser = UsageContext("program", "workbook", "next user")

  /** A new session of the same interactions, as after a user change. */
  private def rebind(value: Exercise): Unit =
    value.controller.bind(InteractionScope(nextUser, value.controller.info.interactions, SyncConnection.offline))

  test("grading started before the session is replaced cannot publish into the next user's history") {
    val gate = Promise[Unit]()
    val grader = new CountingGrader(gate = Some(gate))
    val value = variable(List(grader))
    val pending = value.control.getOrCalculateGrading(forceGrading = true)
    rebind(value)
    value.controller.setStateFromUserInteraction(value.interaction, "new user's answer", UpdateImportance.MAJOR)
    gate.success(())
    pending.map(_ => assert(!value.controller.currentHistory(value.interaction).gradingEvents.exists(_.associatedState == "answer")))
  }

  test("a replaced session drops pending hints and starts with empty feedback caches") {
    val gate = Promise[Unit]()
    val scaffolder = CountingScaffolder(state => gate.future.map(_ => ScaffoldingResult[String, MessengerModel](state,
      ScaffoldingStatus.ScaffoldingFeedback(LanguageMap.universalMap("private hint")), FeedbackEntity.TestEntity, None)))
    val value = hintVariable(scaffolder)
    val pending = value.control.getOrCalculateScaffolding()
    val retired = value.controller.bind(InteractionScope(nextUser, value.controller.info.interactions, SyncConnection.offline))
    value.controller.setStateFromUserInteraction(value.interaction, "answer", UpdateImportance.MAJOR)
    gate.success(())
    pending.flatMap { _ =>
      assert(value.controller.currentHistory(value.interaction).scaffoldingEvents.isEmpty)
      assert(retired.historyOf(value.interaction).scaffoldingEvents.forall(_.isInstanceOf[it.evadid.workbook.model.history.events.ScaffoldingStartedEvent[?]]), "dropped, not recorded late")
      assertEquals(value.control.getCurrentStateOfScaffolding("answer"), None)
      value.control.getOrCalculateScaffolding()
    }.map(_ => assertEquals(scaffolder.calls.now(), 2))
  }

  test("reconnecting the session with new credentials keeps its pending feedback") {
    val gate = Promise[Unit]()
    val value = variable(List(new CountingGrader(gate = Some(gate))))
    val pending = value.control.getOrCalculateGrading(forceGrading = true)
    value.controller.connect(SyncConnection.offline.copy(execution = TestExecutionContext()))
    gate.success(())
    pending.map(_ => assert(value.controller.currentHistory(value.interaction).historyGrading.gradingsMap.nonEmpty))
  }

}
