package it.evadid.workbook.interaction.state

import it.evadid.core.datastructures.chat.MessengerModel
import it.evadid.core.datastructures.language.{LanguageMapContentId, WorkbookContent}
import it.evadid.core.datastructures.state.State
import it.evadid.workbook.abstractions.{FeedbackEntity, WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.abstractions.InteractionFormat.TextInputWithLlmGradingAndScaffoldingFormat
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction
import it.evadid.workbook.elements.interactionElements.choice.WorkbookContentChoiceInteraction.*
import it.evadid.workbook.interaction.sync.{SyncConnection, UpdateImportance, UsageContext}
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import it.evadid.workbook.interaction.variable.InteractionVariableHistory
import it.evadid.workbook.interaction.variable.events.{AnswerChangedEvent, GradingCompletedEvent, GradingStartedEvent}
import it.evadid.workbook.meta.ExecutionContextInformation
import it.evadid.workbook.support.GatedHistoryStore
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future, Promise}

/** Grading while answers change and other devices or the server record feedback events for the same history. */
class InteractionGradingConcurrencySpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val context = UsageContext("program", "workbook", "student")

  // A text exercise whose grader waits for the gate of each answer: "right" is correct, anything else incorrect.
  private type Text = TextInputWithLlmGradingAndScaffoldingFormat.type
  private final class GatedGrader(behavior: GraderRunningBehavior) extends WorkbookInteractionGrader[String, MessengerModel] {
    val gates = State(Map.empty[String, Promise[Unit]])
    val graded = State(Vector.empty[String])
    def gate(answer: String): Promise[Unit] = gates.now().getOrElse(answer, { val created = Promise[Unit](); gates.update(_.updated(answer, created)); created })
    override val key = "gated"
    override val importance = GradingImportance.REQUIRED_TEST
    override val runningBehavior = behavior
    override val gradingEntity = FeedbackEntity.TestEntity
    override protected def handleGrading(state: String, context: ExecutionContextInformation): Future[GradingResult[String, MessengerModel]] = {
      graded.update(_ :+ state)
      gate(state).future.map(_ => GradingResult(this, state, if state == "right" then GradingStatus.Correct else GradingStatus.Incorrect))
    }
  }
  private def textExercise(grader: GatedGrader) = new WorkbookInteractionElement[Text] {
    override val elementId = "gated-text"
    override val defaultValue = ""
    override lazy val childrenOfThisElement = List.empty[WorkbookElement]
    override val associatedFactory = TextInteraction.factory
    override val gradingElements = List(grader)
  }
  private def offline(interaction: WorkbookInteractionElement[?]): InteractionController = {
    val controller = InteractionController(State(InteractionInfo.empty(context)))
    controller.bind(InteractionScope(context, Seq(interaction), SyncConnection.offline))
    controller
  }
  private def status[F <: it.evadid.workbook.abstractions.InteractionFormat](answers: InteractionController,
      interaction: WorkbookInteractionElement[F], answer: it.evadid.workbook.abstractions.InteractionFormat.State[F]): GradingStatus =
    answers.currentHistory(interaction).gradingForState(interaction.gradingElements, answer).testsWithResults.head.gradingStatus

  // A choice exercise graded only on request; its histories cross a destination.
  private def id(name: String) = LanguageMapContentId(s"test/$name")
  private val choice = WorkbookContentChoiceInteraction("choice", ChoiceConfig(id("prompt"), Some(false), GraderRunningBehavior.RUN_ONLY_MANUALLY),
    (0 to 1).map(i => ChoiceOptionWorkbookContent(WorkbookContent(id(s"option$i")),
      Some(GradingForChoice(if i == 0 then GradingStatus.Correct else GradingStatus.Incorrect, id(s"feedback$i"))))))
  private val right = choice.select(choice.defaultValue, 0, true)
  private val wrong = choice.select(choice.defaultValue, 1, true)
  private val grader = choice.gradingElements.head
  private type Choice = choice.interactionFormat.type
  private def remote(events: it.evadid.workbook.interaction.variable.events.InteractionEvent[Choice]*): InteractionVariableHistory[Choice] =
    events.foldLeft(InteractionVariableHistory.empty[Choice])(_.addEvent(_))

  test("an answer changed while the previous answer is graded keeps each result with its own answer") {
    val grader = GatedGrader(GraderRunningBehavior.RUN_ALWAYS)
    val exercise = textExercise(grader)
    val answers = offline(exercise)
    answers.setStateFromUserInteraction(exercise, "wrong", MAJOR)
    answers.setStateFromUserInteraction(exercise, "right", MAJOR, LocalDateTime.now().plusSeconds(1))
    assertEquals(grader.graded.now(), Vector("wrong", "right"), "each answer starts its own automatic grading")
    assertEquals(status(answers, exercise, "right"), GradingStatus.NotFinished)
    grader.gate("right").success(())
    grader.gate("wrong").success(())
    answers.feedback(exercise).getOrCalculateGrading().map { current =>
      assertEquals(current.state, "right")
      assertEquals(status(answers, exercise, "right"), GradingStatus.Correct)
      assertEquals(status(answers, exercise, "wrong"), GradingStatus.Incorrect, "the late result belongs to the earlier answer")
      assertEquals(answers.currentValue(exercise), "right")
    }
  }

  test("automatic grading follows answer changes the graders run for, not feedback events") {
    val grader = GatedGrader(GraderRunningBehavior.RUN_ON_AT_LEAST_MINOR)
    val exercise = textExercise(grader)
    val answers = offline(exercise)
    answers.setStateFromUserInteraction(exercise, "typing", UpdateImportance.TEMPORARY)
    assertEquals(grader.graded.now(), Vector.empty, "a temporary answer is not graded automatically")
    answers.setStateFromUserInteraction(exercise, "wrong", UpdateImportance.MINOR)
    grader.gate("wrong").success(())
    answers.feedback(exercise).getOrCalculateGrading().map { _ =>
      answers.updateHistory(exercise)(identity)
      answers.setStateFromUserInteraction(exercise, "wrong", UpdateImportance.MAJOR)
      assertEquals(grader.graded.now(), Vector("wrong"), "grading events and a new importance of the same answer start nothing")
      assert(answers.currentHistory(exercise).allStates.forall(_.newState != "typing"), "the superseded temporary answer is gone")
      assert(answers.currentHistory(exercise).gradingEvents.forall(_.associatedState == "wrong"))
    }
  }

  test("a superseded temporary answer takes its pending feedback events with it") {
    val grader = GatedGrader(GraderRunningBehavior.RUN_ONLY_MANUALLY)
    val exercise = textExercise(grader)
    val answers = offline(exercise)
    answers.setStateFromUserInteraction(exercise, "draft", UpdateImportance.TEMPORARY)
    val pending = answers.feedback(exercise).getOrCalculateGrading(forceGrading = true)
    assert(answers.currentHistory(exercise).gradingEvents.exists(_.associatedState == "draft"))
    answers.setStateFromUserInteraction(exercise, "right", MAJOR, LocalDateTime.now().plusSeconds(1))
    grader.gate("draft").success(())
    pending.map { _ =>
      assert(answers.currentHistory(exercise).events.values.forall(_.associatedState != "draft"),
        "neither the start nor the late completion of the superseded draft is kept")
    }
  }

  test("a remote completion merged while the local grading is pending keeps the latest result") {
    val grader = GatedGrader(GraderRunningBehavior.RUN_ONLY_MANUALLY)
    val exercise = textExercise(grader)
    val answers = offline(exercise)
    answers.setStateFromUserInteraction(exercise, "right", MAJOR)
    val pending = answers.feedback(exercise).getOrCalculateGrading(forceGrading = true)
    val earlier = GradingResult[String, MessengerModel](grader, "right", GradingStatus.Incorrect, None, None, LocalDateTime.now().minusSeconds(5))
    answers.updateHistory(exercise)(_.mergeWith(InteractionVariableHistory.empty[Text].addEvent(GradingCompletedEvent[Text](earlier))))
    assertEquals(answers.feedback(exercise).getCurrentStateOfGrading("right").testsWithResults.head.gradingStatus, GradingStatus.NotFinished,
      "the newer local start is still running")
    grader.gate("right").success(())
    pending.map { result =>
      assertEquals(result.testsWithResults.head.gradingStatus, GradingStatus.Correct)
      assertEquals(status(answers, exercise, "right"), GradingStatus.Correct, "the local completion is newer than the merged one")
    }
  }

  test("a new answer submitted while the server records a grading start for the previous one is not shown as pending") {
    val store = GatedHistoryStore(context)
    val now = LocalDateTime.now()
    store.seed(choice, remote(AnswerChangedEvent[Choice](wrong, now.minusSeconds(10), MAJOR),
      GradingStartedEvent[Choice](wrong, now.minusSeconds(5), grader)))
    val answers = store.controllerFor(choice)
    answers.setStateFromUserInteraction(choice, right, MAJOR)
    answers.fetch(choice, forceFlush = true).flatMap { _ =>
      assertEquals(answers.currentValue(choice), right, "the newer local answer stays current")
      assertEquals(status(answers, choice, wrong), GradingStatus.NotFinished, "the server's start belongs to the previous answer")
      assertEquals(status(answers, choice, right), GradingStatus.NotRequested)
      answers.feedback(choice).getOrCalculateGrading(forceGrading = true)
    }.map { graded =>
      assertEquals(graded.state, right)
      assertEquals(status(answers, choice, right), GradingStatus.Correct)
      assertEquals(status(answers, choice, wrong), GradingStatus.NotFinished, "no grading runs for an answer that is not current")
    }
  }

  test("a grading start read for the current answer shows as pending until a request grades it here") {
    val store = GatedHistoryStore(context)
    val now = LocalDateTime.now()
    store.seed(choice, remote(AnswerChangedEvent[Choice](wrong, now.minusSeconds(10), MAJOR),
      GradingStartedEvent[Choice](wrong, now.minusSeconds(5), grader)))
    val answers = store.controllerFor(choice)
    answers.fetch(choice, forceFlush = true).flatMap { _ =>
      assertEquals(answers.currentValue(choice), wrong)
      assertEquals(answers.feedback(choice).getCurrentStateOfGrading(wrong).testsWithResults.head.gradingStatus, GradingStatus.NotFinished)
      answers.feedback(choice).getOrCalculateGrading(forceGrading = true)
    }.flatMap { graded =>
      assertEquals(graded.testsWithResults.head.gradingStatus, GradingStatus.Incorrect)
      assertEquals(status(answers, choice, wrong), GradingStatus.Incorrect, "the local completion is newer than the remote start")
      answers.store(choice, forceFlush = true)
    }.map { _ =>
      val stored = it.evadid.workbook.interaction.sync.model.InteractionHistoryJson.deserialize(store.values.now().values.head).typedAs(choice.interactionFormat)
      assertEquals(stored.gradingForState(choice.gradingElements, wrong).testsWithResults.head.gradingStatus, GradingStatus.Incorrect,
        "the destination receives the completion")
    }
  }

  test("a completed grading read from another device is reused instead of grading again") {
    val store = GatedHistoryStore(context)
    val now = LocalDateTime.now()
    val completed = GradingResult(grader, right, GradingStatus.Correct, None, None, now.minusSeconds(5))
    store.seed(choice, remote(AnswerChangedEvent[Choice](right, now.minusSeconds(10), MAJOR), GradingCompletedEvent[Choice](completed)))
    val answers = store.controllerFor(choice)
    answers.fetch(choice, forceFlush = true).flatMap(_ => answers.feedback(choice).getOrCalculateGrading(forceGrading = true)).map { graded =>
      assertEquals(graded.testsWithResults.head.gradingStatus, GradingStatus.Correct)
      assertEquals(answers.currentHistory(choice).gradingEvents.count(_.isInstanceOf[GradingStartedEvent[?]]), 0, "no local grading started")
    }
  }

  test("a grading completing after the user's session was replaced is dropped, also for the same answer") {
    val grader = GatedGrader(GraderRunningBehavior.RUN_ONLY_MANUALLY)
    val exercise = textExercise(grader)
    val answers = offline(exercise)
    answers.setStateFromUserInteraction(exercise, "right", MAJOR)
    val pending = answers.feedback(exercise).getOrCalculateGrading(forceGrading = true)
    answers.bind(InteractionScope(context.copy(userId = "next"), Seq(exercise), SyncConnection.offline))
    answers.setStateFromUserInteraction(exercise, "right", MAJOR)
    grader.gate("right").success(())
    pending.map { _ =>
      assertEquals(status(answers, exercise, "right"), GradingStatus.NotRequested, "the next user's identical answer is not graded by the previous run")
      assert(answers.currentHistory(exercise).gradingEvents.isEmpty)
    }
  }
}
