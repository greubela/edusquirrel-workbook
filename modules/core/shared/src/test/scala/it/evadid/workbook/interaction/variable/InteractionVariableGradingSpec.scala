package it.evadid.workbook.interaction.variable

import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.state.State
import it.evadid.distribution.clients.ExecutionClient
import it.evadid.distribution.commandTypes.LLMCommands
import it.evadid.util.logging.BasicLogger
import it.evadid.workbook.abstractions.{FeedbackEntity, InteractionBoundComponent, WorkbookElement, WorkbookInteractionElementFull}
import it.evadid.workbook.abstractions.grading.*
import it.evadid.workbook.abstractions.scaffolding.{ScaffoldingResult, WorkbookInteractionScaffolder}
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowGrader}
import it.evadid.workbook.interaction.sync.UpdateImportance.MAJOR
import munit.FunSuite
import upickle.default.*

import java.time.LocalDateTime
import scala.collection.immutable.ListSet
import scala.concurrent.{ExecutionContext, Future, Promise}

class InteractionVariableGradingSpec extends FunSuite {
  private class RecordingGrader extends WorkbookInteractionGrader[String, String] {
    private val recorded = State(List.empty[(String, Boolean)])
    def requests: List[(String, Boolean)] = recorded.now()
    override val importance = GradingImportance.REQUIRED_TEST
    override val runningBehavior = GraderRunningBehavior.RUN_ALWAYS
    override val gradingEntity = FeedbackEntity.TestEntity()
    override def gradeState(state: String): Future[GradingResult[String, String]] =
      Future.successful(GradingResult(this, state, GradingStatus.Correct()))
    override def gradeStateSafe(state: String, forced: Boolean): Future[GradingResult[String, String]] = {
      recorded.update(_ :+ (state -> forced))
      gradeState(state)
    }
  }

  private class RecordingScaffolder extends WorkbookInteractionScaffolder[String, String] {
    private val recorded = State(List.empty[String])
    def requests: List[String] = recorded.now()
    override def generateFeedback(state: String): Future[ScaffoldingResult[String, String]] = {
      recorded.update(_ :+ state)
      Future.successful(ScaffoldingResult(state, LanguageMap.universalMap("Hint"), FeedbackEntity.TestEntity(), None))
    }
  }

  private def interaction(graders: List[WorkbookInteractionGrader[String, String]],
      scaffolder: Option[WorkbookInteractionScaffolder[String, String]] = None,
      initial: String = ""): WorkbookInteractionElementFull[String, String, String] =
    new WorkbookInteractionElementFull[String, String, String] {
      override val elementId = "grading-test"
      override val defaultValue = initial
      override lazy val childrenOfThisElement = List.empty[WorkbookElement]
      override val associatedFactory = TextInteraction.factory
      override val gradingElements = graders
      override val scaffoldingElement = scaffolder
    }

  private def variable(grader: RecordingGrader): InteractionVariable[String, String, String] =
    interaction(List(grader)).interactionVariable

  test("lazy interaction creation binds its grader and scaffolder without executing them") {
    val grader = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    val element = interaction(List(grader), Some(scaffolder), "initialized default")
    intercept[IllegalStateException](grader.interactionVariable)
    intercept[IllegalStateException](scaffolder.interaction)
    val value = element.interactionVariable
    assert(grader.interactionVariable eq value)
    assert(scaffolder.interactionVariable eq value)
    assert(grader.interaction eq element)
    assert(scaffolder.interaction eq element)
    assert(element.interactionVariable eq value)
    assertEquals(grader.interactionVariable.currentValue, "initialized default")
    assertEquals(scaffolder.interactionVariable.history.lastStateOption.map(_.value), Some("initialized default"))
    assertEquals(grader.requests, Nil)
    assertEquals(scaffolder.requests, Nil)
    assertEquals(value.history.gradings, Map.empty)
    assertEquals(value.history.scaffoldings, Map.empty)
  }

  test("an unbound grader still supports standalone execution") {
    val grader = new RecordingGrader
    intercept[IllegalStateException](grader.interactionVariable)
    grader.gradeStateSafe("standalone", true).map { result =>
      assert(result.grader eq grader)
      assertEquals(result.gradedState, "standalone")
      assertEquals(result.gradingStatus, GradingStatus.Correct())
      assertEquals(grader.requests, List("standalone" -> true))
      intercept[IllegalStateException](grader.interaction)
    }(using ExecutionContext.global)
  }

  test("binding is idempotent only for the exact variable, not equal owners or matching ids") {
    val first = TextInteraction("same-id").interactionVariable
    val second = TextInteraction("same-id").interactionVariable
    assertEquals(first, second)
    assert(!(first eq second))
    val grader = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    InteractionBoundComponent.bindAll(first, List(grader, scaffolder))
    InteractionBoundComponent.bindAll(first, List(grader, scaffolder, grader))
    intercept[IllegalArgumentException](InteractionBoundComponent.bindAll(second, List(grader, scaffolder)))
    assert(grader.interactionVariable eq first)
    assert(scaffolder.interactionVariable eq first)
    assertEquals(grader.requests, Nil)
    assertEquals(scaffolder.requests, Nil)
  }

  test("a second variable or case-class copy cannot take another variable's components") {
    val grader = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    val value = interaction(List(grader), Some(scaffolder)).interactionVariable
    intercept[IllegalArgumentException](InteractionVariable(value.underlyingInteraction))
    intercept[IllegalArgumentException](value.copy())
    assert(grader.interactionVariable eq value)
    assert(scaffolder.interactionVariable eq value)
    assertEquals(value.currentValue, "")
    val standaloneScaffolder = new RecordingScaffolder
    val first = interaction(Nil, Some(standaloneScaffolder)).interactionVariable
    intercept[IllegalArgumentException](interaction(Nil, Some(standaloneScaffolder)).interactionVariable)
    assert(standaloneScaffolder.interactionVariable eq first)
  }

  test("a conflicting component leaves every new component unbound") {
    val shared = new RecordingGrader
    val original = variable(shared)
    val fresh = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    val conflicting = interaction(List(fresh, shared), Some(scaffolder))
    intercept[IllegalArgumentException](conflicting.interactionVariable)
    intercept[IllegalStateException](fresh.interactionVariable)
    intercept[IllegalStateException](scaffolder.interactionVariable)
    assert(shared.interactionVariable eq original)
    val valid = interaction(List(fresh), Some(scaffolder)).interactionVariable
    assert(fresh.interactionVariable eq valid)
    assert(scaffolder.interactionVariable eq valid)
    assert(shared.interactionVariable eq original)
  }

  test("components read current answers and historical grades from their exact variable") {
    val grader = new RecordingGrader
    val scaffolder = new RecordingScaffolder
    val value = interaction(List(grader), Some(scaffolder)).interactionVariable
    val time = LocalDateTime.of(2026, 10, 17, 10, 0)
    val grade = GradingResult[String, String](grader, "older", GradingStatus.Incorrect())
    val graded = GradedState("older", Set(grade))
    value.updateHistory(_.copy(events = Set(InteractionVariableState("older", MAJOR, time)),
      gradings = Map("older" -> graded)))
    value.updateHistory(_.withAddedEvent(InteractionVariableState("current", MAJOR, time.plusSeconds(1))))
    assertEquals(grader.interactionVariable.currentValue, "current")
    assertEquals(scaffolder.interactionVariable.currentValue, "current")
    assertEquals(grader.interactionVariable.history.gradings("older").state, "older")
    assertEquals(scaffolder.interactionVariable.history.gradings("older").testsWithResults.head.gradedState, "older")
    assertEquals(value.history.gradings, Map("older" -> graded))
    assertEquals(grader.requests, Nil)
    assertEquals(scaffolder.requests, Nil)
  }

  test("binding preserves concrete grader codecs and value identity without copying its owner") {
    val grader = SlideshowGrader(true)
    val json = write(grader)
    val binary = writeBinary(grader).toVector
    val hash = grader.hashCode()
    val owner = Slideshow("codec", Nil).interactionVariable
    InteractionBoundComponent.bindAll(owner, List(grader))
    assertEquals(write(grader), json)
    assertEquals(writeBinary(grader).toVector, binary)
    assertEquals(grader.hashCode(), hash)
    List(grader.copy(), read[SlideshowGrader](json), readBinary[SlideshowGrader](binary.toArray))
      .zipWithIndex.foreach { (restored, index) =>
        assertEquals(restored, grader)
        assertEquals(restored.hashCode(), hash)
        assert(!(restored eq grader))
        intercept[IllegalStateException](restored.interactionVariable)
        val second = Slideshow(s"codec-copy-$index", Nil).interactionVariable
        InteractionBoundComponent.bindAll(second, List(restored))
        assert(restored.interactionVariable eq second)
        assert(grader.interactionVariable eq owner)
      }
  }

  test("a concrete scaffolder resolves its current client at dispatch after asynchronous preparation") {
    given ExecutionContext = ExecutionContext.global
    val command = LLMCommands.feedbackLlmCommandFactory
    def client(label: String): ExecutionClient = command.toLocalExecutionClient((request, _) =>
      Future.successful(label + ":" + request.prompt))
    val selected = State(client("first"))
    val lookups = State(0)
    val prepared = Promise[Unit]()
    val scaffolder = new WorkbookInteractionScaffolder[String, String] {
      private val executionClient: () => ExecutionClient = () => {
        lookups.update(_ + 1)
        selected.now()
      }
      override def generateFeedback(state: String): Future[ScaffoldingResult[String, String]] =
        prepared.future.flatMap(_ => command.sendCommandTo(executionClient(),
          LLMCommands.FeedbackLlmRequest(state, ""), Some(BasicLogger())))
          .map(response => ScaffoldingResult(state, LanguageMap.universalMap(response.result),
            FeedbackEntity.TestEntity(), Some(response.result)))
    }
    val value = interaction(Nil, Some(scaffolder)).interactionVariable
    assert(scaffolder.interactionVariable eq value)
    assertEquals(lookups.now(), 0)
    val pending = scaffolder.generateFeedback("one")
    assert(!pending.isCompleted)
    assertEquals(lookups.now(), 0)
    selected.set(client("second"))
    prepared.success(())
    pending.flatMap { first =>
      assertEquals(first.forState, "one")
      assertEquals(first.additionalInformation, Some("second:one"))
      selected.set(client("third"))
      scaffolder.generateFeedback("two").map { second =>
        assertEquals(second.forState, "two")
        assertEquals(second.additionalInformation, Some("third:two"))
        assertEquals(lookups.now(), 2)
        assertEquals(value.history.scaffoldings, Map.empty)
      }
    }
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
}
